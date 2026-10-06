# ============================================================
# 部署方式：本地 `mvn package` 打出 app.jar 后上传服务器（见 DEPLOY.md）
#
# 这样服务器不需要装 Maven、也不用联网拉依赖，构建只要几秒 —— 对国内服务器更友好。
# 因此 app.jar 是**构建产物**，不进 git（.gitignore 已排除）。
#
# 如果你手上只有源码、没有 app.jar（例如刚 clone 本仓库），用多阶段构建版本：
#     docker compose -f docker-compose.yml -f docker-compose.clone.yml up -d --build
# ============================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY app.jar .
EXPOSE 8080
# 2C2G 机器必须显式限堆：不加 -Xmx 时 JVM 按物理内存 1/4 取默认值，会顶到 swap 甚至被 OOM Killer 杀掉。
# 小堆（<512M）用 SerialGC 比 G1 更省内存和线程开销；堆内存溢出时直接退出交由 restart 策略拉起，好过进程僵死。
ENTRYPOINT ["java", \
            "-Xms192m", "-Xmx320m", \
            "-XX:MaxMetaspaceSize=128m", \
            "-XX:+UseSerialGC", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "app.jar"]
