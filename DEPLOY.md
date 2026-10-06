# GeoCommunity 虚拟机 Docker 部署

方案：docker-compose 用 `image:` 拉取 MySQL/Redis/RabbitMQ/nginx 现成镜像，后端 jar 用薄 Dockerfile 拷进容器。**虚拟机只需装 Docker，不需要 Maven/Node**。

已有库升级先阅读 [FIXES_2026-10-03.md](FIXES_2026-10-03.md) 和 [回复与死信重试说明](REPLIES_RETRY_2026-10-03.md)，按顺序执行SQL迁移并移除旧RabbitMQ绑定。

本轮前后端协调修复见 [2026-10-05审查记录](FRONTEND_BACKEND_REVIEW_2026-10-05.md)，无需新增数据库迁移。

## 一、本机构建（Windows，一次性）

```bash
# 后端：打 jar，并复制为 app.jar（Dockerfile 固定拷 app.jar）
cd D:\post\live
mvn package -DskipTests
copy target\geo-community-1.0.0.jar app.jar

# 前端：构建 dist
cd frontend
npm install
npm run build     # 产出 frontend\dist
```

## 二、传输到虚拟机（推荐：一个压缩包搞定）

本机构建后，把部署所需的文件打成 1 个包（已含 `.env.example` 和本文件）：

```bash
cd D:\post\live
tar -czf geo-deploy.tar.gz app.jar Dockerfile docker-compose.yml \
    nginx/nginx.conf sql frontend/dist \
    .env.example DEPLOY.md FIXES_2026-10-03.md REPLIES_RETRY_2026-10-03.md FRONTEND_BACKEND_REVIEW_2026-10-05.md
```

把 `geo-deploy.tar.gz` 拷到 VM（scp/共享目录/任意方式均可），然后：

```bash
mkdir -p /opt/geo && cd /opt/geo
tar -xzf /path/to/geo-deploy.tar.gz
cp .env.example .env          # 必改：JWT_SECRET / ADMIN_INIT_PASSWORD / MYSQL_ROOT_PASSWORD
vi .env                       # 改完再启动，密码类变量只在首次初始化生效
docker compose up -d
```

解压后目录结构即为 compose 需要的相对路径：

```
/opt/geo/
├── app.jar
├── Dockerfile
├── docker-compose.yml
├── nginx/nginx.conf
├── sql/schema.sql            # MySQL 首次初始化建表（compose 自动挂载执行）
├── sql/migrations.sql        # 仅升级已有库时手动执行，全新部署用不到
├── frontend/dist/            # 前端静态文件
├── .env.example → .env       # 环境变量（启动前必须改）
└── DEPLOY.md
```

**不要**传 `target/`、`node_modules/`、`.env`（含密钥）、`src/`（jar 已含源码，VM 上不需要）。

## 三、环境变量（.env）

在 docker-compose.yml 同级创建 `.env` 文件，示例：

```bash
# 数据库 root 密码（MySQL 容器 + 后端连接用同一个）
MYSQL_ROOT_PASSWORD=请替换成强密码

# JWT 签名密钥，必须设 ≥32 字节随机串（不设则用开发占位值，令牌可被伪造）
# 生成：openssl rand -base64 48
JWT_SECRET=请替换成一段随机长字符串

# 内置管理员 luan 的初始密码（不设则用源码里的默认值 456281，等于公开）
# 仅在管理员账号首次创建时生效；账号已存在时改这里不会覆盖旧密码
ADMIN_INIT_PASSWORD=请替换成强密码

# RabbitMQ 账号（缺省 guest）
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

# 阿里云 OSS（可选：不配也能启动，只是上传图片不可用）
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET_NAME=
```

**环境变量怎么进容器（三层链路）**：
1. `.env` 文件放在 compose 同级，`docker compose` 自动读取。
2. compose 里 `app.environment` 用 `${OSS_ACCESS_KEY_ID:-}` 引用，把它传进容器环境。
3. Spring Boot 宽松绑定把环境变量自动映射到配置项：`OSS_ACCESS_KEY_ID` → `aliyun.oss.access-key-id`、`JWT_SECRET` → `jwt.secret`、`SPRING_DATASOURCE_PASSWORD` → `spring.datasource.password`。

> 未配 OSS 时应用也能启动，只有调用图片上传接口会返回「图片服务未配置，无法上传图片」，其余功能正常。

## 三点五、2核2G 机器必做：swap 与内存核对

compose 里已针对 2G 内存做过调优（JVM 限堆 320M、MySQL 关 performance_schema、RabbitMQ 降水位、各容器加 `mem_limit`）。但**还必须在宿主机加 swap**——阿里云默认不给 swap，内存尖峰时会被 OOM Killer 直接杀进程（通常是 MySQL，有数据损坏风险）。

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 开机自动挂载

