# 地方社区 — 项目文档

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.2.5, MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7 (StringRedisTemplate + Redisson 分布式锁) |
| 消息队列 | RabbitMQ 3 (异步通知) |
| 存储 | 阿里云 OSS (图片) |
| 前端 | Vue 3 (Composition API), Vite, Pinia, Vue Router, Element Plus, Axios |
| 部署 | Docker Compose (mysql + redis + rabbitmq + app + nginx) |

---

## 一、项目结构

```
backend/
├── common/
│   ├── aspect/          # 切面（管理员日志、权限校验）
│   ├── auth/            # 拦截器（JWT 鉴权、Session 续期）
│   ├── constant/        # RedisKeys（所有缓存 key 统一管理）
│   ├── exception/       # BusinessException + 全局异常处理
│   ├── mybatis/         # 类型处理器（JSON 数组互转）
│   ├── result/          # Result<T> 统一响应格式
│   └── utils/           # JwtUtil, OssService, JsonUtil 等工具
├── config/              # Spring 配置类 + 自定义注解
├── controller/          # 10 个 REST 控制器
├── dto/                 # 请求/响应体
├── entity/              # 10 个数据库实体
├── mapper/              # 10 个 MyBatis-Plus Mapper
├── mq/                  # RabbitMQ 消息定义 + 消费者
├── service/             # 接口 + 实现
└── task/                # 定时任务（浏览量刷库）

frontend/
├── api/                 # Axios 请求封装（6 个模块）
├── components/          # 4 个公共组件
├── views/               # 8 个主页面 + 3 个管理后台页面
├── stores/              # Pinia 状态管理
└── router/              # 前端路由
```

---

## 二、核心业务流程

### 1. 用户注册 / 登录

**注册流程：**
- 输入手机号 → 点获取验证码 → POST `/auth/send-code`
  - 验证码存 Redis，key=`sms:{phone}`，TTL=5 分钟
  - 开发环境固定验证码 `123456`
- 输入验证码 + 昵称 → POST `/auth/register`
  - 验证码校验通过后写入 `user` 表
  - 生成 JWT token 返回前端

**登录流程：**
- POST `/auth/login` → 手机号 + 密码（BCrypt 校验）→ 查 `user` 表
  - 手机号不存在返回「用户不存在」；账号封禁（status≠1）返回「账号已封禁」
  - 生成 JWT token（7 天过期）
  - Redis 记录活跃 session `session:idle:{token}`（与 JWT 有效期一致，默认 7 天，滑动续期）

**JWT 鉴权：**
- 前端把 token 存 `localStorage`，每次请求带 `Authorization: Bearer {token}`
- `AuthInterceptor` 拦截所有 `/api/v1/*` 请求，校验 token 合法性
- `RefreshInterceptor` 每次请求续期 Redis session（用户活跃则 token 不过期）
- 401 响应时前端清除 token 并跳转登录页

---

### 2. 帖子 CRUD

#### 发布帖子
1. 前端提交标题、分类、内容（HTML）、图片（上传 OSS 拿回 URL）、位置
2. `PostController.createPost()` → `PostServiceImpl.createPost()`
   - 写 `post` 表 → 作者 `post_count +1` → 清除帖子列表缓存
   - 帖子入热榜 ZSET（初始分 0）
   - MQ 发 `NEW_POST` 通知 → 消费者给所有粉丝插入通知记录（分页批量插入）

#### 查看帖子列表
- GET `/post/list?categoryId=&sort=latest|hot&page=&size=`
- `PostServiceImpl.listPosts()`：
  - 按 `categoryId:sort:page:size` 生成缓存 key
  - 有缓存 → 直接返回 JSON（TTL=2 分钟 + 随机偏移防雪崩）
  - 无缓存 → 查 DB → 写缓存
  - 作者自己的帖子列表不走缓存（每个人不同）
- SQL 在 `PostMapper.xml`，通过 JOIN 查出作者信息（昵称、头像）

#### 查看帖子详情
- GET `/post/{id}`
- `PostServiceImpl.getDetail()`：**三重防护缓存**
  1. 查空值标记 → 有则直接返回 null（穿透防护）
  2. 读缓存 → 有则返回（顺便 +1 浏览计数）
  3. 抢分布式锁 `lock:detail:{id}` → 抢到者查 DB 写缓存
  4. 没抢到锁 → 等 50ms 重试读缓存 → 还不行直接查 DB（降级）
- `PostController.detail()` 从 Service 拿到 Post 后，再查 User 表填充作者信息，查 Category 表填充分类名

