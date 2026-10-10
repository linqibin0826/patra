# patra-gateway-boot

Patra 的 API 网关：所有外部请求的统一入口，按路径前缀路由到各微服务，并负责**鉴权**：按会话令牌查 Redis 会话并续期，按路径规则放行或拒绝，通过后把网关签的身份断言写进转发请求。Spring Cloud Gateway 的 **WebMVC 版**（servlet 栈）+ Spring Security。

设计：[gateway 切换到 WebMVC 版（PAP-69）](../../docs/patra/specs/2026-10-09-gateway-webmvc-design.md)、[gateway 鉴权（PAP-65）](../../docs/patra/specs/2026-10-09-gateway-auth-design.md)。

## 职责

- **路由**：`/patra-catalog/**`、`/patra-registry/**`、`/patra-ingest/**`、`/patra-identity/**` 剥掉第一段后转给对应服务，经 Nacos 发现、Spring Cloud LoadBalancer 选实例。
- **鉴权**：`Authorization: Bearer <会话令牌>` → 查 Redis 会话（顺手续期）→ 建认证对象；令牌不是「恰好一个 Bearer 头 + 会话令牌格式 + Redis 里有」的一律按匿名，是否放行由下面的路径规则决定。
- **入站转发头**：外部自带的 `Forwarded` / `X-Forwarded-*` 在 servlet 层直接剥掉、不解释（`ForwardedHeaderFilter` 的 `removeOnly`），路径规则、路由和下面的出站头都按真实请求算。
- **出站请求头**：剥掉外部自带的 `Authorization`；已登录时写入网关现签的 60 秒身份断言（`Authorization: Bearer <JWT>`），匿名不写；再由框架追加网关自己的 `X-Forwarded-*` / `Forwarded`，剥逐跳头。下游收到的 `Authorization` 只可能是网关签的断言，或者没有。
- **透传**：下游的状态码、响应头、响应体原样回客户端，包括 4xx / 5xx / 3xx；不跟随重定向，不替任何一方谈压缩；代理链上关掉了 Spring Security 默认补的响应头（`Cache-Control: no-store`、`X-Content-Type-Options` 等），下游没给的头网关也不补。
- **文档聚合**：`/scalar` 聚合四个服务的 OpenAPI 文档。
- **可观测性**：OTel Agent + Micrometer，actuator 暴露 `health` / `info` / `metrics`。

## 路径规则

规则看到的是剥前缀**之前**的路径，写在 `GatewaySecurityConfiguration` 里，两条过滤器链：

| 顺序 | 路径 | 结果 |
|---|---|---|
| 第一条链 | `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` | 任何人 403 `GW-0403`；不读令牌、不查 Redis。服务之间的直连调用不过网关，不受影响 |
| 第二条链 | `/patra-identity/auth/register`、`/auth/login`、`/auth/logout`、`/v3/api-docs`、`/v3/api-docs/**` | 放行（登出公开是为了幂等：会话已失效也要能到达 identity） |
| 第二条链 | `/patra-identity/**` 其余 | 需登录；匿名 401 `GW-0401` |
| 第二条链 | 其他一切（catalog、registry、ingest、网关自己的 `/actuator/**` 与 `/scalar`） | 放行 |

规则只看路径不看方法。网关自己的 `/actuator/health` 没有服务前缀，compose 健康检查照常。`//`、`%2F`、`;`、`..` 这类路径由 Spring Security 的防火墙以 400 拒绝（框架默认，不是 ProblemDetail）。

## 模块结构

