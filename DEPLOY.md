# Docker 部署

本项目使用 Docker Compose 启动 MySQL、Redis、RabbitMQ、后端和 Nginx。公网入口为 Nginx 的 80 端口；数据库、缓存、消息队列和后端端口不应直接暴露到公网。

## 首次部署

需要 Docker Compose、Node.js 和 npm。后端使用仓库中的多阶段 Dockerfile 构建，不需要在宿主机安装 Java 或 Maven。

1. 在仓库根目录复制 `.env.example` 为 `.env`，设置 `MYSQL_ROOT_PASSWORD`、至少 32 个字符的随机 `JWT_SECRET` 和管理员初始密码 `ADMIN_INIT_PASSWORD`。初始密码仅在管理员账号首次创建时使用。图片上传需要另外配置 OSS 参数。
2. 构建前端静态文件：

   ```bash
   cd frontend
   npm install
   npm run build
   cd ..
   ```

3. 在仓库根目录构建并启动服务：

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.clone.yml up -d --build
   ```

4. 查看容器状态和后端日志，然后访问 `http://<服务器地址>/`：

   ```bash
   docker compose ps
   docker compose logs -f app
   ```

全新数据库会由 MySQL 容器根据 `sql/schema.sql` 初始化。已有数据库升级前请先备份，再按顺序执行 `sql/migrations_2026-10-03.sql` 和 `sql/migrations_2026-10-03_replies_retry.sql`。修改 `.env` 中的数据库密码不会更改已初始化数据卷里的密码。

## 本地开发

可先用 `docker-compose.dev.yml` 启动 MySQL、Redis 和 RabbitMQ，再分别启动后端和前端：

```bash
docker compose -f docker-compose.dev.yml up -d
mvn spring-boot:run
```

另开终端运行前端：

```bash
cd frontend
npm install
npm run dev
```

本地开发配置会将基础设施端口映射到宿主机，只应在可信的开发环境中使用。

## 常用命令

```bash
docker compose ps
docker compose logs -f app
docker compose down
```

`docker compose down -v` 会删除数据库和 Redis 数据卷，请谨慎使用。停止服务但保留数据时使用 `docker compose down`。