#### 编辑帖子
- 仅作者可操作，校验 `exist.authorId == currentUserId`
- 更新 DB → 删帖子详情缓存 + 清列表缓存

#### 删除帖子
- 级联清理：点赞表、收藏表、评论表、热榜 ZSET
- 作者 `post_count -1`

---

### 3. 点赞 / 收藏

**点赞流程：**
1. 校验帖子存在且状态正常
2. `post_like` 表 insert（唯一索引 `uk_post_user` 防重复，重复抛 `BusinessException`）
3. Redis Set `post:liked:{postId}` 记录点赞用户（可降级）
4. `post.like_count +1`
5. 热榜分数 +2（Redis ZSET `post:hot:24h`）
6. 删帖子详情缓存（下次查看展示最新 like_count）
7. MQ 发 `LIKE` 通知给帖子作者

**取消点赞：** 流程相反，`like_count -1`，热榜 -2

**收藏流程：** 类似点赞，热榜分数 +4

---

### 4. 评论

#### 发表评论
- `CommentServiceImpl.addComment()`：
  - 校验帖子存在
  - 写 `comment` 表（`parent_id` 为 null 表示根评论，非 null 表示回复子评论）
  - 帖子 `comment_count +1`
  - 删帖子详情缓存
  - 热榜 +3
  - MQ 发 `COMMENT` 通知给帖子作者

#### 评论列表（树形结构）
- `CommentServiceImpl.listComments()`：
  1. 分页查根评论（`parent_id IS NULL`）
  2. 批量查子评论（`parent_id IN (根评论 IDs)`）
  3. 统一加载所有评论的作者信息（`userMapper.selectBatchIds`）
  4. 子评论按 `parent_id` 分组，挂到根评论的 `children` 字段

---

### 5. 关注系统

- `followUser()`：校验不能关注自己/用户存在 → insert `user_follow` 表 → 双方 `follower_count`/`following_count +1` → 删双方用户缓存
- `unfollowUser()`：校验已关注 → delete → 双方计数 -1 → 删缓存
- `getFollowers()`/`getFollowing()`：分页查关注关系 → 收集用户 ID → `selectBatchIds` 批量查用户信息

---

### 6. 通知系统

- 通知由 **MQ 消费者** 异步产生：
  - `NEW_POST`：发帖时，给所有粉丝批量插入通知
  - `LIKE`：有人点赞时，通知帖子作者
  - `COMMENT`：有人评论时，通知帖子作者
- 前端轮询 `GET /notification/unread-count` 获取未读数
- 点通知跳转到对应帖子详情

---

### 7. 举报系统

- `submitReport()`：校验举报类型（post/comment/user）→ 校验对象存在 → 查重（同一人同一对象待处理举报只能有一条）→ insert
- `handleReport()`：管理员处理 → 违规成立（status=1）时自动处理被举报对象：
  - **帖子**：status 设为 -1（删除），清缓存，清帖子列表缓存
  - **评论**：status 设为 -1（删除），清帖子详情缓存
  - **用户**：status 设为 0（封禁），清用户缓存，清帖子列表缓存，**强制下线**（删除该用户全部活跃会话）

---

### 8. 管理后台

- 管理员账号：phone=`luan`，password=`456281`（密码 BCrypt 存储，由后端 `DataInitializer` 启动时自动创建/升级，不在 SQL 中明文写入）
- 管理功能：用户管理（封禁/解封）、帖子管理（强制删除）、分类管理（增删改）、举报处理、操作日志查看
- 封禁即强制下线：`SessionService.forceLogout()` 按 `session:user:{userId}` 索引删除该用户所有 `session:idle:{token}`，其 JWT 下次请求即 401 → 前端自动登出（解封后需重新登录）
- 权限控制：`AdminOnlyAspect` 用 `@AdminOnly` 注解校验角色
- 操作审计：`AdminLogAspect` 用 `@AdminLog` 注解自动记录操作日志

---

## 三、缓存设计

### Key 模式