```
patra-gateway-boot/
├── src/main/java/dev/linqibin/patra/gateway/
│   ├── PatraGatewayApplication.java              # 启动类
│   ├── config/GatewayConfiguration.java          # HTTP 客户端不谈压缩
│   ├── error/                                    # 代理失败 → 网关自己的应用异常（503 / 504，固定文案）
│   └── security/
│       ├── GatewaySecurityConfiguration.java     # 会话存储、签名器（启动自检）、两条过滤器链、入站转发头剥除、出站头过滤器、错误映射
│       ├── GatewayIdentityAssertionProperties.java   # patra.gateway.identity-assertion.private-key（只按字符串绑定）
│       ├── SessionTokenAuthenticationConverter.java  # 取令牌、查会话、建认证；503 / 500 分流
│       ├── SessionLookupFailedException.java     # 查会话的非暂时失败 → 500
│       ├── GatewaySecurityErrorMappingContributor.java # starter 的映射表加一行
│       └── IdentityAssertionRequestHeadersFilter.java  # 出站：剥外部 Authorization，已登录写入断言
├── src/main/resources/
│   ├── application.yml                           # 路由、HTTP 客户端、超时、虚拟线程、Redis 超时、错误前缀
│   ├── application-dev.yml                       # dev：Redis 地址、两把密钥的环境变量、DEBUG 日志
│   └── application-container.yml                 # 容器：同一组环境变量，无默认值
└── src/integrationTest/java/dev/linqibin/patra/gateway/
    ├── PatraGatewayApplicationIT.java            # servlet 启动、JDK 客户端、虚拟线程、两条链、签名器
    ├── GatewayRoutingIT.java                     # 转发行为（WireMock 顶替下游）
    ├── GatewayFailureIT.java                     # 代理失败的状态码
    ├── GatewayAuthenticationIT.java              # 认证、规则、断言、出站头
    ├── GatewayBlockedPathsIT.java                # 拒绝名单
    └── GatewayRedisUnavailableIT.java            # Redis 不可用 → 503
```

## 会话与断言

- 会话契约在 `patra-identity-session`（`RedisSessionStore`、`SessionToken`）：网关只查和续期，不配有效期；续期按会话里的 `idle_timeout_ms` 和 `expires_at`，距上次写入满 60 秒才写回。
- 断言格式、签名器、验签器在 `patra-spring-boot-starter-security`：`ES256`、`typ: patra-identity+jwt`、`iss: patra-gateway`、`aud: patra`、60 秒有效期，每个请求现签、不缓存。
- 私钥按字符串绑定，建签名器时才解析；启动时用配置的公钥自签自验一次，公私钥不成对或 `kid` 不一致直接启动失败，错误信息只带 `kid`。
- 网关也要配 `patra.security.identity-assertion.public-keys`（自己那把公钥）：安全 starter 的验签器和启动自检用它，不要为了绕过它排除 starter 的自动配置。

## 入站转发头为什么在 servlet 层剥

springdoc 的 Scalar starter 会给应用无条件注册一个「应用」入站转发头的 `ForwardedHeaderFilter`：客户端带 `X-Forwarded-Prefix` 就能改写网关看到的路径，让 `StripPrefix` 剥错段。网关在 `GatewaySecurityConfiguration.forwardedHeaderFilter()` 声明同类型的 Bean 让它让位，并开 `removeOnly`：只删不解释，最高优先级，先于 Security 和路由。

将来网关前面真放了反向代理：去掉 `removeOnly`，把 `spring.cloud.gateway.server.webmvc.trusted-proxies` 从 `.*` 改成代理的地址。

## HTTP 客户端、超时与线程

| 项 | 值 | 说明 |
|---|---|---|
| 客户端 | JDK HttpClient | `spring.http.clients.imperative.factory: jdk`，显式指定 |
| 连接超时 | 5 秒 | `spring.http.clients.connect-timeout` |
| 读超时 | 60 秒 | `spring.http.clients.read-timeout`，从请求发出起算的总时长，流式响应同样受限 |
| 重定向 | 不跟随 | `spring.http.clients.redirects: dont-follow` |
| 压缩 | 关 | `GatewayConfiguration` 关掉 JDK 客户端的透明压缩 |
| 线程 | 虚拟线程 | `spring.threads.virtual.enabled: true` |
| Redis | 连接 5 秒、命令 2 秒 | `spring.data.redis.connect-timeout` / `timeout`。命令超时是单条命令的上限，建连耗时另计；Redis 断线期间 Lettuce 把命令排队等重连，等满 2 秒的请求变成 503 |