free -h          # 确认 Swap 一行有 2.0Gi
```

**注意**：不要在服务器上执行 `mvn package` 或 `npm run build`，2G 内存编译必 OOM。前端和后端都在本地构建好再传上来。

各容器 `mem_limit` 之和约 1.5G，给系统和 dockerd 留约 500M，正常水位下够用。若 `docker stats` 看到某容器长期贴着上限，说明该调大机器规格了。

## 四、启动与验证

```bash
cd /opt/geo
docker compose config        # 语法检查（可选）
docker compose up -d         # 首次拉镜像并启动，等待 mysql 健康后再起后端
docker compose ps            # 应看到 mysql/redis/rabbitmq/app/nginx 5 个容器 Up
docker compose logs -f app   # 后端日志，出现"内置管理员账号已创建"即正常
```

浏览器访问 `http://<虚拟机IP>/`：
- 注册/登录：短信验证码固定 **123456**
- 密码规则：8~64 位，至少一个字母 + 一个数字，仅限字母/数字/常见符号
- 管理员：账号 `luan`，密码取 `.env` 的 `ADMIN_INIT_PASSWORD`（默认 `456281`，上线前务必改）
- 发帖、点赞、评论、搜索、热榜均可验证
- 未配 OSS 时上传图片会弹「图片服务未配置，无法上传图片」

## 五、注意事项

**端口暴露（重要）**：compose 里只有 nginx 的 80 对外开放，其余全部走 docker 内网：
- `app:8080` 和 `rabbitmq:15672` 仅绑定 `127.0.0.1`，公网不可达。想在服务器上排查后端：
  `curl -i http://127.0.0.1:8080/actuator/health`；想看 RabbitMQ 管理台，从本地建 SSH 隧道：
  `ssh -L 15672:127.0.0.1:15672 root@<服务器IP>`，然后本地访问 `http://localhost:15672`。
- `mysql:3306`、`redis:6379` 完全没有映射到宿主机（Redis 无密码，暴露到公网等于把 root 送人）。
  进容器操作：`docker compose exec mysql mysql -uroot -p` / `docker compose exec redis redis-cli`。
- **云服务器安全组只需放行 80（和 22）**，不要放行 3306/6379/5672/8080。

**其他**
- 首次启动 MySQL 会用 `sql/schema.sql` 初始化空库；数据持久化在 docker volume `mysql-data`。
  **改了 `.env` 里的 `MYSQL_ROOT_PASSWORD` 而 volume 已存在时，密码不会跟着变**（MySQL 只在首次初始化时读）。
  重置方式：`docker compose down -v` 会连数据一起删，谨慎使用。
- 改 `nginx/nginx.conf` 无需重建镜像：`docker compose restart nginx`。
- 改后端代码：重新打 jar 覆盖 `app.jar` 后 `docker compose up -d --build app`。
- 改前端代码：本地 `npm run build` 后重传 `frontend/dist`，`docker compose restart nginx` 即可，不用重建镜像。
- 短信验证码目前是**写死的 123456**（`AuthController.sendCode`，模拟实现），任何人可注册任意手机号。
  对外提供服务前必须接入真实短信服务。限流已就位（见下），但限流防的是"接口滥用和短信费"，
  在当前写死验证码的前提下它并不提升越权安全性。

## 六、限流与密码策略（排查用）

**密码**：8~64 位，至少一个字母 + 一个数字，仅限字母/数字/常见符号。注册和修改密码两处都校验。
中文密码被有意禁止——BCrypt 只取前 72 字节，UTF-8 下中文超 24 个字会被**静默截断**，
会出现"新密码设置成功但登不上"这类难排查的问题。

**限流状态全在 Redis 里**，没有数据库表也没有定时清理任务，靠 TTL 自动过期。
排查时进 `docker compose exec redis redis-cli`，用 `KEYS sms:*` / `KEYS login:*` 查看：

| key | 限制 | 触发后 |
|---|---|---|
| `sms:cd:{手机号}` | 60 秒 1 次 | 等 60 秒 |
| `sms:daily:{手机号}` | 24 小时内 10 次 | 等窗口重置 |
| `sms:ip:{客户端IP}` | 1 小时内 20 次 | 等窗口重置 |
| `sms:vfail:{手机号}` | 验证码校验失败 5 次 | 重新获取验证码 |
| `login:fail:user:{手机号}` | 密码错 3 次 | 等 2 分钟 |
| `login:fail:admin:{账号}` | 密码错 3 次 | 等 2 分钟 |

用户反馈"发不了验证码 / 登不上"时，先 `TTL <对应的 key>` 看剩余等待秒数。
要立刻放行可以手动删：`DEL sms:daily:13800000000`。

客户端 IP 取自 nginx 写入的 `X-Real-IP`（`getRemoteAddr()` 只会拿到 nginx 容器 IP）。
因为 `app:8080` 只绑回环地址，这个头**无法从外部伪造**——这两处配置是配套的，改动其一时要注意。
