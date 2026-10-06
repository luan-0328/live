# GeoCommunity Docker 部署全流程详解

> 目标：一台只装了 Docker 的 Linux 虚拟机，一条 `docker-compose up -d` 把整套系统跑起来。
> 本文讲清三件事：**要打包什么、前后端怎么配置、环境变量怎么进容器**。

---

## 一、一句话原理

`docker-compose up -d` 做的事 = **从 Docker Hub 拉公共镜像** + **用你的代码构建应用镜像** + **按编排关系启动所有容器**。

你的系统有 5 个容器：

| 容器 | 来源 | 说明 |
|---|---|---|
| mysql:8.0 | **Docker 拉取**（公共镜像） | 数据库，自带初始化 |
| redis:7-alpine | Docker 拉取 | 缓存 |
| rabbitmq:3-management-alpine | Docker 拉取 | 消息队列 |
| nginx:1.25-alpine | Docker 拉取 | 托管前端 + 反向代理 |
| app | **自己构建**（Dockerfile + app.jar） | 你的后端代码 |

**关键认知**：Docker 只能自动下载"公网上现成的镜像"（MySQL、Redis 这种通用软件）。**你自己的代码在 Docker Hub 上没有**，所以必须你自己打包好带过去——这就是"要打包什么"的由来。

---

## 二、要打包什么（3 类东西）

### ① 后端：一个 jar 包
```bash
cd D:\post\live
mvn package -DskipTests                  # 编译整个后端源码 → target/geo-community-1.0.0.jar
copy target\geo-community-1.0.0.jar app.jar   # 复制成 Dockerfile 约定的文件名
```
> jar 已经把编译后的字节码 + 依赖 + 配置文件全装进去了，VM 上不需要装 Maven/JDK 来编译，只需要 Java 运行时来跑（运行时由 Docker 镜像提供）。

### ② 前端：一个 dist 目录
```bash
cd frontend
npm install     # 首次需要，装依赖
npm run build   # vite 打包 → frontend/dist/（index.html + assets/）
```
> dist 是浏览器能直接跑的静态文件（HTML/CSS/JS），nginx 直接托管它。

### ③ 配置文件（决定了怎么拼装）
| 文件 | 作用 |
|---|---|
| `Dockerfile` | 把 jar 包成镜像的"配方" |
| `docker-compose.yml` | 编排 5 个容器（拉哪些镜像、端口、环境变量、依赖顺序） |
| `nginx/nginx.conf` | 前端托管 + API 反向代理规则 |
| `sql/schema.sql` | MySQL 首次启动自动建表 |
| `.env.example` | 环境变量模板（复制成 .env） |

**不需要传**：`target/`（编译产物重复）、`frontend/node_modules/`（依赖重复）、`src/`（源码已在 jar 里）、`.env`（含密钥）。

---

## 三、后端配置（Dockerfile + 环境变量）

### Dockerfile —— 把 jar 变成镜像
```dockerfile
FROM eclipse-temurin:21-jre-jammy    # 基础镜像 = Java 21 运行时（ubuntu 底座）
WORKDIR /app                         # 工作目录
COPY app.jar .                       # 把你的 jar 放进去
EXPOSE 8080                          # 声明容器监听 8080
ENTRYPOINT ["java", "-jar", "app.jar"]  # 启动命令
```
> Java 镜像在 `build` 时被拉取（就是之前问的"java 镜像哪来的"）。应用容器 = Java 运行时 + 你的 jar。

### 应用怎么知道连哪个 MySQL？
后端源码里 `application.yml` 默认连 `localhost`，但容器环境变量会**覆盖**它。compose 给 app 传了：

```
SPRING_PROFILES_ACTIVE: docker        # 激活 application-docker.yml（host 写 mysql/redis/rabbitmq 服务名）
SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/community?...   # 直接覆盖数据库地址
SPRING_DATA_REDIS_HOST: redis         # Redis 地址
SPRING_RABBITMQ_HOST: rabbitmq        # MQ 地址
```
> 在 docker 内部网络里，**服务名（mysql/redis/rabbitmq）就是主机名**，容器间互相用名字访问，不走外部 IP。

---

## 四、前端配置（nginx 托管 + 反代）

前端是个纯静态站，需要 web 服务器托管。nginx 一个容器干两件事：

### ① 托管静态文件
compose 把 `./frontend/dist` 挂载进 nginx 容器的 `/usr/share/nginx/html`，这就是浏览器打开页面后看到的内容。

### ② 反向代理 API（前后端对接的关键）
浏览器页面调接口时，请求是 `http://虚拟机IP/api/v1/xxx`（**同源**，前端代码里 `baseURL = '/api/v1'`）。
nginx 收到后转发给后端容器，并**剥掉 /api/v1 前缀**：

