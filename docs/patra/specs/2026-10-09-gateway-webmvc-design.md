# gateway 切换到 WebMVC 版工程设计（PAP-69）

> **Issue**：[PAP-69](https://linear.app/papertrace/issue/PAP-69)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)（决策 H）
> **前置设计**：[安全 starter（PAP-62 / PAP-70）](2026-10-05-security-starter-design.md) 第 17 节
> **后续 Issue**：[PAP-65](https://linear.app/papertrace/issue/PAP-65) 在本设计之上加鉴权
> **日期**：2026-10-09
> **状态**：已实施（2026-10-09，分支 `feat/v0.8-accounts-api`）

## 1. 要解决的问题

网关现在是 Spring Cloud Gateway 的 WebFlux 版，仓库里其余服务全是 servlet。安全 starter 只做了 servlet 一套：过滤器链、`SecurityServletAutoConfiguration`、ProblemDetail 错误输出、同步查 Redis，都进不了 WebFlux 网关。PAP-65 要在网关上加鉴权，前提是网关先换成 WebMVC 版。

本 Issue 只换技术栈，不加任何鉴权逻辑。迁移后对外行为要与迁移前一致；顺手把今天没有的两件事定下来：下游失败时的状态码（不再是 500）和每个请求的超时上限。

**完成标准**：PAP-69 的 Acceptance Criteria。

## 2. 范围

做：

- 依赖、配置键、API 文档聚合换成 WebMVC 版；版本目录里指向 WebFlux 的别名清掉。
- HTTP 客户端显式指定为 JDK HttpClient，连接超时 5 秒、读超时 60 秒，不跟随重定向。
- 网关开虚拟线程。
- 网关自身产生的错误接 starter-web，输出 ProblemDetail，错误码前缀 `GW`。
- starter-web 的全局处理器补上 Spring MVC 自带异常（未匹配路径等）的错误码渲染，所有服务受益。
- 集成测试钉住转发行为与三种下游失败的状态码；对照 mini 回归。
- README 重写；安全 starter 设计第 14 节写回全局处理器对代理失败的实测。

不做：

- 鉴权、路由级规则、拦内部接口和 actuator：PAP-65。
- 熔断、重试、限流、WebSocket 代理。
- 其他服务的任何改动；mini 上 compose、`.env.gateway`、健康检查不动。
- 路由改写成 Java 的 RouterFunction DSL：三条路由不值得，PAP-65 的过滤器链也不依赖路由怎么写。

## 3. 已定的前提

- 发布规格决策 H：网关切 WebMVC 版，全仓库只留 servlet 一套；先换栈并回归，再加鉴权。
- Spring Cloud 2025.1.3，`spring-cloud-gateway-server-webmvc` 5.0.3；Spring Boot 4.0.8；Java 25。
- WebMVC 版网关自己不带 HTTP 客户端实现，用 Boot 的 `RestClient`，请求工厂来自 Boot 的 `spring.http.clients.*` 配置（Boot 4 现行键名；`spring.http.client.*` 已标记弃用）。
- WebMVC 版的 `spring.cloud.gateway.server.webmvc` 下只有 `routes`、`trusted-proxies`、`x-forwarded.*`、`forwarded-by-enabled`、`streaming-*`、`use-framework-retry-filter`，没有 `http-client` 一组。
- `lb://` 由 `spring-cloud-loadbalancer` 处理，找不到实例时过滤器抛 `HttpServerErrorException(503)`（5.0.3 源码 `LoadBalancerFilterFunctions`），不是直接写响应；要靠异常处理器接住。
- 网关代理下游时用 `RestClient.exchange(..., false)`，下游的任何状态码都原样成为响应、不抛异常；所以进到异常处理器的 `HttpServerErrorException` 只会来自网关自己。
- Spring 7.0.9 的 `JdkClientHttpRequest` 把读超时实现成从请求发出起算的总时长计时器，只在响应体流关闭时取消；收到响应头不会重置它。
- 现状：网关只有一个启动类、三条路由（ingest、registry、catalog，各 `StripPrefix=1`）、`trusted-proxies: ".*"`、Scalar 聚合三份文档、actuator 暴露 `health,info,metrics`；mini 上以 `ghcr.io/linqibin0826/patra-gateway` 镜像跑在 9528，健康检查打 `/actuator/health`。

## 4. 依赖与模块

模块不变，仍是单个 boot 模块 `patra-api/patra-gateway-boot`，包 `dev.linqibin.patra.gateway`。

| 改动 | 内容 |
|---|---|
| 换 | `spring-cloud-starter-gateway-server-webflux` → `spring-cloud-starter-gateway-server-webmvc`（版本目录别名 `spring-cloud-starter-gateway` 改指 webmvc，网关用别名） |
| 换 | `springdoc-openapi-starter-webflux-scalar` → `springdoc-openapi-starter-webmvc-scalar`（别名 `springdoc-openapi-scalar`）；删掉 `springdoc-openapi-webflux-scalar` 别名 |
| 加 | `:linqibin-commons:linqibin-spring-boot-starter-web`：ProblemDetail 全局处理器 |
| 加 | 集成测试依赖 WireMock（版本目录 `wiremock-standalone`），顶替下游 |
| 改 | `linqibin-spring-boot-starter-web`：`GlobalRestExceptionHandler` 继承自 `ResponseEntityExceptionHandler`，Spring MVC 自带异常被父类接走、绕过错误引擎，渲染出的 ProblemDetail 没有 `code`。覆写 `handleNoResourceFoundException` / `handleNoHandlerFoundException` 改走 `ProblemDetailAdapter`，并把这两个异常映射为 `NOT_FOUND` |
| 不变 | `spring-cloud-starter-loadbalancer`、starter-core、starter-observability、starter-test |

网关里新增代码只有两处：`error/GatewayErrorMappingContributor`（第 8 节）和声明它的 `GatewayConfiguration`；starter-web 的改动见上表。

## 5. 配置

`application.yml` 的改动：

```yaml
spring:
  threads:
    virtual:
      enabled: true
  http:
    clients:
      imperative:
        factory: jdk
      connect-timeout: 5s
      read-timeout: 60s
      redirects: dont-follow
  cloud:
    gateway:
      server:
        webmvc:
          trusted-proxies: ".*"
          routes:
            # 三条路由原样：id、lb:// 地址、Path 谓词、StripPrefix=1 一字不改

linqibin:
  starter:
    core:
      error:
        context-prefix: GW
```

- `trusted-proxies` 在 WebMVC 版里同样是激活 `X-Forwarded-*` 头的开关，dev 环境信任所有上游，和今天一样。
- 日志类别 `org.springframework.cloud.gateway` 覆盖 WebMVC 版的包 `org.springframework.cloud.gateway.server.mvc`，dev 的 DEBUG 不用改。
- `application-dev.yml`、`application-container.yml`、Nacos、actuator、`scalar.sources`、端口、日志目录都不动。
- 没有新增环境变量：超时和虚拟线程都在 yml 里有值。

## 6. HTTP 客户端、超时与线程模型

- **客户端**：JDK HttpClient，由 `spring.http.clients.imperative.factory: jdk` 显式指定，不靠 Boot 的 classpath 自动探测（探测会选到 Apache HttpClient 5，它在上游返回 304 时有未修复的连接泄漏）。网关是这个进程里唯一的 HTTP 客户端，这几个键就是它唯一的来源。
- **超时**：连接 5 秒，读 60 秒。今天的 WebFlux 网关没有响应超时，下游一直不回请求就一直挂着；换成每请求一线程的模型后必须有上限。60 秒照顾 catalog 偶尔慢的检索接口。响应头到达前超时返回 504（第 8 节）。
- **读超时是总时长**：JDK 客户端的计时器从请求发出起算，响应体流关闭才取消，持续收到数据也不重置。后果是任何响应（包括流式）从发出到读完都不能超过 60 秒，超过时正在转发的响应体被切断，此时状态码已经发出，改不成 504。Patra 目前没有 SSE 接口，门户的读接口都是短响应，所以本版接受这个限制并用测试钉住；将来引入 SSE 的 Issue 要给流式路由单独的客户端或不设读超时（第 13 节）。
- **重定向**：`dont-follow`。网关不能替客户端跟随下游的 3xx，Reactor Netty 默认也不跟随；Boot 的默认值是「能跟就跟」，所以要显式关掉。
- **虚拟线程**：只给网关开。Tomcat 给每个请求一个虚拟线程，等下游的那段时间不占平台线程；Java 25 没有 synchronized 钉住载体线程的问题。其他服务的瓶颈在数据库连接池，本版不开。虚拟线程让等待变便宜，超时让等待有限，两者都要。

## 7. 转发行为：迁移前后必须一致的事

| 项 | 行为 |
|---|---|
| 路径 | `/patra-catalog/venues?page=0` 到下游是 `/venues?page=0`：剥掉第一段，查询串、方法、请求体原样 |
| 响应 | 下游的状态码、响应头、响应体原样回客户端，包括 4xx / 5xx 和 3xx |
| `X-Forwarded-*` | 下游收到 `X-Forwarded-Host` / `Port` / `Proto` / `Prefix`，`Prefix` 是剥掉的那一段（`/patra-catalog`）；catalog 的 springdoc 靠它把 servers 还原成网关地址 |
| `Forwarded` | 照旧写入 |
| 逐跳头 | `Connection`、`Transfer-Encoding` 等逐跳头照旧剥掉；`Authorization` 不是逐跳头，原样到达下游 |
| 流式响应 | `text/event-stream` 等流式类型照旧边收边转（WebMVC 版默认的 `streaming-media-types` 含 SSE），但整个响应受 60 秒总时长限制（第 6 节），这是与迁移前唯一的有意差别 |
| 网关自己的端点 | `/actuator/**`、`/scalar` 由网关自己响应，不走路由 |
| 未匹配路由 | 404（响应体格式见第 8 节） |

## 8. 错误输出与失败状态码

网关自身产生的错误走 starter-web 的全局处理器，输出 `application/problem+json`，错误码由 `HttpStdErrors.Group` 按前缀 `GW` 生成，和 identity 的 `IDN-` 同一套办法。

| 场景 | 状态码 | 响应体 | 来源 |
|---|---|---|---|
| 未匹配路由 | 404 | ProblemDetail，`GW-0404` | 没有路由命中，DispatcherServlet 抛 `NoResourceFoundException`，由改造后的 starter-web 处理器渲染（第 4 节） |
| `lb://` 找不到实例 | 503 | ProblemDetail，`GW-0503` | LoadBalancer 过滤器抛 `HttpServerErrorException(503)`，`GatewayErrorMappingContributor` 映射 |
| 连接被拒、连接超时、域名解析失败 | 503 | ProblemDetail，`GW-0503` | `GatewayErrorMappingContributor` |
| 读超时 | 504 | ProblemDetail，`GW-0504` | 同上 |
| 下游 4xx / 5xx / 3xx | 原样 | 原样 | 透传，网关不改写 |

`GatewayErrorMappingContributor` 实现 starter-core 的 `ErrorMappingContributor`，认两种异常。一是网关自己抛的 `HttpServerErrorException`，状态码 503 时映射为 `UNAVAILABLE()`（下游的状态码不会以异常形式出现，见第 3 节）。二是 `ResourceAccessException`（`RestClient` 到不了下游时抛的 I/O 异常），沿原因链分类：

- `HttpConnectTimeoutException`、`ConnectException`、`UnknownHostException`、`UnresolvedAddressException` → `UNAVAILABLE()`（503）。`HttpConnectTimeoutException` 是 `HttpTimeoutException` 的子类，必须先判。
- 其余 `HttpTimeoutException`、`SocketTimeoutException` → `GATEWAY_TIMEOUT()`（504）。
- 原因链里没有上面任何一种的 `ResourceAccessException` → 503：到不了下游就是不可用。
- 其他异常返回空，交给引擎原有规则。

没有这个 contributor，错误引擎会把 `ResourceAccessException` 和 `HttpServerErrorException` 都判成 500（`GW-0500`），这正是 Issue 里「下游不可用返回 503 而不是 500」要堵的口子。

迁移前 WebFlux 对未知路由给的是 Spring 默认 JSON；迁移后状态码不变、响应体换成 ProblemDetail，是 Issue 明确要的变化。

## 9. API 文档聚合

Scalar 换成 springdoc 的 webmvc 版 starter，`scalar.sources` 不变：三份 `/v3/api-docs` 仍经各自的路由代理到下游。各服务文档里的 servers 地址靠 `X-Forwarded-*` 还原成网关地址，这是第 7 节要钉住的行为之一。

## 10. 测试策略

单元测试：

- `GatewayErrorMappingContributor`：`HttpServerErrorException(503)` → 503；连接类原因 → 503，读超时类 → 504，`HttpConnectTimeoutException` 不被当成读超时，没原因的 `ResourceAccessException` → 503，其他异常 → 空。
- starter-web：`NoResourceFoundException` / `NoHandlerFoundException` 渲染出的 ProblemDetail 带 `code`，状态仍是 404；现有的参数校验用例不变。

集成测试（`src/integrationTest`，`@SpringBootTest` 用真实端口加 `RestTestClient`，`test` profile 关掉 Nacos）：

- 关 Nacos 只关两项：`spring.cloud.nacos.discovery.enabled=false`、`spring.cloud.service-registry.auto-registration.enabled=false`。不能照搬 identity 的 `spring.cloud.discovery.enabled=false`：`LoadBalancerClientConfiguration` 整个挂在 `@ConditionalOnDiscoveryEnabled` 下，关了它 `lb://` 就没有实例来源。
- 下游用 WireMock 顶替。路由配置用生产那份不改，靠 `spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri` 把 `lb://patra-catalog` 指到 WireMock 的端口（`@DynamicPropertySource`），这样 `lb://` 这条路真的走到。
- `test` profile 把读超时压到 1 秒。
- 要钉住的行为：
  1. 转发与剥前缀：`/patra-catalog/venues?page=0` 到下游是 `/venues?page=0`，状态码、响应头、响应体原样回来。
  2. 下游收到 `X-Forwarded-Host` / `Port` / `Proto` / `Prefix`，`Prefix` 是 `/patra-catalog`。
  3. 外部请求的 `Authorization` 头原样到达下游。
  4. 下游回的 ProblemDetail 422 原样透传。
  5. 下游回 302 时客户端收到 302，网关不跟随。
  6. 未知路由：404，`application/problem+json`，错误码 `GW-0404`。
  7. 找不到实例：`lb://patra-ingest` 不配实例，503，`application/problem+json`，`GW-0503`。
  8. 连接被拒：实例指向没人监听的端口，503，`GW-0503`。
  9. 读超时：WireMock 固定延迟超过 1 秒，504，`GW-0504`。
  10. 容器里网关持有的 `ClientHttpRequestFactory` 是 `JdkClientHttpRequestFactory`。
  11. 请求处理线程是虚拟线程：测试配置里挂一个过滤器记录 `Thread.currentThread().isVirtual()`。
  12. 应用以 servlet 启动；classpath 上没有 WebFlux 版网关的自动配置类。
  13. `/scalar` 返回 200。
  14. 读超时是总时长：WireMock 分块慢速发送一个超过 1 秒的响应体，客户端收到 200 后响应被切断；钉住第 6 节接受的限制，框架行为变了能立刻知道。

回归门控：`./gradlew :patra-api:patra-gateway-boot:check`、`:integrationTest`；全仓库 `./gradlew check`；`./gradlew dumpModuleGraph` 后模块图与构建一致（网关新增 starter-web 依赖，`patra-infra/cd/module-graph.json` 会变）。

## 11. 回归对照 mini

本机以 dev profile 起网关，它经 Nacos 发现的就是 mini 上的那批服务。同一批 URL 分别打 mini 的网关（迁移前）和本机网关（迁移后），对比状态码、`Content-Type`、响应体：

- 门户用到的 catalog 读接口；
- 三个服务的 `/v3/api-docs`，重点看 servers 地址是否仍指向网关；
- `/scalar`、一个不存在的路径、`/actuator/health`。

门户本机起、网关地址指本机，跑现有的 Playwright e2e；再手工过首页、`/papers`、`/papers/[id]`、`/journals`、`/journals/[id]`。对照结果写进第 12 节。

## 12. 实施时要实测的点

结果写回本节（2026-10-09 实施时实测；对照 mini 的两条见文末说明）。

1. 找不到实例。实测（`GatewayFailureIT`）：到达处理器的是 `HttpServerErrorException`，经 contributor 映射后响应 503、`application/problem+json`、`GW-0503`。
2. JDK 客户端下连接被拒与读超时的原因链。实测：连接被拒是 `ResourceAccessException → java.net.ConnectException`（503）；响应头到达前超时是 `ResourceAccessException → java.net.http.HttpTimeoutException("Request cancelled")`（504）。第 8 节的分类不用改。另外 `ResourceAccessException` 的构造只收 `IOException` 原因，`UnresolvedAddressException` 不可能直接挂在它下面，真实形态是包在 `ConnectException` 里，contributor 先判 `ConnectException` 已覆盖。
3. 全局处理器对代理失败的表现。实测：代理失败以异常到达 `GlobalRestExceptionHandler`，经错误引擎解析；没有 contributor 时判成 500 `GW-0500`。已写回安全 starter 设计第 14 节。
4. `X-Forwarded-Prefix` 与下游 springdoc 的 servers。实测（`GatewayRoutingIT`）：下游收到 `X-Forwarded-Host` / `Port` / `Proto` 和 `X-Forwarded-Prefix: /patra-catalog`，`Forwarded` 照旧写入。与 mini 上 WebFlux 版的对照见文末。
5. 重定向。没有单测 Boot 的默认值；`dont-follow` 下下游的 302 与 `Location` 原样到客户端，下游没有收到对 `/new` 的请求。注意测试客户端 `RestTestClient` 自己会跟随 302，看到的会是它跟去 `/new` 后网关给的 404，这条用例要用不跟随重定向的 JDK `HttpClient` 打网关。
6. 下游收到的 `Host`。实测：`Host: localhost:<下游端口>`，即下游自己的地址，网关不保留客户端的 Host。WireMock 3 默认开 h2c，JDK 客户端会升级到 HTTP/2、Host 变成看不见的 `:authority`，测试里 `http2PlainDisabled(true)`；Tomcat 默认不开 h2c，真实下游不受影响。
7. 虚拟线程下的运行。实测：本机以 dev profile 启动，Tomcat 1.4 秒起来，`PatraGatewayApplicationIT` 证实请求跑在虚拟线程上；Nacos 与 OTel 的联通对照见文末。
8. Apache HttpClient 5。实测：`httpclient5 5.6.4` 在运行时 classpath 上，由 `com.alibaba.nacos:nacos-client:3.1.1` 带入；Boot 的自动探测会选到它，`factory: jdk` 之后它只是闲置依赖，`PatraGatewayApplicationIT` 断言容器里的 `ClientHttpRequestFactory` 是 `JdkClientHttpRequestFactory`。
9. 读超时切断流式响应。实测（`GatewayFailureIT`）：响应体转发到一半被切断时，网关侧抛 `IOException`（`closed`、`chunked transfer encoding, state: READING_DATA`、`subscription cancelled`），此时响应已提交，全局处理器仍按 500 `GW-0500` 走了一遍并打了 ERROR 日志，但状态码改不了；客户端拿到半截响应体或连接异常。

实施时另外发现的三点：

- 文档聚合页的路径是 `/scalar`，不是 README 原来写的 `/scalar.html`：springdoc 3.0.1 的 webmvc Scalar 控制器映射在 `${scalar.path:/scalar}`。第 7 节与 README 已改。
- Boot 4.0.8 的 `ImperativeHttpClientAutoConfiguration` 会用 builder 加 `spring.http.clients.*` 的设置装出一个 `ClientHttpRequestFactory` Bean，WebMVC 版网关的 RestClient 优先用它。所以「不声明 `ClientHttpRequestFactory` Bean」仍成立，但容器里有 Boot 装的那个，测试直接断言它的类型。
- 项目的测试 starter 带着 `spring-boot-starter-data-jpa-test` / `jdbc-test` / `flyway-test`，`hexagonal-boot` 插件给每个 boot 模块都加了它；网关没有数据库，测试上下文会因为建不出 `DataSource` 起不来。网关在 `configurations.testImplementation` 上把这几个模块排除掉。

**对照 mini**（第 11 节，2026-10-09 实测）。本机以 dev profile 起迁移后的网关，经 mini 上的 Nacos 发现服务（实例地址是 mini 的局域网 IP `192.168.97.x`，本机同网段直连）；同一批 URL 分别打 mini 上迁移前的 WebFlux 网关和本机网关：

| 路径 | 迁移前 | 迁移后 | 结论 |
|---|---|---|---|
| 门户用到的 7 个 catalog 读接口（文献流 `tab=recent`、检索、检索 facets、文献详情、期刊列表、期刊 facets、期刊详情） | 200 | 200 | `Content-Type` 与响应体逐字节一致（忽略 `traceId`、`timestamp`） |
| `publications?tab=latest`（下游校验失败） | 422 ProblemDetail `CAT-0422` | 同 | 下游错误原样透传 |
| `/patra-catalog/v3/api-docs` | 200，`servers[0].url=http://100.103.73.27:9528/patra-catalog` | 200，`servers[0].url=http://localhost:9528/patra-catalog` | 各指向自己的网关地址，`X-Forwarded-*` 生效 |
| `/patra-registry/v3/api-docs`、`/patra-ingest/v3/api-docs` | 404 ProblemDetail（两个服务没开 springdoc） | 同 | 一致 |
| `/scalar` | 200 | 200 | HTML 只差基地址；mini 上 `/scalar.html` 同样是 404，README 原来的路径早已过时 |
| `/nowhere` | 404，Spring 默认 JSON | 404，`application/problem+json`，`GW-0404` | Issue 要的变化 |
| `/actuator/health` | 200 | 200 | 一致 |

唯一的响应头差别是状态行：Netty 写 `HTTP/1.1 200 OK`，Tomcat 写 `HTTP/1.1 200 `（不带原因短语），客户端不关心。

门户 e2e（`pnpm test:e2e`，门户指向本机网关）：`--workers=1` 串行 14 个用例全过；指向 mini 旧网关同样 14 个全过。默认并行跑时两边都会有 3 到 4 个「点卡片后 5 秒内没跳转」的超时失败，对 mini 更多，是 dev server 并行编译加 tailscale 时延的抖动，与网关无关。

第 7 条补充：虚拟线程下 Nacos 发现正常（实例列表、订阅都拿到），OTel agent 向 mini 的 collector 导出正常，日志里没有导出失败。

本机对照时踩到的两个环境坑，与网关无关但值得记：macOS 的 JVM 会自动把系统代理（Shadowrocket 的 127.0.0.1:7890）装进 `http.proxyHost`，而系统例外列表里的 CIDR（`100.64.0.0/10`、`192.168.0.0/16`）Java 的 `nonProxyHosts` 不认，于是网关的 RestClient 和 Nacos 的 gRPC 客户端（grpc-java 也看 `ProxySelector`）都被送进了代理，拿到的是代理回的空 503 加 `Proxy-Connection: close`；本机跑时要带 `JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.*"`（https、socks 同样设）。另外 mini 的 Nacos 开着鉴权，本机起网关要带 `.env.common` 里的 `NACOS_USERNAME` / `NACOS_PASSWORD`。

## 13. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 网关是 servlet 应用，`SecurityServletAutoConfiguration` 会生效；starter-web 已接入，鉴权的拒绝响应直接复用 ProblemDetail，错误码前缀 `GW` | PAP-65 |
| 查会话是同步 Redis 调用，在虚拟线程上阻塞无妨；不要为它引入响应式客户端 | PAP-65 |
| 路径规则看到的是剥前缀之前的路径（安全 starter 设计第 17 节已写），WebMVC 版不改变这一点 | PAP-65 |
| 没有新增环境变量；CD 推送后会重建网关镜像并重启，compose 不用改 | PAP-66 |
| 读超时是总时长：流式路由要单独的 `RestClient`（不设读超时）或改用按空闲计时的客户端，加 SSE 前先解决 | 引入 SSE 的 Issue |

## 14. 要同步改的文档和 Issue

- `patra-api/patra-gateway-boot/README.md`：按实际行为重写（现在连包路径都还是 `com/patra/gateway`）。
- `docs/patra/specs/2026-10-05-security-starter-design.md` 第 14 节：全局处理器对代理失败的实测。
- `linqibin-commons/linqibin-spring-boot-starter-web/README.md`：Spring MVC 自带的 404 也带错误码。
- `patra-infra/cd/module-graph.json`：随依赖变化重新生成。
- Linear PAP-69：To-Do 和 AC 随实测勾掉；PAP-65 描述里「网关已切到 WebMVC 版」到时成为事实，不用改。
