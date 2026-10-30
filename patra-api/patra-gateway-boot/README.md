# patra-gateway-boot

Patra 的 API 网关：所有外部请求的统一入口，按路径前缀路由到各微服务，并负责**鉴权**：按会话令牌调 identity 的内部接口查会话（identity 顺手续期），按路径规则放行或拒绝，通过后把网关签的身份断言写进转发请求。Spring Cloud Gateway 的 **WebMVC 版**（servlet 栈）+ Spring Security。

设计：[gateway 切换到 WebMVC 版（PAP-69）](../../docs/patra/specs/2026-10-09-gateway-webmvc-design.md)、[gateway 鉴权（PAP-65）](../../docs/patra/specs/2026-10-09-gateway-auth-design.md)、[查会话改调 identity（PAP-106）](../../docs/patra/specs/2026-10-23-session-introspection-design.md)。

## 职责

- **路由**：`/patra-catalog/**`、`/patra-registry/**`、`/patra-identity/**`、`/patra-interaction/**` 剥掉第一段后转给对应服务，经 Nacos 发现、Spring Cloud LoadBalancer 选实例。
- **鉴权**：`Authorization: Bearer <会话令牌>` → 调 identity 的 `POST /_internal/sessions/introspect`（`lb://patra-identity`，令牌放请求体，不缓存）→ 建认证对象；不是「恰好一个非空 Bearer 头 + identity 说 active」的一律按匿名，是否放行由下面的路径规则决定。令牌格式只由 identity 判断，网关不连 Redis。
- **入站转发头**：只有受信代理（门户）发来的 `X-Forwarded-For` 被采用，Tomcat `RemoteIpValve` 据此改写客户端地址；其余一切 `Forwarded` / `X-Forwarded-*`（包括非受信来源的 `X-Forwarded-For`）在 servlet 层直接剥掉、不解释（`ForwardedHeaderFilter` 的 `removeOnly`），路径规则、路由和下面的出站头都按真实请求算。
- **出站请求头**：剥掉外部自带的 `Authorization`；已登录时写入网关现签的 60 秒身份断言（`Authorization: Bearer <JWT>`），匿名不写；再由框架追加网关自己的 `X-Forwarded-*` / `Forwarded`，剥逐跳头。下游收到的 `Authorization` 只可能是网关签的断言，或者没有。
- **透传**：下游的状态码、响应头、响应体原样回客户端，包括 4xx / 5xx / 3xx；不跟随重定向，不替任何一方谈压缩；代理链上关掉了 Spring Security 默认补的响应头（`Cache-Control: no-store`、`X-Content-Type-Options` 等），下游没给的头网关也不补。
- **文档聚合**：`/scalar` 聚合五个服务的 OpenAPI 文档。
- **可观测性**：OTel Agent + Micrometer，actuator 暴露 `health` / `info` / `metrics`。

## 路径规则

规则看到的是剥前缀**之前**的路径，写在 `GatewaySecurityConfiguration` 里，两条过滤器链：

| 顺序 | 路径 | 结果 |
|---|---|---|
| 第一条链 | `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` | 任何人 403 `GW-0403`；不读令牌、不查会话。服务之间的直连调用不过网关，不受影响 |
| 第二条链 | `/patra-identity/auth/register`、`/auth/login`、`/auth/logout`、`/v3/api-docs`、`/v3/api-docs/**` | 放行（登出公开是为了幂等：会话已失效也要能到达 identity） |
| 第二条链 | `/patra-identity/**` 其余 | 需登录；匿名 401 `GW-0401` |
| 第二条链 | 其他一切（catalog、registry、interaction、网关自己的 `/actuator/**` 与 `/scalar`） | 放行 |

规则只看路径不看方法。网关自己的 `/actuator/health` 没有服务前缀，compose 健康检查照常。`//`、`%2F`、`;`、`..` 这类路径由 Spring Security 的防火墙以 400 拒绝（框架默认，不是 ProblemDetail）。

## 模块结构

