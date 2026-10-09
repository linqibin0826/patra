# patra-gateway-boot

Patra 的 API 网关：所有外部请求的统一入口，按路径前缀把请求路由到各微服务。Spring Cloud Gateway 的 **WebMVC 版**（servlet 栈），和仓库里其他服务同一套 Web 栈。

设计：[gateway 切换到 WebMVC 版工程设计（PAP-69）](../../docs/patra/specs/2026-10-09-gateway-webmvc-design.md)。鉴权在 PAP-65 加。

## 职责

- **路由**：`/patra-catalog/**`、`/patra-registry/**`、`/patra-ingest/**` 剥掉第一段后转给对应服务，经 Nacos 发现、Spring Cloud LoadBalancer 选实例。
- **转发头**：给下游加 `X-Forwarded-Host` / `Port` / `Proto` / `Prefix` 和 `Forwarded`，下游 springdoc 据此把 servers 还原成网关地址。逐跳头剥掉，`Authorization` 等其余请求头原样到达下游；下游收到的 `Host` 是它自己的地址。
- **透传**：下游的状态码、响应头、响应体原样回客户端，包括 4xx / 5xx / 3xx；网关不跟随重定向，也不替任何一方谈压缩：`Accept-Encoding` 原样到下游，`Content-Encoding` 和压缩字节原样回客户端。
- **文档聚合**：`/scalar` 聚合三个服务的 OpenAPI 文档。
- **可观测性**：OTel Agent + Micrometer，actuator 暴露 `health` / `info` / `metrics`。

## 模块结构

```
patra-gateway-boot/
├── src/main/java/dev/linqibin/patra/gateway/
│   ├── PatraGatewayApplication.java              # 启动类
│   ├── config/GatewayConfiguration.java          # 网关自己的装配（HTTP 客户端不谈压缩）
│   └── error/                                    # 代理失败 → 网关自己的应用异常（503 / 504，固定文案）
│       ├── GatewayProxyFailureAdvice.java        # 排在全局处理器前，先包装再交给它渲染
│       ├── ProxyFailureClassifier.java           # 到不了 → Unavailable，等不到 → Timeout
│       ├── DownstreamUnavailableException.java
│       └── DownstreamTimeoutException.java
├── src/main/resources/
│   ├── application.yml                           # 路由、HTTP 客户端、超时、虚拟线程、错误前缀
│   ├── application-dev.yml                       # dev：Nacos 用 TAILSCALE_IP 注册，DEBUG 日志
│   └── application-container.yml                 # 容器部署
└── src/integrationTest/java/dev/linqibin/patra/gateway/
    ├── PatraGatewayApplicationIT.java            # servlet 启动、JDK 客户端、虚拟线程
    ├── GatewayRoutingIT.java                     # 转发行为（WireMock 顶替下游）
    └── GatewayFailureIT.java                     # 失败状态码
```

