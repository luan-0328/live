# 地方社区交流系统（geo-community）

一个面向「同城/地方」场景的社区平台后端 + 前端：发帖、评论、点赞、收藏、关注、通知、举报、管理后台。
本轮修复及已有环境升级步骤见 [FIXES_2026-10-03.md](FIXES_2026-10-03.md)。模拟验证码保留；二级回复及死信重试见 [新增说明](REPLIES_RETRY_2026-10-03.md)，最新前后端修复见 [审查记录](FRONTEND_BACKEND_REVIEW_2026-10-05.md)。

**设计重点在"读多写少"场景下的缓存与并发正确性**：缓存穿透、击穿、雪崩治理、浏览计数延迟落库、热榜时间衰减、
幂等交给数据库唯一索引，并用 JMeter + 服务端 SQL 日志做了可复现的性能验证。

```
Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.7 · MySQL 8 · Redis 7（Redisson）· RabbitMQ 3 · 阿里云 OSS · Vue 3 · Docker Compose
```

- 后端：98 个 Java 文件 · 12 张表 · 原有单元测试与新增回归/真实MySQL测试
- 前端：Vue 3 + Vite + Pinia + Element Plus，22 个页面/组件（用户端 10 + 管理后台 6 + 公共组件 6）
- 部署：Docker Compose 五容器（MySQL / Redis / RabbitMQ / App / Nginx）一键起

---

## 一、功能一览

| 模块 | 能力 |
|---|---|
| 账号 | 手机号+验证码注册、密码登录、管理员登录、JWT 会话滑动续期、改资料/手机号/密码、注销 |
| 帖子 | 发帖（多图/分类/经纬度）、列表（最新/热门、分类筛选、分页）、详情、编辑、删除、全文检索 |
| 互动 | 点赞/取消、收藏/取消、评论与二级回复（树形）、浏览计数 |
| 社交 | 关注/取关、粉丝/关注列表、个人主页、我的收藏 |
| 通知 | 发帖通知粉丝、点赞/评论通知作者、未读数、全部已读（RabbitMQ 异步） |
| 热榜 | Redis ZSET 加权榜 + 时间衰减（首页与详情页展示） |
| 举报与治理 | 用户举报、管理员处置（违规成立→自动删帖/删评/封号并强制下线） |
| 管理后台 | 数据概览、用户封禁/解封、帖子强删、分类增删改、举报处理、操作日志（`@AdminLog` 切面审计） |

---

## 二、核心设计（每个都有"为什么"）

### 1. 缓存三灾分而治之

| 问题 | 手段 | 位置 |
|---|---|---|
| 穿透 | 空值标记 `post:null:{id}`（查不到也缓存，TTL 5min） | `PostServiceImpl.getDetail` |
| 击穿 | Redisson 分布式锁 `lock:detail:{id}` + `tryLock(0,5s)` + 双重检查；**未抢到锁的请求不干等**：短睡后重读缓存，仍失败直接回源降级 | 同上 |
| 雪崩 | TTL 随机偏移：列表/搜索 `120+rand(60)` 秒，详情/用户 `1800+rand(600)` 秒 | `PostServiceImpl` / `UserServiceImpl` |

> 为什么"抢不到锁也不能干等"：热点 key 过期瞬间若有大量请求排队等锁，p99 会被锁等待拉爆；
> 这里用"少量回源压力"换尾部延迟稳定，是有意识的取舍。

### 2. 写放大治理：浏览量延迟落库

详情页浏览只做 Redis `INCR post:view:{id}`，`ViewCountFlushTask` 每 **5 分钟** `SCAN` → `getAndDelete` →
逐帖合并 `UPDATE` 回 MySQL。把"每次浏览一次写库"压成"每 5 分钟定时合并写入"；统计采用近似计数，Redis到MySQL交接窗口崩溃可能丢失增量。

### 3. 双写一致性

统一 **先改 DB 再删缓存**（不更新缓存，避免并发写互相覆盖）；事务提交后切换缓存版本；列表/搜索不再全量SCAN，详情按id更新版本，旧版本缓存按TTL回收。提交前开始的读请求只能回填旧版本。

### 4. 热榜：加权 + 时间衰减

ZSET `post:hot:24h`，点赞 +2 / 评论 +3 / 收藏 +4；`HotBoardDecayTask` 每 30 分钟全榜 ×0.8、低于 1 分移出，
被点赞/评论时 `ZINCRBY` 自动重新入榜。**用衰减代替"定时重算榜单"**，冷帖自然沉底、全榜衰减的开销随榜单规模增长。

### 5. 幂等交给数据库，而不是代码判断

`post_like` / `post_favorite` 上的 `uk_post_user(post_id, user_id)` 唯一索引兜底重复点赞；
计数字段用 `like_count = like_count + 1` 的原子自增（不是读-改-写）；点赞行与计数在同一事务内。

### 6. 通知：全异步 + 批量 + 去重