```
patra-gateway-boot/
├── src/main/java/dev/linqibin/patra/gateway/
│   ├── PatraGatewayApplication.java              # 启动类
│   ├── config/GatewayConfiguration.java          # HTTP 客户端不谈压缩（Reactor Netty 关 compress）
│   ├── config/IdentityClientConfiguration.java   # identity 查会话接口的 HTTP Interface 代理（lb://patra-identity）
│   ├── error/                                    # 代理失败 → 网关自己的应用异常（503 / 504，固定文案）
│   └── security/
│       ├── GatewaySecurityConfiguration.java     # 签名器（启动自检）、两条过滤器链、入站转发头剥除、出站头过滤器、错误映射
│       ├── GatewayIdentityAssertionProperties.java   # patra.gateway.identity-assertion.private-key（只按字符串绑定）
│       ├── SessionTokenAuthenticationConverter.java  # 取令牌、调 identity 查会话、建认证；503 / 500 分流
│       ├── SessionLookupFailedException.java     # 查会话的非暂时失败 → 500
│       ├── GatewaySecurityErrorMappingContributor.java # starter 的映射表加一行
│       └── IdentityAssertionRequestHeadersFilter.java  # 出站：剥外部 Authorization，已登录写入断言
├── src/main/resources/
│   └── application.yml                           # 路由、HTTP 客户端、超时、虚拟线程、identity 分组超时、错误前缀；随环境变的值在 Nacos
└── src/integrationTest/java/dev/linqibin/patra/gateway/
    ├── PatraGatewayApplicationIT.java            # servlet 启动、Reactor Netty 客户端、虚拟线程、两条链、签名器
    ├── GatewayRoutingIT.java                     # 转发行为（WireMock 顶替下游）
    ├── GatewayFailureIT.java                     # 代理失败的状态码；读超时是空闲超时（长流、慢速大文件下载）
    ├── GatewayStalledDownstreamIT.java           # 下游停住：响应体中途停住被切断；不读请求体时写超时 504
    ├── GatewayAuthenticationIT.java              # 认证、规则、断言、出站头
    ├── GatewayTrustedProxyIT.java                # 受信代理的 X-Forwarded-For 被采用
    ├── GatewayUntrustedProxyIT.java              # 非受信来源的被剥掉；登录设备接口需登录
    ├── GatewayBlockedPathsIT.java                # 拒绝名单
    └── GatewayIdentityUnavailableIT.java         # identity 查不了会话（它回 503、连不上）→ 503
```

## 会话与断言

- 查会话的契约在 `patra-identity-api`（`SessionEndpoint`）：网关不配有效期、不做续期，续期与过期规则都在 identity（见 identity README 第 9 节）。
- 断言格式、签名器、验签器在 `patra-spring-boot-starter-security`：`ES256`、`typ: patra-identity+jwt`、`iss: patra-gateway`、`aud: patra`、60 秒有效期，每个请求现签、不缓存。
- 私钥按字符串绑定，建签名器时才解析；启动时用配置的公钥自签自验一次，公私钥不成对或 `kid` 不一致直接启动失败，错误信息只带 `kid`。
- 网关也要配 `patra.security.identity-assertion.public-keys`（自己那把公钥）：安全 starter 的验签器和启动自检用它，不要为了绕过它排除 starter 的自动配置。

## 受信代理与入站转发头

两层，先后执行（PAP-99，设计 `docs/patra/specs/2026-10-22-identity-login-devices-design.md` 第 6 节）：

1. **Tomcat `RemoteIpValve`**（`server.forward-headers-strategy: native`）：连接地址落在 `server.tomcat.remoteip.internal-proxies` 里时，取 `X-Forwarded-For` 最右边那个不是代理的值作为客户端地址并消费掉该头；别的连接什么都不做。默认值是空串：谁都不信。**不能留 Boot 的默认值**——它含 `100.64.0.0/10`、`192.168.0.0/16`，tailnet 上的任何机器、OrbStack 端口转发进来的连接（来源 `192.168.97.1`）都会被当成代理。值来自环境变量 `PATRA_GATEWAY_TRUSTED_PROXIES`：mini 在 `docker-compose.apps.yaml` 的 gateway 服务块里设成门户容器的固定地址 `192.168.97.250/32`（同一个文件里 portal 的 `ipv4_address`，两处一起改）。valve 对受信连接还会采用 `X-Forwarded-Proto` / `-Host` / `-Port`，门户按契约只发 `X-Forwarded-For` 与 `User-Agent`。
2. **`ForwardedHeaderFilter`（`removeOnly`）**：剥掉剩下的一切 `Forwarded` / `X-Forwarded-*`，不改客户端地址。springdoc 的 Scalar starter 会给应用无条件注册一个「应用」入站转发头的 `ForwardedHeaderFilter`：客户端带 `X-Forwarded-Prefix` 就能改写网关看到的路径，让 `StripPrefix` 剥错段。网关在 `GatewaySecurityConfiguration.forwardedHeaderFilter()` 声明同类型的 Bean 让它让位，并开 `removeOnly`：只删不解释，最高优先级，先于 Security 和路由。

出站：Gateway 按处理后的客户端地址写 `X-Forwarded-For` 与 `Forwarded: for=`，identity 的 `getRemoteAddr()` 就是它；`User-Agent` 原样转发。`spring.cloud.gateway.server.webmvc.trusted-proxies: ".*"` 只管出站头能不能写，不要收窄（收窄后非受信来源的请求出站一个转发头都没有，下游的 springdoc 地址还原就坏了）。valve 在 Tomcat 启动时读配置，改了要重启网关。

## HTTP 客户端、超时与线程