```nginx
location /api/v1/ {
    proxy_pass http://app:8080/;     # 转发到 app 容器，/api/v1 前缀被去掉
    ...
}
```
> 所以前端代码里写死 `/api/v1` 就行，不用管后端 IP 是多少——nginx 在中间搭桥。这就是前后端对接方式。

### 为什么 SPA 刷新不 404
```nginx
location / {
    try_files $uri $uri/ /index.html;   # 找不到路由就返回 index.html，交给前端 router
}
```

---

## 五、docker-compose.yml 逐块拆解

```yaml
services:
  mysql:
    image: mysql:8.0                    # ① 拉取现成镜像
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-1234}   # 初始化密码
      MYSQL_DATABASE: community
    volumes:
      - mysql-data:/var/lib/mysql       # ② 数据持久化到命名卷，删容器不丢数据
      - ./sql/schema.sql:/docker-entrypoint-initdb.d/schema.sql  # 首次启动自动建表
    healthcheck:                        # ③ 健康检查，mysql 就绪后才让 app 启动
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]

  app:
    build: .                            # ④ 用 Dockerfile 构建（不是拉镜像）
    environment:
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_ROOT_PASSWORD:-1234}
      JWT_SECRET: ${JWT_SECRET:-...}
      OSS_ACCESS_KEY_ID: ${OSS_ACCESS_KEY_ID:-}
    depends_on:
      mysql: { condition: service_healthy }   # ⑤ 等 mysql 健康了再启动

  nginx:
    image: nginx:1.25-alpine
    ports: ["80:80"]                    # ⑥ 对外只开 80，其余端口只在容器内部
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - ./frontend/dist:/usr/share/nginx/html:ro
```

**端口策略**：对外（宿主机器）只暴露 80（页面）和 3306/6379/5672/15672（调试用）。页面访问走 80 → nginx → app。

---

## 六、环境变量三层链路（关键理解）

```
① .env 文件（compose 同级目录）
      ↓ docker compose 自动读取
② compose 里 environment: 用 ${VAR:-默认值} 引用，把变量传进容器
      ↓ 容器内就是普通环境变量
③ Spring Boot 宽松绑定自动映射：
      OSS_ACCESS_KEY_ID       → 配置项 aliyun.oss.access-key-id
      JWT_SECRET              → 配置项 jwt.secret
      SPRING_DATASOURCE_PASSWORD → spring.datasource.password
```

`.env` 模板（`cp .env.example .env` 后改）：
```bash
MYSQL_ROOT_PASSWORD=1234              # MySQL 密码（首次初始化生效，之后别改）
JWT_SECRET=一段随机长字符串            # token 签名密钥
RABBITMQ_USER=guest
RABBITMQ_PASS=guest
OSS_ACCESS_KEY_ID=                    # 阿里云 OSS（可选，不配也能启动）
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET_NAME=
```

**`:-` 的含义**：`${OSS_ACCESS_KEY_ID:-}` = 如果 .env 没写就传空字符串；`${JWT_SECRET:-xxx}` = 没写就用 xxx 兜底。所以**不建 .env 也能启动**，.env 只是用来覆盖。

---

## 七、完整流程（本地 → 虚拟机）

**本地（Windows，一次性）**
```bash
mvn package -DskipTests && copy target\geo-community-1.0.0.jar app.jar
cd frontend && npm install && npm run build
tar -czf ../geo-deploy.tar.gz app.jar Dockerfile docker-compose.yml \
    nginx/nginx.conf sql/schema.sql frontend/dist .env.example DEPLOY.md
```

**虚拟机**
```bash
mkdir -p /opt/geo && cd /opt/geo
tar -xzf geo-deploy.tar.gz
cp .env.example .env        # 可选：改 JWT_SECRET / 填 OSS
docker-compose up -d        # 拉镜像 + 构建 + 启动，全自动
docker-compose ps           # 5 个容器 Up 即成功
```

---

## 八、踩过的坑（避雷）

| 坑 | 原因 | 正确做法 |
|---|---|---|
| `Unsupported character encoding 'utf8mb4'` | JDBC 连接串写了 MySQL 字符集名，驱动不认 | 用 `characterEncoding=UTF-8` |
| 容器 Exit 1 | 连不上 MySQL/密码不符 | 看 `docker-compose logs app` |
| `docker.mirrors.ustc.edu.cn` 拉取失败 | 镜像源失效 | 换可用镜像源或直连 Docker Hub |
| 改 MYSQL_ROOT_PASSWORD 后连不上库 | 密码只在首次初始化生效 | 密码定好别再改，或删数据卷重来 |
| 改配置后容器没变化 | compose 没重建 | `docker-compose up -d --force-recreate` 或 `down` 后 `up` |