发帖/点赞/评论/关注事件投递到 RabbitMQ（`geo.exchange`），消费端按 **500 条**批量插入粉丝通知；
业务事务写入Outbox，后台收到broker确认后删除；`notification` 上的 `uk_user_event(user_id,event_id)` 保证同一事件重投不会产生重复通知。
消费端 `prefetch=1`、失败重试 3 次。

### 7. 会话可撤销：JWT + Redis 双轨

JWT 只承载 `userId/role`；真正的会话状态在 Redis（`session:idle:{token}`，与 JWT 同寿命，临近过期时轮换token）。
封禁时按 `session:user:{userId}` 索引删除该用户全部会话 → **下次请求即 401（强制下线）**。
Redis 不可用时私人接口拒绝访问；公开页面可匿名浏览。续期保留最多60秒并发宽限，登出会撤销对应的新旧token。

### 8. 限流：三类原语按场景选型

| 场景 | 手段 |
|---|---|
| 验证码发送冷却 | `SET NX EX`（判断 + 占位原子完成，避免 GET 再 SET 的竞态） |
| 短信每日配额（手机号 / IP 两个维度） | 固定窗口计数（窗口从首次计数起算，不被后续请求延长） |
| 登录密码错误 / 验证码试错 | 滑动窗口计数（每次失败刷新 TTL，防"卡窗口末尾试探"） |

### 9. 地理检索（前后端已接线）

`GET /post/nearby`：先用**边界盒预筛**（1°≈111km，经度范围按纬度修正，实际索引效果需通过EXPLAIN验证）缩小候选集，
再对盒内数据算 **球面余弦公式** 精筛。直接在 `WHERE` 里对经纬度做函数运算会导致索引失效——这是这里必须两步走的原因。

---

## 三、历史性能与质量验证（2026-09-28版本）

以下为修改前历史数据，本轮代码变化后未重新压测；当前验证见修复说明。

### 读接口（JMeter 5.6.3，本机单机，150 并发 = 3 接口 × 50 线程 × 20 循环，ramp-up 10s）

| 场景 | 请求数 | 错误 | 平均 | P95 | P99 | 吞吐 | **服务端 SQL 次数** |
|---|---|---|---|---|---|---|---|
| 冷缓存（清空 `post:*` 后立刻压） | 3000 | 0 | 1.3ms | 3ms | 5ms | 308/s | **6**（每个 key 仅回源一次） |
| 热缓存 | 3000 | 0 | 0.74ms | 2ms | 2ms | 308/s | **0** |

- 缓存命中率 **99.8%**（`(3000-6)/3000`）；命中后 P99 由 5ms 降到 2ms。
- 交叉验证方式：压测前后统计服务端 `logs/geo-community.log` 里 MyBatis `Preparing:` 的条数——
  **只看 JMeter 的延迟证明不了缓存起作用，必须回服务端数 SQL**。
- 持续施压（150 并发长跑）实测本机 ≈ **1.4 万 QPS**（P50 6ms / P99 33ms）；瓶颈出现在压测机自身
  （`BindException` 本地端口耗尽），而非被测服务——这也是"单机压测测不出真实容量上限"的典型现象。

### 写接口（中等压力，共 220 请求）

| 接口 | 请求 | 平均 | P95 | 落库校验 |
|---|---|---|---|---|
| POST /post/{id}/comment | 100 | 10.6ms | 15ms | 评论行 +100 ✅ |
| POST /post/{id}/like | 100 | 7.3ms | 10ms | 唯一索引生效：仅 1 行、计数 +1 ✅ |
| POST /post | 20 | 15.5ms | 24ms | 新帖 +20 ✅ |

**并发幂等实测**：20 线程同时点赞同一帖 → `post_like` 只落库 1 行、`like_count` 只 +1；
但平均延迟从常规 7ms 涨到 443ms（唯一键冲突 + 同一行计数锁排队），这是同一热点行并发写的固有代价。

### 测试与验收

- **63 项单元测试**（JUnit 5 + Mockito）覆盖缓存三灾、锁、幂等、举报处置等分支：`mvn test` 全绿
- **31 项端到端验收**（真 HTTP + 回库核对）：注册/登录/会话 → 发帖 → 列表/详情/全文检索 → 评论 →
  点赞/收藏 → 关注 + MQ 通知 → 未读数 → **未登录 401、越权改删他人帖子被拒且数据不变** →
  举报与管理员处置（帖子自动删除 + 日志留痕）→ **封禁强制下线**（写请求 401、公开读放行）
- **库结构对账**：`schema.sql` ↔ 实际库 ↔ 实体类三方比对，曾发现并修复 3 处漂移（见下）

---

## 四、快速开始

### 部署方式一：本地打包后上传（推荐，服务器无需装 Maven）