| 项 | 值 | 说明 |
|---|---|---|
| 客户端 | Reactor Netty | `spring.http.clients.imperative.factory: REACTOR`，显式指定 |
| 连接超时 | 5 秒 | `spring.http.clients.connect-timeout` |
| 读超时 | 60 秒 | `spring.http.clients.read-timeout`，空闲超时：连续 60 秒收不到下游任何字节才断，不限总时长；大文件慢速下载、长流不受影响 |
| 写超时 | 60 秒 | 取同一个 `read-timeout`，`GatewayConfiguration` 给每个请求挂 Netty 的 `WriteTimeoutHandler`：下游不读请求体、单次写停住超过 60 秒就断开 |
| 重定向 | 不跟随 | `spring.http.clients.redirects: dont-follow` |
| 压缩 | 关 | `GatewayConfiguration` 关掉 Boot 给 Reactor Netty 默认开的压缩 |
| 线程 | 虚拟线程 | `spring.threads.virtual.enabled: true` |
| 查会话（identity） | 连接 1 秒、读 2 秒 | `patra.http-client.http-interface.groups.identity`，独立的 Apache HttpClient（`RestClientFactory` 在 customizer 之后设，不被 Gateway 的 Reactor 工厂盖掉）；identity 卡住时带令牌的请求几秒内变成 503 |

## 错误

网关自身产生的错误都是 `application/problem+json`，错误码前缀 `GW`。下游自己的错误原样透传。

| 场景 | 状态码 | 错误码 | detail |
|---|---|---|---|
| 匿名（含无效、过期、不存在的令牌）访问需登录路径 | 401 | `GW-0401` | Authentication required（带 `WWW-Authenticate: Bearer`） |
| 任何人访问拒绝名单 | 403 | `GW-0403` | Access denied |
| 带会话令牌且 identity 查不了会话（它回 503 + `DEP_UNAVAILABLE`、连不上、超时、没有实例；任何路由） | 503 | `GW-0503` | Service Unavailable |
| 带会话令牌且查会话遇到其他失败（identity 回其他错误、响应解析不了） | 500 | `GW-0500` | Internal Server Error |
| 未匹配任何路由 | 404 | `GW-0404` | |
| `lb://` 找不到实例；连接被拒、连接超时、域名解析失败 | 503 | `GW-0503` | 下游服务暂时不可用 |
| 响应头到达前读超时（下游 60 秒没回） | 504 | `GW-0504` | 下游服务响应超时 |
| 下游不读请求体超过 60 秒（写超时） | 504 | `GW-0504` | 下游服务响应超时 |
| 响应头已到、下游中途停住超过读超时，响应还没提交 | 500 | `GW-0500` | |
| 同上，响应已提交 | 连接中止（没有分块终止块，客户端报传输不完整） | 无 | |
| 防火墙拒绝的畸形路径 | 400 | 无 | 框架默认 |

identity 自己的 `IDN-0401` 仍会出现：带有效断言但用户已封禁的 60 秒窗口里由 identity 回 401。

## 配置

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `PATRA_NACOS_ADDR` | Nacos 地址 | `${PATRA_INFRA_HOST:127.0.0.1}:8848`，由 Nacos starter 注入 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 应用账号 `patra-app` | 无，来自 `~/.patra/secrets/nacos.env`（容器经 `env_file`，本机由 Nacos starter 读） |
| `PATRA_ADVERTISE_IP` | 向 Nacos 注册的地址 | 空，自动探测；IDE 设为本机 tailscale IP |
| `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY` | 签断言的私钥，含私钥的 EC P-256 JWK JSON，带 `kid` | 无，来自 `~/.patra/secrets/gateway.env`；缺了启动失败 |
| `PATRA_LOG_DIR` | 日志目录 | `logs` |
| `PATRA_GATEWAY_TRUSTED_PROXIES` | 受信代理地址（CIDR，逗号分隔），只有它们发来的 `X-Forwarded-For` 被采用（`server.tomcat.remoteip.internal-proxies`） | 空（谁都不信）；mini 的 compose 里是 `192.168.97.250/32` |

端口 9528。

随环境变的值在 Nacos：身份断言公钥在 `patra-common.yaml`，私钥引用在 `patra-gateway.yaml`（源文件在 `patra-infra/nacos/dev/`）。其余默认值在 `application.yml`。

- 容器：私钥由 compose 从 `~/.patra/secrets/` 加载。
- 本机：Nacos starter 读同一批文件，不用设环境变量。
- 要看路由和负载均衡的 DEBUG 日志：在 Nacos 的 `patra-gateway.yaml` 里临时加 `logging.level.org.springframework.cloud.gateway: DEBUG`，重启网关生效，看完删掉。
- 密钥的生成、复制和换密钥的顺序，见 `patra-infra/docker/README.md`「密钥」。

## 测试

```bash
./gradlew :patra-api:patra-gateway-boot:test              # 单元测试
./gradlew :patra-api:patra-gateway-boot:integrationTest   # 集成测试（WireMock 顶替下游与 identity 的查会话，不连 Nacos）
```

集成测试不用 Docker。`test` profile 只关掉 Nacos 的发现与注册；私钥由 `GatewayITSigningKeyInitializer` 写进配置，公钥由安全 starter 测试支持的环境后处理器自动注入；会话由 `GatewayITSessions` 给 WireMock 顶替的 identity 登记（`issue` / `revoke`，没登记的令牌回 inactive）。

## 技术栈

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.0.8 |
| Spring Security | 7.0.7 |
| Spring Cloud | 2025.1.3（gateway-server-webmvc 5.0.3） |
| Nacos Discovery | spring-cloud-alibaba 2025.1.0.0 |
| springdoc（webmvc Scalar） | 3.0.1 |
| Java | 25 |