## 错误

网关自身产生的错误都是 `application/problem+json`，错误码前缀 `GW`。下游自己的错误原样透传。

| 场景 | 状态码 | 错误码 | detail |
|---|---|---|---|
| 匿名（含无效、过期、不存在的令牌）访问需登录路径 | 401 | `GW-0401` | Authentication required（带 `WWW-Authenticate: Bearer`） |
| 任何人访问拒绝名单 | 403 | `GW-0403` | Access denied |
| 带会话令牌且 Redis 暂时不可用（任何路由） | 503 | `GW-0503` | Service Unavailable |
| 带会话令牌且查会话遇到非暂时失败（`NOAUTH`、键被写坏） | 500 | `GW-0500` | Internal Server Error |
| 未匹配任何路由 | 404 | `GW-0404` | |
| `lb://` 找不到实例；连接被拒、连接超时、域名解析失败 | 503 | `GW-0503` | 下游服务暂时不可用 |
| 响应头到达前读超时 | 504 | `GW-0504` | 下游服务响应超时 |
| 响应头已到、响应体读超时，响应还没提交 | 500 | `GW-0500` | |
| 同上，响应已提交 | 200，半截响应体 | 无 | |
| 防火墙拒绝的畸形路径 | 400 | 无 | 框架默认 |

identity 自己的 `IDN-0401` 仍会出现：带有效断言但用户已封禁的 60 秒窗口里由 identity 回 401。

## 配置

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profile | `dev` |
| `NACOS_HOST` / `NACOS_PORT` / `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos | 跟随 `PATRA_INFRA_HOST`、`8848`、`nacos` / `nacos` |
| `TAILSCALE_IP` | dev 下向 Nacos 注册的 IP | 空 |
| `REDIS_HOST` / `REDIS_PORT` | 会话所在的 Redis（container） | 无，来自 `.env.common`；dev 固定为 `${PATRA_INFRA_HOST:127.0.0.1}` / `16379` |
| `REDIS_PASSWORD` | Redis 密码，用户 `default` | 无，来自 `~/.patra/secrets/redis.env` |
| `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY` | 签断言的私钥，含私钥的 EC P-256 JWK JSON，带 `kid` | 无，来自 `~/.patra/secrets/gateway.env`；缺了启动失败 |
| `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` | 网关自己那把公钥，JWK Set JSON | container 来自 `.env.common`；dev 直接写在 `application-dev.yml` |
| `PATRA_LOG_DIR` | 日志目录 | `logs` |

端口 9528。

- 容器：`REDIS_PASSWORD` 和私钥由 compose 从 `~/.patra/secrets/` 加载。
- 本地以 dev profile 启动：`application-dev.yml` 用 `spring.config.import` 读同一批文件，不用设环境变量；缺文件时启动失败并报出路径。
- 密钥的生成、复制和换密钥的顺序，见 `patra-infra/docker/README.md`「密钥」。

## 测试

```bash
./gradlew :patra-api:patra-gateway-boot:test              # 单元测试
./gradlew :patra-api:patra-gateway-boot:integrationTest   # 集成测试（WireMock 顶替下游，Redis 用 Testcontainers，不连 Nacos）
```

集成测试需要本机 Docker。`test` profile 只关掉 Nacos 的发现与注册；私钥由 `GatewayITSigningKeyInitializer` 写进配置，公钥由安全 starter 测试支持的环境后处理器自动注入；会话由测试直接写进 Redis 容器。

## 技术栈

| 组件 | 版本 |
|---|---|
| Spring Boot | 4.0.8 |
| Spring Security | 7.0.7 |
| Spring Cloud | 2025.1.3（gateway-server-webmvc 5.0.3） |
| Spring Data Redis / Lettuce | 4.0 / 6.8.2 |
| Nacos Discovery | spring-cloud-alibaba 2025.1.0.0 |
| springdoc（webmvc Scalar） | 3.0.1 |
| Java | 25 |