```bash
# 1) 本地打包（产物 app.jar 是构建产物，不入 git）
mvn -DskipTests package && cp target/geo-community-1.0.0.jar app.jar

# 2) 前端产物：nginx 容器直接挂载 frontend/dist，必须先构建
cd frontend && npm ci && npm run build && cd ..

# 3) 起服务（把 app.jar + docker-compose.yml + frontend/dist + nginx/ 一起传到服务器即可）
cp .env.example .env            # 填 MySQL 密码 / JWT_SECRET / ADMIN_INIT_PASSWORD / OSS
docker compose up -d --build    # mysql + redis + rabbitmq + app + nginx

# 访问 http://localhost（nginx 托管前端，/api/v1/* 反代到 app:8080）
```

### 部署方式二：源码直跑（刚 clone、手上没有 app.jar）

```bash
docker compose -f docker-compose.yml -f docker-compose.clone.yml up -d --build
```

`docker-compose.clone.yml` 会把构建切到 `Dockerfile.multistage`（容器内 `mvn package`），
所以不需要本地预装 JDK/Maven；代价是首次构建要下载 Maven 与依赖（约 2~5 分钟）。

### 本地开发

```bash
# 1) 起依赖：MySQL 8 / Redis 7 / RabbitMQ 3（或用 docker compose up -d mysql redis rabbitmq）
# 2) 初始化库
mysql -uroot -p -e "CREATE DATABASE community CHARACTER SET utf8mb4"
mysql -uroot -p community < sql/schema.sql
mysql -uroot -p community < sql/migrations_2026-09-28.sql   # 老库升级用（新装库忽略）
# 3) 起后端（默认 8080）
mvn spring-boot:run
# 4) 起前端（默认 3000，代理到 8080）
cd frontend && npm install && npm run dev
```

内置管理员：账号 `luan`，初始密码由 `ADMIN_INIT_PASSWORD` 提供（默认 `456281`，**仅在账号首次创建时生效**）。

### 接口约定

- 路径无统一前缀，按模块分：`/post/**`、`/user/**`、`/comment/**`、`/notification/**`、`/rank/hot`、`/admin/**`
- **公开GET接口采用明确白名单**（帖子、评论、分类、热榜、公开主页）；私人GET及写请求需 `Authorization: Bearer <token>`
- 统一响应体 `{code, message, data}`；**业务错误用 `code` 表达**（例如越权返回 `code=1008 无操作权限`），
  调用方需判断 `code` 而不能只看 HTTP 状态码

---

## 五、目录结构

```
src/main/java/com/geocommunity/
├── common/        鉴权拦截器（JWT + Redis 会话）、切面（@AdminOnly / @AdminLog）、
│                  限流器、RedisKeys 常量、统一响应与全局异常
├── config/        Spring 配置（Redis / RabbitMQ / MyBatis-Plus / WebMvc）、启动初始化（管理员与分类种子）
├── controller/    10 个 REST 控制器
├── service/       业务逻辑（缓存三灾、热榜、幂等、举报处置都在这里）
├── mapper/        MyBatis-Plus Mapper（复杂 SQL 在 resources/mapper/*.xml）
├── entity/ dto/   实体与视图对象
├── mq/            RabbitMQ 事件定义与消费者（批量插入 + 幂等）
└── task/          定时任务：浏览量刷库（5min）、热榜衰减（30min）
frontend/src/      Vue 3 页面（用户端 10 + 管理后台 6）与公共组件
sql/               schema.sql（新装库）、migrations*.sql（老库升级）
nginx/             反向代理配置（/api → app:8080）
```

---

## 六、已知取舍与待办

1. **验证码暂不修改**：短信仍使用固定开发验证码。二级回复现已接通实际评论关联与通知。
2. **图片上传依赖阿里云 OSS**：不配密钥时上传不可用。
3. **通知Outbox**：失败消息留库重试；旧库必须执行2026-10-03迁移，旧RabbitMQ通配绑定必须移除，见修复说明。
4. **单层缓存**：只有Redis，没有本地缓存；缓存操作失败仍依赖TTL恢复。
5. **会话依赖Redis**：Redis不可用时不会降级恢复私人访问权限。
6. **真实环境验收**：本轮真实MySQL测试已通过，Redis Lua、RabbitMQ实际投递及浏览器端到端仍需部署环境验证。
7. **生产配置**：Compose启用prod并要求随机JWT_SECRET和管理员初始密码；TLS需自行配置，数据库及MQ口令需替换。

---

## 七、更新记录

- **2026-10-05**：对齐前后端参数、权限、会话状态、分页、图片失败处理及个人中心入口；详情见最新审查记录。

- **2026-10-03**：修复字段绑定、会话、并发计数、事务缓存、通知Outbox与事件幂等、死信路由、分页和热榜；接通帖子编辑与附近查询。详见修复说明。

- **2026-09-28**：压测 + 库结构对账，修复三处 schema 漂移（新增 `sql/migrations_2026-09-28.sql`）
  - `notification` 缺 `uk_user_from_type_post` → 消费端"重复通知跳过"失效（库里已出现重复通知）
  - `post` 缺 `idx_author_status` / `idx_status_like_created` / `idx_lat_lng` → 帖子列表、热榜排序、附近查询退化为全表扫描
  - `admin_log` 表不存在 → 管理后台操作日志不可用
