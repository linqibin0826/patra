# patra 后端服务通用运行镜像（Spring Boot 4 分层 jar）
# 6 个服务（registry / object-storage / catalog / ingest / identity / gateway）共用这一份——它们都用同一
# linqibin.hexagonal-boot 约定插件打包成 fat jar（archiveClassifier="" + 禁 plain jar），
# 故 COPY build/libs/*.jar 与服务无关，逐字节通用。唯一参数化点是 EXPOSE 端口（仅文档性）。
#
# 用法：docker build -f patra-infra/docker/service.Dockerfile --build-arg APP_PORT=<port> <boot 模块 context>
#
# 分层：builder 用 jarmode=tools 拆 4 层；runtime 按变化频率低→高逐层 COPY，使 ~80M 第三方依赖层
# 在依赖不变时被 docker 永久缓存复用，每次部署只传约几百 KB 的 application 层（弱网部署关键）。

# ---- builder：提取分层 ----
FROM eclipse-temurin:25-jre AS builder
WORKDIR /builder
# bootJar 产物：archiveClassifier="" 且禁用 plain jar，build/libs 下只有一个 fat jar
COPY build/libs/*.jar app.jar
# Spring Boot 4 用 jarmode=tools（旧版 layertools 已废弃）。提取出 dependencies /
# spring-boot-loader / snapshot-dependencies / application 四层到 extracted/。
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- runtime：精简运行镜像 ----
FROM eclipse-temurin:25-jre

# compose healthcheck 需要 curl（temurin jre 镜像默认不含）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 非 root 运行（纵深防御）。应用 logback 写 /app/logs/，故预建该目录并交给 appuser，
# 否则非 root 无权创建日志目录会导致启动失败（Exited 1）。
RUN useradd -r -u 10001 appuser \
    && mkdir -p /app/logs \
    && chown -R appuser:appuser /app

# 分层 COPY，顺序即缓存策略——越少变化的层越靠前，使其 docker 缓存命中率最大化：
#   dependencies        第三方 jar（build.gradle 不变则层 hash 不变 → 永久缓存）
#   spring-boot-loader  loader 类（SB4 tools 模式下内嵌于 app.jar，本层当前为空）
#   snapshot-dependencies  SNAPSHOT 第三方依赖（当前无，留层契合 Spring Boot 分层约定）
#   application         瘦启动 jar app.jar + patra 自身模块，每次 build 仅此层变
COPY --from=builder --chown=appuser:appuser /builder/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appuser /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appuser /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appuser /builder/extracted/application/ ./

USER appuser

# 各服务端口不同，由 build-arg 注入（EXPOSE 仅文档性，实际端口由 compose 映射）
ARG APP_PORT=8080
EXPOSE ${APP_PORT}

# 启动瘦 jar（非 uber jar）：仅含应用代码 + 对 lib/ 下已提取依赖的引用，CDS/AOT 友好。
# 容器感知内存（temurin 25 默认开启 container support；显式给上限更稳）。
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