## 路由

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          trusted-proxies: ".*"      # 激活 X-Forwarded-* 头；patra 仅 tailscale 内网暴露
          routes:
            - id: patra-catalog
              uri: lb://patra-catalog
              predicates:
                - Path=/patra-catalog/**
              filters:
                - StripPrefix=1
```

三条路由同形。例：`GET /patra-catalog/venues?page=0` → `GET http://<catalog 实例>/venues?page=0`，下游另收到 `X-Forwarded-Prefix: /patra-catalog`。

## HTTP 客户端、超时与线程

| 项 | 值 | 说明 |
|---|---|---|
| 客户端 | JDK HttpClient | `spring.http.clients.imperative.factory: jdk`，显式指定，不靠 classpath 自动探测（classpath 上有 Nacos 客户端带来的 Apache HttpClient 5，自动探测会选到它） |
| 连接超时 | 5 秒 | `spring.http.clients.connect-timeout` |
| 读超时 | 60 秒 | `spring.http.clients.read-timeout`。**从请求发出起算的总时长**，持续收到数据也不重置：任何响应（含流式）要在 60 秒内读完，超过则正在转发的响应体被切断 |
| 重定向 | 不跟随 | `spring.http.clients.redirects: dont-follow` |
| 压缩 | 关 | JDK 客户端默认会替请求补 `Accept-Encoding: gzip, deflate` 并解压响应、抹掉 `Content-Encoding`；网关在 `GatewayConfiguration` 里关掉它 |
| 线程 | 虚拟线程 | `spring.threads.virtual.enabled: true`，每个请求一个虚拟线程 |

WebMVC 版网关自己不带 HTTP 客户端实现，代理走 Boot 的 `RestClient`；Boot 按上面这组键装出 `ClientHttpRequestFactory`，网关的 RestClient 用的就是它。

## 错误

网关自身产生的错误走 starter-web 的全局处理器，`application/problem+json`，错误码前缀 `GW`（`linqibin.starter.core.error.context-prefix`）。下游自己的错误原样透传、不改写。

| 场景 | 状态码 | 错误码 |
|---|---|---|
| 未匹配任何路由 | 404 | `GW-0404` |
| `lb://` 找不到实例 | 503 | `GW-0503` |
| 连接被拒、连接超时、域名解析失败 | 503 | `GW-0503` |
| 响应头到达前读超时 | 504 | `GW-0504` |
| 响应头已到、响应体读超时，响应还没提交 | 500 | `GW-0500` |
| 同上，响应已提交（流式类型或已写出超过 Tomcat 缓冲） | 200，半截响应体 | 无 |

503 / 504 的 `detail` 分别固定为「下游服务暂时不可用」「下游服务响应超时」：`GatewayProxyFailureAdvice` 先把代理失败包成网关自己的应用异常再交给 starter-web 渲染，原始异常消息（带下游实例地址）只留在日志里。响应已提交后再出错，状态码无法再改，网关不再追加任何内容。

## API 文档聚合

`http://<gateway>:9528/scalar`（springdoc 3.0 的 webmvc Scalar 控制器映射在 `${scalar.path:/scalar}`）。`scalar.sources` 列出三个服务的 `/v3/api-docs`，经各自路由代理到下游；各服务需引入 `linqibin-spring-boot-starter-openapi`。

## 配置

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profile | `dev` |
| `NACOS_HOST` | Nacos 地址 | 跟随 `PATRA_INFRA_HOST`，再缺省为 `127.0.0.1` |
| `NACOS_PORT` | Nacos 端口 | `8848` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 凭据 | `nacos` / `nacos` |
| `TAILSCALE_IP` | dev 下向 Nacos 注册的 IP | 空 |
| `PATRA_LOG_DIR` | 日志目录 | `logs` |

端口 9528。超时与虚拟线程在 `application.yml` 里有值，不需要环境变量。

## 测试

```bash
./gradlew :patra-api:patra-gateway-boot:test              # 单元测试
./gradlew :patra-api:patra-gateway-boot:integrationTest   # 集成测试（WireMock 顶替下游，不连 Nacos）
```

集成测试的 `test` profile 只关掉 Nacos 的发现与注册，`spring.cloud.discovery.enabled` 保持开启，`lb://` 经 `spring.cloud.discovery.client.simple.instances.*` 拿到 WireMock 的地址。网关没有数据库，`build.gradle.kts` 在 `configurations.testImplementation` 上排除了测试 starter 带来的 JPA / JDBC / Flyway 测试模块；WireMock 关掉 h2c（`http2PlainDisabled`），JDK 客户端才会像对 Tomcat 一样走 HTTP/1.1。

## 技术栈

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3（gateway-server-webmvc 5.0.3） |
| Spring Cloud LoadBalancer | 随 Spring Cloud |
| Nacos Discovery | spring-cloud-alibaba 2025.1.0.0 |
| springdoc（webmvc Scalar） | 3.0.1 |
| Java | 25 |