| Key 模式 | 用途 | TTL |
|---|---|---|
| `post:list:{cat}:{sort}:{page}:{size}` | 帖子列表 | 120s + 随机 0-60s |
| `post:search:{kw}:{cat}:{page}:{size}` | 搜索结果 | 120s + 随机 0-60s |
| `post:detail:{id}` | 帖子详情 | 1800s + 随机 0-600s |
| `post:null:{id}` | 空值标记（防穿透） | 300s |
| `post:liked:{id}` | 点赞用户集合 | 无 |
| `post:view:{id}` | 浏览计数 | 无 |
| `post:hot:24h` | 热榜 ZSET（每 30 分钟分数 ×0.8 衰减，<1 移出） | 无 |
| `user:{id}` | 用户信息 | 1800s + 随机 0-600s |
| `user:null:{id}` | 空值标记 | 300s |
| `lock:detail:{id}` | 分布式锁 | 5s 租约 |
| `lock:user:{id}` | 分布式锁 | 5s 租约 |
| `favorites:user:{id}` | 用户收藏集合 | 无 |
| `session:idle:{token}` | 活跃 session | 7d（与 JWT 对齐） |
| `session:user:{userId}` | 用户活跃 token 索引（封禁强制下线用） | 无 |
| `sms:{phone}` | 验证码 | 300s |

### 双写一致性策略

**更新数据时：先改 DB，再删缓存**

- 用户信息变化 → 只删用户缓存（帖子列表 2 分钟 TTL 自然刷新）
- 帖子内容/计数变化 → 删帖子详情缓存
- 帖子列表变化 → SCAN 清除所有 `post:list:*` 和 `post:search:*` 缓存

---

## 四、关键设计点

### 4.1 浏览量延迟刷库
- 每次查看详情时 `redisTemplate.opsForValue().increment(KEY_VIEW_COUNT + postId, 1)`
- `ViewCountFlushTask` 每 5 分钟执行一次：SCAN 所有 `post:view:*` key，读值后写入 MySQL `post.view_count`
- 避免每次浏览都写 DB，扛住高并发

### 4.2 热榜
- Redis ZSET `post:hot:24h`，score 加权：点赞+2，评论+3，收藏+4
- `RankController.getHotBoard()` 查 ZSET，取前 100 名，再拼帖子标题和作者名
- 无 TTL，冷帖自然沉底

### 4.3 图片上传
- 前端上传到 `/upload/image` → 后端校验格式/大小 → 传给阿里云 OSS → 返回 URL
- 头像目录 `avatar/`，帖子图片目录 `post/`
- 前端最多上传 9 张图，每张 ≤5MB

### 4.4 缓存批量清理
- `evictPostListCache()` 使用 Redis SCAN（游标模式，count=200）而不是 KEYS
- 避免大 key 数量时 Redis 阻塞

---

## 五、数据库表关系

```
user (1) ──< post          (作者发帖)
user (1) ──< comment       (作者评论)
user (1) ──< user_follow   (关注)
user (1) ──< notification  (通知接收者)
user (1) ──< report        (举报人/处理人)
user (1) ──< admin_log     (操作人)

post (1) ──< comment       (帖子评论)
post (1) ──< post_like     (点赞)
post (1) ──< post_favorite (收藏)

category (1) ──< post      (帖子分类)
```

---

## 六、开发启动

### 本地开发
1. 启动基础设施：MySQL + Redis + RabbitMQ
2. 启动后端：运行 `GeoCommunityApplication.java`
3. 启动前端：`cd frontend && npm run dev`（端口 3000，自动代理 API 到 8080）

### Docker 部署
```bash
docker compose up -d
```
> 已有数据库（用旧 schema.sql 初始化过）升级时，需手动执行一次 `sql/migrations.sql` 补齐新增索引；新装库会自动包含。
前端通过 Nginx 反向代理，API 路径 `/api/v1/` → `app:8080/`

### 内网穿透给别人看
前端开发服务器默认只允许 localhost 访问。修改 `frontend/vite.config.js`：
```js
server: {
  host: '0.0.0.0',          // 允许外部访问
  port: 3000,
  allowedHosts: ['你的natapp域名'],  // 放行 natapp 域名
}
```
natapp 转发 `localhost:3000`，Vite 自动代理 API 到后端 8080。

---

## 七、前端路由

| 路径 | 页面 | 权限 |
|---|---|---|
| `/` | 首页（帖子列表） | 公开 |
| `/login` | 登录 | 公开 |
| `/register` | 注册 | 公开 |
| `/post/:id` | 帖子详情 | 公开 |
| `/create` | 发帖 | 需登录 |
| `/user/:id` | 用户主页 | 公开 |
| `/user/center` | 个人中心 | 需登录 |
| `/search` | 搜索 | 公开 |
| `/notifications` | 通知 | 需登录 |
| `/favorites` | 收藏 | 需登录 |
| `/admin` | 管理后台 | 管理员 |
| `/admin/users` | 用户管理 | 管理员 |
| `/admin/categories` | 分类管理 | 管理员 |
| `/admin/reports` | 举报管理 | 管理员 |
