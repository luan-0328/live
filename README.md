# GeoCommunity 地方社区交流系统

GeoCommunity 是一个提供本地信息发布与互动功能的前后端项目。用户可以浏览和搜索帖子、发布图文内容、评论与回复、点赞收藏、关注用户；系统还包含通知、热榜、举报处理和管理后台。

## 技术栈

- 后端：Java 21、Spring Boot 3、MyBatis-Plus
- 数据与中间件：MySQL 8、Redis 7、Redisson、RabbitMQ
- 前端：Vue 3、Vite、Pinia、Element Plus
- 部署：Docker Compose、Nginx
- 图片存储：阿里云 OSS（需自行配置）

## 主要实现

- 使用 Redis 缓存帖子和用户数据，并通过空值缓存、分布式锁及过期时间随机化处理常见缓存问题。
- 对帖子、评论和用户操作进行服务端校验；点赞、收藏等关系由数据库唯一约束防止重复写入。
- 使用 RabbitMQ 异步处理通知，并通过 Outbox、重试和事件去重处理投递失败及重复消息。
- 使用 Redis 管理登录会话，支持会话续期和管理员封禁后的强制下线。
- 提供帖子搜索、附近内容查询、热榜、举报处置和后台操作日志等功能。

## 本地运行

准备 Docker、Node.js 以及 Java 21/Maven。先复制 `.env.example` 为 `.env`，填写数据库密码、随机 JWT 密钥和管理员初始密码；如需图片上传，再配置 OSS 参数。首次部署请按 [部署说明](DEPLOY.md) 操作。

仓库不包含构建产物。前端需在 `frontend` 目录运行 `npm install` 和 `npm run build`。后端可使用 Maven 构建，也可按部署说明使用 Docker 多阶段构建。

运行已有自动化检查：

```bash
mvn test
cd frontend
npm test
```

## 项目目录

```text
src/          Spring Boot 后端与自动化测试
frontend/     Vue 前端与前端测试
sql/          数据库初始化脚本及迁移脚本
nginx/        Nginx 配置
```

## 使用说明

短信验证码目前为开发模拟实现；图片上传需要有效的 OSS 配置。部署前请设置自己的环境变量，不要将 `.env` 或任何真实密钥提交到仓库。
