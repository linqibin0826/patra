# gateway 鉴权工程设计（PAP-65）

> **Issue**：[PAP-65](https://linear.app/papertrace/issue/PAP-65)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)（决策 B、C、D、I，边界 F）
> **前置设计**：[安全 starter（PAP-62 / PAP-70）](2026-10-05-security-starter-design.md) 第 17 节、[identity 会话（PAP-64）](2026-10-09-identity-session-design.md) 第 16 节、[gateway 切换到 WebMVC 版（PAP-69）](2026-10-09-gateway-webmvc-design.md) 第 13 节
> **后续 Issue**：[PAP-66](https://linear.app/papertrace/issue/PAP-66) 注入密钥与 Redis 密码；[PAP-67](https://linear.app/papertrace/issue/PAP-67) 门户按本设计的 401 处理 Cookie
> **日期**：2026-10-09
> **状态**：设计中，待评审

## 1. 要解决的问题

v0.8 的身份体系里，网关是唯一的鉴权入口：浏览器只拿着不透明的会话令牌，下游只认网关签的身份断言。identity（PAP-63 / PAP-64）已经能签发会话并验断言，安全 starter（PAP-62 / PAP-70）已经提供签名器、认证对象和错误输出，网关也已经切到 servlet 栈（PAP-69）。缺的是网关自己这一段：按令牌查会话并续期，按路径规则决定匿名能不能过，通过后把断言写进转发的请求。

同时堵上一个口子：网关现在按前缀整段转发，外部请求可以经网关到达各服务的 `/_internal/**`、`/admin/**` 和 actuator。服务之间经 Nacos 直连、不过网关，所以拦掉这条路不影响内部调用。

**完成标准**：PAP-65 的 Acceptance Criteria。

## 2. 范围

做：

- 网关接入安全 starter 和会话模块，写自己的两条 Spring Security 过滤器链：拒绝名单、会话认证加路径规则。
- 出站请求头：剥外部 `Authorization`，已登录时写入现签的断言；剥外部自带的 `X-Forwarded-*` 和 `Forwarded`。
- 私钥的配置项、签名器 Bean、启动自检。
- 新增 `/patra-identity/**` 路由和 Scalar 聚合源。
- 会话模块 `TransientRedisFailures` 补一条连接层失败的判定。
- 单元测试与集成测试覆盖 AC 的每一行；本地实测；README 重写。

不做：

- catalog、registry、ingest 的任何路径级限制（边界 F：运维路由维持现状）。
- 后台账号、角色、方法级权限（决策 D：本版没有后台账号，`/admin/**` 一律拒绝外部访问）。
- 有效期策略、续期频率（都在会话模块和 identity 里，网关不配）。
- 防火墙拒绝（`//`、`%2F` 等）的响应改成 ProblemDetail：框架默认的 400，门户不会发这种请求。
- 部署：环境变量、Redis 密码、compose 条目归 PAP-66；Cookie 归 PAP-67。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 不透明令牌 + Redis 会话；令牌带前缀 `patra_user_`；存储契约在 `patra-identity-session` | 决策 B，PAP-64 设计第 6、7 节 |
| 网关查会话、续期、判路由规则，通过后签 60 秒断言放进 `Authorization: Bearer`，先剥外部的 `Authorization`；匿名不带断言；下游用公钥验签 | 决策 C |
| 无效或过期的令牌按匿名处理，是否放行由路由规则决定；Redis 不可用返回 503 | 决策 C，安全 starter 设计第 17 节 |
| 会话带账号类型；`/admin/**` 本版拒绝外部访问 | 决策 D |
| 认证框架用 Spring Security，网关写自己的过滤器链，复用 starter 的 `StatelessSecurityDefaults.apply`、`CurrentUserAuthentication`、`IdentityAssertionSigner`、`SecurityProblemWriter` | 决策 I，安全 starter 设计第 17 节 |
| 私钥只在网关，由 secret 注入；网关同样要配自己那把公钥 `patra.security.identity-assertion.public-keys`，不排除 `SecurityServletAutoConfiguration` | 决策 G，安全 starter 设计第 8.3、17 节 |
| 每个请求：取 `Bearer` → `SessionToken.parse` 失败按匿名 → `findAndTouch` 为空按匿名 → `StoredSession.toCurrentUser()` 建认证对象；公开路由上照样翻译令牌；续期间隔 60 秒是模块常量 | PAP-64 设计第 16 节 |
| `SessionStoreUnavailableException` 包成 `AuthenticationServiceException` 输出 503；暂时失败的判定复用 `TransientRedisFailures` | PAP-64 设计第 16 节 |
| 路径规则看到的是剥前缀之前的路径；各服务的 actuator 一并拦掉；匿名和已登录撞上拒绝规则要得到同一个结果 | 安全 starter 设计第 17 节 |
| 网关是 servlet 应用，查会话是同步 Redis 调用，跑在虚拟线程上 | 决策 H，PAP-69 设计第 13 节 |
| 登出在网关上公开，identity 用 `current()` 判断有没有当前用户 | PAP-64 设计第 10 节 |

本设计新定的三件事（2026-10-09 与用户商定）：

| 决定 | 内容 | 否决的选项 |
|---|---|---|
| 拒绝名单的响应 | 统一 403 `GW-0403`，detail「Access denied」 | 404：这三组路径在 README 和公开仓库里本就是公开信息，藏不住；404 让排查时分不清是拦截还是路径错，且要绕过框架的 401 / 403 机制单独写出 |
| identity 路由的默认规则 | `/patra-identity/**` 默认需登录，只有注册、登录、登出和 `/v3/api-docs` 公开 | 只给 `/auth/me` 加规则、其余放行：identity 以后的接口几乎都要登录，默认关上，漏配规则时得到 401 而不是裸奔 |
| 外部自带的转发头 | 在网关剥掉 `X-Forwarded-*` 和 `Forwarded`，下游只看到网关写的值 | 保持 `trusted-proxies: ".*"` 下的框架行为（保留客户端的值再追加）：网关前面没有任何反向代理，这些头从外面进来一律不合法 |

版本：Spring Boot 4.0.8、Spring Security 7.0.x、Spring Cloud Gateway Server WebMVC 5.0.3、Spring Data Redis 4.0.x、Lettuce 6.8.2。下面用到的框架行为都对着这几个版本的源码核过：

- `ProxyExchangeHandlerFunction` 用 `ObjectProvider.orderedStream()` 收集容器里所有 `RequestHttpHeadersFilter`，对每个请求依次调用，把最后一个的返回值作为出站请求头；传给第一个过滤器的是 `ServerRequest.headers().asHttpHeaders()`。
- `XForwardedRequestHeadersFilter` 和 `ForwardedRequestHeadersFilter` 的 order 都是 0；来源可信时保留已有的值再追加，不可信时整个剥掉、也不追加；没有「剥掉客户端的、只写自己的」这一档。`RemoveHopByHopRequestHeadersFilter` 的 order 是最大值减一。
- `AuthenticationFilter`：转换器返回 `null` 时直接继续过滤器链、不建认证；转换器抛 `AuthenticationException` 时调失败处理器、不再继续；成功时把认证放进上下文和 `RequestAttributeSecurityContextRepository`，再交给成功处理器（它的默认实现调完三参版本后继续过滤器链）。
- Spring Data Redis 的 `LettuceExceptionConverter`：`RedisConnectionException` → `RedisConnectionFailureException`；`RedisCommandTimeoutException` → `QueryTimeoutException`；`RedisCommandExecutionException` 及其子类 → `RedisSystemException("Error in execution")`；其余 `RedisException` → `RedisSystemException("Redis exception")`。
- Lettuce 6.8.2 里直接继承 `RedisException` 的有 `RedisCommandExecutionException`（服务端的错误回复，`LOADING` / `READONLY` / `BUSY` / `NOSCRIPT` 是它的子类）、`RedisConnectionException`、`RedisCommandTimeoutException`、`RedisCommandInterruptedException`。

## 4. 方案比较

| | 做法 | 取舍 |
|---|---|---|
| A（采用） | Spring Security 两条过滤器链管认证和路径规则；Gateway MVC 的 `RequestHttpHeadersFilter` Bean 管出站改头 | 符合决策 I；规则集中一处、对所有路由生效；401 / 403 / 503 的输出直接复用 starter |
| B | 全部用 Gateway MVC 的路由过滤器：每条路由在 YAML 里挂 `RemoveRequestHeader=Authorization`，再写自定义 `FilterSupplier` 查会话、签断言 | 绕开 Spring Security，和决策 I 冲突；规则散在每条路由里，新加路由容易漏；错误输出要自己写一套 |
| C | 一个 servlet `Filter` 用 `HttpServletRequestWrapper` 改请求头，认证仍交给 Spring Security | 改头藏在 wrapper 里，依赖 Gateway MVC 从 wrapper 读头这一实现细节；不如 A 直接 |

拒绝名单的实现也比较过：单独一条排在前面的过滤器链（采用）、在主链里 `denyAll` 加自定义处理器、在路由谓词里排除这些路径让它们变成 404。单独一条链里没有认证过滤器，所有人都是匿名、都走同一个出口，不查 Redis；另两种要么要在处理器里判断路径，要么把规则搬进路由层。

## 5. 模块与组件

都在 `patra-gateway-boot`，新包 `dev.linqibin.patra.gateway.security`。

| 组件 | 职责 |
|---|---|
| `GatewaySecurityConfiguration` | 两条 `SecurityFilterChain`、`RedisSessionStore`、`IdentityAssertionSigner`（含启动自检）、错误码映射 Bean、两个出站头过滤器 Bean |
| `SessionTokenAuthenticationConverter` | 实现 `AuthenticationConverter`：取令牌、查会话、建 `CurrentUserAuthentication`；暂时失败和缺陷分别包成两种异常（第 7 节） |
| `SessionLookupFailedException` | 继承 `AuthenticationServiceException`：查会话时的非暂时失败（配置错、数据坏），输出 500 |
| `GatewaySecurityErrorMappingContributor` | 继承 starter 的 `SecurityErrorMappingContributor`，先判 `SessionLookupFailedException` → `INTERNAL_ERROR`，其余交给父类；声明成 Bean 后 starter 那个 `@ConditionalOnMissingBean` 的让位 |
| `IdentityAssertionRequestHeadersFilter` | `RequestHttpHeadersFilter`：剥外部 `Authorization`，已登录则写入现签的断言 |
| `ExternalForwardedHeadersFilter` | `RequestHttpHeadersFilter`：剥外部自带的 `X-Forwarded-*` 和 `Forwarded`，排在框架追加自己值的过滤器之前 |
| `GatewayIdentityAssertionProperties` | `patra.gateway.identity-assertion.private-key`，只按字符串绑定（第 9 节） |

依赖（`build.gradle.kts`）：

| 依赖 | 带来什么 |
|---|---|
| `:patra-starters:patra-spring-boot-starter-security` | Spring Security、`StatelessSecurityDefaults`、`SecurityProblemWriter`、`SecurityErrorMappingContributor`、`identityAssertionDecoder`、`IdentityAssertionSigner`、`CurrentUserAuthentication` |
| `:patra-api:patra-identity:patra-identity-session` | `RedisSessionStore`、`SessionToken`、`StoredSession`、`SessionStoreUnavailableException`；它用 `api` 带进 `spring-boot-starter-data-redis`，Boot 据此自动装配 `StringRedisTemplate` |
| `testImplementation(testFixtures(security starter))` | `TestSigningKey`、`TestIdentity`、自动注入测试公钥的环境后处理器 |

`Clock` 用 starter-core 自动配置提供的那个（网关已依赖 starter-core），不另建。

## 6. 过滤器链与路径规则

### 6.1 第一条链：拒绝名单（`@Order(1)`）

- `securityMatcher("/*/_internal/**", "/*/admin/**", "/*/actuator/**")`，`authorizeHttpRequests` 里 `anyRequest().denyAll()`。
- 先调 `StatelessSecurityDefaults.apply(http, problemWriter)`（无会话、关 CSRF、关登出），再把未登录入口点换掉：匿名撞上 `denyAll` 时框架走的是入口点，默认输出 401；这里让入口点改调 `problemWriter.handle(request, response, new AccessDeniedException(…))`，输出 403。链里没有认证过滤器，所有人在这条链上都是匿名，永远走这一个出口：不读令牌、不查 Redis，匿名和已登录得到同一个结果。
- 匹配用解码后的路径（`PathPatternRequestMatcher`），`%5Finternal` 和 `_internal` 同一个结果。通配符只放第一段：三组路径在各服务里都挂在根上，剥前缀后第二段就是下游的第一段。网关自己的 `/actuator/**` 没有服务前缀，不受影响，compose 健康检查照旧。
- 403 不带 `WWW-Authenticate`。

拒绝名单是配置类里的常量 `BLOCKED_PATHS`。

### 6.2 第二条链：其余全部（`@Order(2)`）

- `StatelessSecurityDefaults.apply(http, problemWriter)`。
- 在 `AnonymousAuthenticationFilter` 之前挂一个 `AuthenticationFilter`：认证管理器是直通实现（`authentication -> authentication`，转换器给出的已经是认证完成的对象，不经过 `ProviderManager`），转换器是第 7 节的 `SessionTokenAuthenticationConverter`；成功处理器换成空实现（默认会先回 302），失败处理器换成 `problemWriter`；过滤器不声明成 Bean（否则 Boot 会把它再注册成全局 servlet 过滤器）。写法和 starter 的默认链一致，只有转换器不同。starter 那个拒绝一切的 `AuthenticationManager` Bean 继续存在，没人调用它。
- 匿名认证保持框架默认（开）：框架靠匿名认证对象区分「没登录」和「登录了但不允许」。
- 规则按顺序：

  | 顺序 | 匹配 | 结果 |
  |---|---|---|
  | 1 | `dispatcherTypeMatchers(ERROR, FORWARD)` | 放行（和 starter 一样，否则错误页被 401 覆盖） |
  | 2 | `/patra-identity/auth/register`、`/patra-identity/auth/login`、`/patra-identity/auth/logout`、`/patra-identity/v3/api-docs`、`/patra-identity/v3/api-docs/**` | 放行 |
  | 3 | `/patra-identity/**` | `authenticated()` |
  | 4 | `anyRequest()` | 放行：catalog、registry、ingest 三条路由、网关自己的 `/actuator/**` 和 `/scalar`、没匹配路由的路径（继续往下走，由路由层给 404 `GW-0404`） |

- 规则只看路径不看方法：`GET /auth/login` 这类由下游回 405，网关不替它判断。
- 公开名单是配置类里的常量 `IDENTITY_PUBLIC_PATHS`，README 列同一份。

### 6.3 各种请求的结果

| 请求 | 结果 |
|---|---|
| 不带令牌 → 公开路径 | 放行，下游没有 `Authorization` |
| 不带令牌 → `/patra-identity/auth/me` | 401 `GW-0401`，`WWW-Authenticate: Bearer`，`application/problem+json` |
| 无效、过期、不存在的令牌 → 需登录路径 | 同上，和不带令牌一样 |
| 无效令牌 → 公开路径 | 放行，外部 `Authorization` 被剥掉，下游当匿名 |
| 有效令牌 → 任何放行的路径 | 放行，下游收到断言 |
| 任何人 → 三组拒绝路径 | 403 `GW-0403`，`Access denied` |
| 不带令牌 → identity 上不存在的路径 | 401（规则先于路由）；已登录才看到下游的 404 |
| `//`、`%2F`、`;`、`..` 这类路径 | 400，Spring Security 防火墙的默认输出，不是 ProblemDetail |

## 7. 查会话

### 7.1 `SessionTokenAuthenticationConverter`

| 收到的请求 | 结果 |
|---|---|
| 没有 `Authorization` 头 | `null`，匿名 |
| 多个 `Authorization` 头 | `null`，匿名；出站时一起剥掉 |
| 一个头，但不是 `Bearer` 方案（比如 `Basic`） | `null`，匿名 |
| `Bearer` 后面不是会话令牌格式（比如别人签的 JWT、随手写的串） | `null`，匿名；`SessionToken.parse` 不匹配正则就不哈希、不查 Redis |
| 会话令牌，Redis 里没有或已到绝对过期 | `null`，匿名 |
| 会话令牌，查到 | `new CurrentUserAuthentication(session.toCurrentUser())`，不带凭据；`findAndTouch` 已顺手续期 |
| 查会话时 `SessionStoreUnavailableException` | 抛 `AuthenticationServiceException`（保留原因）→ `GW-0503` |
| 查会话时其他运行时异常（`NOAUTH`、`WRONGTYPE`、会话字段坏掉导致 `StoredSession` 构造失败） | 抛 `SessionLookupFailedException`（保留原因）→ `GW-0500` |

- `Bearer` 比较不区分大小写（RFC 6750），令牌去首尾空白，和 starter 的转换器一致。
- 前六行都是「当匿名」，是否放行交给第 6 节的规则。
- 后两行走 `AuthenticationFilter` 的失败处理器，由 `SecurityProblemWriter` 输出：`detail` 是固定短句（`Service Unavailable` / `Internal Server Error`），原始原因只进日志，写出器对 5xx 记 ERROR 带堆栈。最后一行不包成 503 的原因和安全 starter 设计第 9.2 节一样：配置错和数据坏是缺陷，伪装成「稍后再试」会把事故藏起来。
- 令牌不进日志：转换器不打印请求头，两种异常的消息是固定文案，`SessionToken.toString()` 只输出前缀。

### 7.2 Redis

| 项 | 设计 |
|---|---|
| Bean | `RedisSessionStore(StringRedisTemplate, Clock)` 在 `GatewaySecurityConfiguration` 里声明 |
| 地址 | dev：`spring.data.redis.url: ${GATEWAY_REDIS_URL:redis://${PATRA_INFRA_HOST:127.0.0.1}:16379}`；container：`${GATEWAY_REDIS_URL}` 不给默认值。变量名和带密码的写法由 PAP-66 定稿，这里按 identity 的 `IDENTITY_REDIS_URL` 同一套路起名 |
| 超时 | `application.yml`：`connect-timeout: 5s`、`timeout: 2s`。identity 是 10 秒 / 5 秒；网关上每个带令牌的请求都要等这一跳，把 Redis 卡住时的等待从 Lettuce 默认的 60 秒压到秒级。`timeout` 是单条命令的等待上限，建连（含断线后重连）的耗时另计，两者不共用一个总预算，所以这不是「两秒内必定返回 503」的承诺，一次请求最坏可能等到两者之和；本版没有对总耗时的验收要求，集成测试只证明失败时是 503，不证明时限 |

### 7.3 会话模块补一条暂时失败的判定

Redis 重启瞬间在途命令的 `Connection closed` 是 Lettuce 的 `RedisException`，被 Spring 包成 `RedisSystemException("Redis exception", …)`，现在判成 500。按第 3 节核过的异常层级，`RedisCommandExecutionException` 是「服务端回了错误」，其余直接继承 `RedisException` 的都是连接层的事。`TransientRedisFailures.isTransient` 加一条：

- `RedisSystemException` 的原因是 `io.lettuce.core.RedisException` 且不是 `RedisCommandExecutionException` → 暂时失败。

覆盖连接关闭和 `RedisCommandInterruptedException`；`NOAUTH`、`WRONGTYPE`、`NOSCRIPT` 仍是缺陷或配置错。已有的 `LOADING` / `READONLY` / `BUSY` 子类判定和 `MASTERDOWN` 前缀判定不动。改在会话模块，identity 的登录限流一起受益。

## 8. 出站请求头

两个 `RequestHttpHeadersFilter` Bean，都排在框架的 X-Forwarded / Forwarded 过滤器（order 0）之前：

| Bean | order | 做什么 |
|---|---|---|
| `ExternalForwardedHeadersFilter` | -200 | 复制一份头，去掉名字以 `x-forwarded-` 开头的和 `forwarded`（不分大小写）。随后框架的过滤器在干净的头上追加网关自己的值，下游拿到的转发头只可能是网关写的。`trusted-proxies: ".*"` 保留，它只负责让追加生效 |
| `IdentityAssertionRequestHeadersFilter` | -100 | 复制一份头，去掉 `Authorization`；从 `SecurityContextHolder.getContextHolderStrategy()` 取当前认证对象，是 `CurrentUserAuthentication` 就 `setBearerAuth(signer.sign(user))`，匿名什么都不写。和 starter 的 `IdentityAssertionForwardingInterceptor` 取上下文的方式相同 |

- 返回的都是新建的可变 `HttpHeaders`：传进来的那份是 `ServerRequest` 的只读视图，框架随后还要在最终结果上删 `Host`。
- `ProxyExchangeHandlerFunction` 在 `DispatcherServlet` 里、请求线程上调用这些过滤器，安全过滤器链放进线程上下文的认证对象此时还在。
- 断言每个请求现签、不缓存，有效期 60 秒由 starter 的常量决定；登出和封禁删掉会话后，下一个请求就签不出断言。
- 将来网关前面真放了反向代理：删掉 `ExternalForwardedHeadersFilter`，把 `trusted-proxies` 改成代理的地址。README 写明。

## 9. 签名器与密钥

### 9.1 配置项

`GatewayIdentityAssertionProperties(String privateKey)`，前缀 `patra.gateway.identity-assertion`。记录只存字符串，构造器不做任何校验：绑定阶段一旦抛异常，Boot 的失败报告会把配置值原样打进日志，私钥就泄露在日志里了。

| 文件 | 配置 |
|---|---|
| `application-dev.yml` | `patra.gateway.identity-assertion.private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}`、`patra.security.identity-assertion.public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}`，都不给默认值 |
| `application-container.yml` | 同一组占位 |

变量名由 PAP-66 定稿。本地跑网关前先用 starter 的 `generateIdentityAssertionKey -PkeyOut=<路径>` 生成一对：私钥文件的内容给网关，标准输出的公钥同时给网关和 identity，用完删掉文件；步骤写进网关 README。密钥不进仓库（决策 G）。

### 9.2 签名器 Bean 与启动自检

校验和解析都放在 `GatewaySecurityConfiguration` 的包级静态方法 `createSigner(String privateKey, Clock clock, JwtDecoder decoder)` 里，Bean 方法调它，参数里的 `JwtDecoder` 按名字 `identityAssertionDecoder` 注入：

1. 没配或空白 → `IllegalStateException`，消息点名 `patra.gateway.identity-assertion.private-key`。
2. `ECKey.parse` 失败 → 「不是合法的 JWK」。
3. `new IdentityAssertionSigner(key, clock)`：含私钥、带 `kid`、P-256 由它的构造器现成检查。
4. **自检**：签一条探针断言，用 `identityAssertionDecoder`（它用的是网关配的公钥）验一次；`JwtException` → 「私钥与 `patra.security.identity-assertion.public-keys` 不配对或 `kid` 不一致」。密钥换错、公私钥不成对在启动时就暴露，不等到第一个登录用户拿到 401。
5. 启动日志记一行 INFO 带 `kid`。

所有错误信息里只出现 `kid`，不出现密钥内容。

starter 的 `SecurityServletAutoConfiguration` 全部保留：网关需要它的写出器、错误映射、重抛处理器和验签器；只有默认过滤器链因为网关自己声明了而让位。

## 10. 路由与文档聚合

`application.yml`：

- 第四条路由 `patra-identity`：`uri: lb://patra-identity`，`Path=/patra-identity/**`，`StripPrefix=1`，和另外三条同形。
- `scalar.sources` 加 `/patra-identity/v3/api-docs`，标题 Identity Service，slug `identity`。

## 11. 错误契约

网关鉴权产生的响应都是 `application/problem+json`，经 `SecurityProblemWriter` 输出，`instance` 是请求路径：

| 场景 | 状态 | 错误码 | detail | 其他 |
|---|---|---|---|---|
| 匿名（含无效、过期、不存在的令牌）访问需登录路径 | 401 | `GW-0401` | Authentication required | `WWW-Authenticate: Bearer` |
| 任何人访问 `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` | 403 | `GW-0403` | Access denied | 不查 Redis |
| 带会话令牌且 Redis 暂时不可用（任何路由，含公开和登出） | 503 | `GW-0503` | Service Unavailable | ERROR 日志带原因 |
| 带会话令牌且查会话遇到非暂时失败 | 500 | `GW-0500` | Internal Server Error | ERROR 日志带堆栈 |
| 路径被防火墙拒绝 | 400 | 无 | 空 | 框架默认，本版不改 |

PAP-69 的 404 / 503 / 504 表不变。identity 自己的 `IDN-0401` 仍会出现：带有效断言但用户已封禁的 60 秒窗口里，`/auth/me` 由 identity 回 401。门户（PAP-67）按「任何 401 都清 Cookie」处理，不区分前缀。

## 12. 配置与文档改动

| 文件 | 改动 |
|---|---|
| `patra-api/patra-gateway-boot/build.gradle.kts` | 加 security starter、session 模块；`testImplementation(testFixtures(security starter))` |
| `application.yml` | 第四条路由、Scalar 源、Redis 超时 |
| `application-dev.yml` | Redis 地址、两个密钥配置项 |
| `application-container.yml` | 同一组占位，不给默认值 |
| `spotbugs-exclude.xml` | 新包 `gateway.security` 比照 `gateway.error` 排除 EI_EXPOSE_REP / EI_EXPOSE_REP2（注入的 Bean 被存成字段） |
| `patra-infra/cd/module-graph.json` | 随依赖重新生成 |

| 文档 | 改动 |
|---|---|
| `patra-api/patra-gateway-boot/README.md` | 重写「职责」「错误」「配置」「测试」：路径规则表、两条链、出站改头、密钥生成与本地启动步骤、反向代理的注意事项；开头「鉴权在 PAP-65 加」改成指向本设计 |
| `patra-api/patra-identity/patra-identity-session/README.md` | `TransientRedisFailures` 新增那条规则 |
| 安全 starter README「给网关用的部分」 | 不动，它列的类正是网关用到的 |
| 本设计第 14 节 | 实施后写回实测结果 |

## 13. 测试策略

### 13.1 单元测试（`src/test`，不起 Spring）

| 测试类 | 钉住什么 |
|---|---|
| `SessionTokenAuthenticationConverterTest` | 第 7.1 节表格逐行：没头、多头、非 Bearer、非令牌格式、查不到都返回 `null`；查到时主体字段、凭据为 `null`、已认证；`SessionStoreUnavailableException` → `AuthenticationServiceException` 且保留原因；其他运行时异常 → `SessionLookupFailedException`；两种异常的消息里没有令牌 |
| `IdentityAssertionRequestHeadersFilterTest` | 匿名时剥掉一个或多个 `Authorization`，其他头原样；已登录时写入断言，用测试公钥能验出同一个用户；返回的是新的可变实例 |
| `ExternalForwardedHeadersFilterTest` | 大小写混写的 `X-Forwarded-*` 和 `Forwarded` 都去掉，其他头保留；order 小于 0 |
| `GatewaySecurityErrorMappingContributorTest` | `SessionLookupFailedException` → `INTERNAL_ERROR`；父类的 503 / 401 / 403 映射仍在 |
| `GatewaySecurityConfigurationTest`（`createSigner`） | 没配、不是 JWK、只有公钥、和验签器不配对四种启动失败，消息点名配置项且不含密钥内容；配对时签出的断言能被验签器解出 |
| 会话模块 `TransientRedisFailuresTest` 补用例 | `RedisException("Connection closed")`、`RedisCommandInterruptedException` → 暂时；`RedisCommandExecutionException("NOAUTH …")`、`WRONGTYPE` → 不是 |

### 13.2 集成测试（`src/integrationTest`，真实端口）

所有网关 IT 从此都要 Redis 和密钥才能起上下文：

- 新增 `GatewayITSigningKeyInitializer`（`ApplicationContextInitializer`）把 `TestSigningKey` 的私钥 JWK 写进 `patra.gateway.identity-assertion.private-key`；公钥由 starter 测试支持的 `TestIdentityAssertionEnvironmentPostProcessor` 自动注入。
- 现有 `PatraGatewayApplicationIT`、`GatewayRoutingIT`、`GatewayFailureIT` 挂上 `RedisContainerInitializer`（starter-test）和它。`GatewayRoutingIT` 里「`Authorization` 原样透传」的用例删掉，由下面的用例取代。
- 下游用 WireMock 顶替 identity 和 catalog，`lb://patra-identity`、`lb://patra-catalog` 经 `spring.cloud.discovery.client.simple.instances.*` 指到它。
- 会话用注入的 `RedisSessionStore.create` 直接写进测试容器，`createdAt` 和 `expiresAt` 由测试给，不需要可调时钟。

| 测试类 | 用例 |
|---|---|
| `GatewayAuthenticationIT` | 匿名访问公开路径放行且下游没有 `Authorization`；匿名访问 `/auth/me` 401 带 `WWW-Authenticate` 和 problem+json，下游收不到请求；格式正确但不存在的令牌访问 `/auth/me` 401、访问公开路径放行；有效令牌访问 `/auth/me`，下游收到的断言用网关的 `identityAssertionDecoder` 解出 `sub`、`sid`、`account_type`、`client_type` 与会话一致；外部 `Basic` 头、别的密钥签的 JWT、两个 `Authorization` 头都到不了下游、下游当匿名；删掉会话后同一令牌 401，拿它登出则以匿名到达 identity 并透传 204；会话最后活跃时间落后两分钟时一次请求后被写回且 TTL 重算，绝对过期只剩十分钟时 TTL 不超过十分钟；已登录请求无 `Set-Cookie`、不是 3xx；`/patra-identity/` 下没点名的路径匿名 401、已登录到达下游；公开名单四条匿名到达下游；客户端自带的 `X-Forwarded-Host` / `X-Forwarded-Prefix` 和 `Forwarded` 到下游时只剩网关写的值 |
| `GatewayBlockedPathsIT` | 三组路径各取一个真实接口（`GET /patra-registry/_internal/provenances`、`POST /patra-identity/admin/users/1/ban`、`GET /patra-catalog/actuator/health`），匿名和已登录都 403 `GW-0403`、无 `WWW-Authenticate`、下游零请求；`/patra-catalog/_internal` 不带尾段、`%5Finternal` 编码同样 403；网关自己的 `/actuator/health` 仍 200；`//` 得到 400 |
| `GatewayRedisUnavailableIT` | Redis 指向没人监听的端口（和 identity 的 `RedisUnavailableIT` 同一写法），超时压到 500 毫秒：带令牌访问 `/auth/me`、公开路径、登出都是 503 `GW-0503`；不带令牌访问公开路径 200 |
| `PatraGatewayApplicationIT` 补断言 | 容器里恰好两条 `SecurityFilterChain`；上下文能起来即启动自检通过 |

## 14. 实施时要实测的点

实施后本地做，结果写回本节：

1. 本地 compose 的 Redis 和 PostgreSQL + 生成的一对密钥，起 identity 和网关，经网关注册、查当前用户、登出、再查，看四个状态码；顺带看一眼网关日志里没有令牌、没有密钥。
2. 门户 Playwright 指向本地网关跑一遍（`--workers=1`），证明门户读接口和改动前一致。
3. 循环打带令牌的请求，期间停掉再起 Redis 容器，确认是 503 不是 500，记下实际的异常类和消息，核对第 7.3 节的判定。
4. 用错误的私钥启动一次，看启动失败的提示里有没有把密钥打出来。
5. `//`、`%2F` 这类路径确实被防火墙以 400 拒绝（第 6.3 节最后一行按框架默认写，IT 里钉住）。

## 15. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 网关启动必须有三样东西：`PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`（含私钥的 JWK JSON，secret 注入）、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`（网关也要配自己那把公钥）、`GATEWAY_REDIS_URL`（带密码）。**顺序**：mini 上先把这三样放进网关的环境，再部署带本设计的网关镜像；否则 CD 推上去的网关容器起不来，mini 上的门户随之不可用 | PAP-66 |
| identity 的 compose 条目、`services.json`、建库；网关 Scalar 聚合已在本设计里加 | PAP-66 |
| 网关的 401 是 `GW-0401`，identity 的是 `IDN-0401`，门户按「任何 401 都清 Cookie」处理；门户不会打到 403 的拒绝名单 | PAP-67 |
| 网关前面放反向代理时：删掉 `ExternalForwardedHeadersFilter`，`trusted-proxies` 改成代理地址 | 引入反向代理的 Issue |
| 后台账号上线时：`SessionToken` 的前缀映射加 `STAFF`；`/*/admin/**` 从拒绝名单改成按账号类型和角色判断 | 后台账号的 Issue |
| 防火墙的 400 要不要也输出 ProblemDetail | 需要时再立 |

## 16. 要同步改的文档和 Issue

- `patra-api/patra-gateway-boot/README.md`：按第 12 节重写。
- `patra-api/patra-identity/patra-identity-session/README.md`：第 7.3 节的新规则。
- Linear PAP-65：To-Do 的 Tech Design 项指向本设计；第 3 节的三个决定写进描述；To-Do 和 AC 随实施与实测勾掉。
- Linear PAP-66：第 15 节第一行的顺序约束写进描述。
