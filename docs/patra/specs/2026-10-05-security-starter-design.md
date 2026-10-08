# 安全 starter 工程设计（PAP-62 / PAP-70）

> **Issue**：[PAP-62](https://linear.app/papertrace/issue/PAP-62)（首版）、[PAP-70](https://linear.app/papertrace/issue/PAP-70)（改版：签名身份断言）
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)
> **日期**：2026-10-05；2026-10-08 改写
> **状态**：首版与改版都已实现

**改版记录**（2026-10-08，PAP-70）：网关向下游传递身份的方式从「五个明文身份头 + 共享内部令牌」改成「网关签名的身份断言」，对应 release spec 决策 C 的改定。本文按改版后的最终形态写，第 6、7、8.2、8.3、10、13、14、15、16、17 节是这次改写的；身份头方案的原文见本文在 PAP-70 之前的 git 历史。

## 1. 要解决的问题

v0.8 引入账号与登录。网关按会话令牌查 Redis 会话，通过后用自己的私钥签一个短期的身份断言转给下游（release spec 决策 B、C）。下游服务需要一个统一的办法验签得到「当前用户」，在需要登录的地方给出一致的 401；服务替用户调用另一个服务时，要能把身份带过去。以后的 admin 后台还要在此之上做角色与权限。

本设计交付这个办法：一份纯 Java 的当前用户抽象，一个基于 Spring Security 的 servlet starter（断言的格式、签名器、验签、转发都在里面），http-interface starter 的一个小 SPI，以及 linqibin-commons 里两处配套修改。

**使用方**：本版是 identity 和 gateway。identity 用它验签得到当前用户，内部调用时转发断言；gateway 用它里面的签名器、认证对象和错误输出，自己再加查会话与路径规则（PAP-65）。

**完成标准**：首版是 PAP-62 的 Acceptance Criteria，改版是 PAP-70 的 Acceptance Criteria。

## 2. 范围

做：

- 当前用户的抽象与端口，domain 可以依赖。
- 身份断言：格式、签名器、验签器。网关、下游、测试支持共用这一份实现。
- 下游的 Spring Security 默认配置：无会话，从断言建立认证。
- 401 / 403 接到现有的统一错误格式。
- JPA 审计列自动填当前用户 ID。
- 「以某个用户的身份执行」的入口，给定时任务和消息消费用。
- 内部客户端转发断言：服务替用户同步调用另一个服务时，对方也能得到当前用户。
- 生成密钥对的任务。
- 测试支持与 README。

不做：

- 查会话、续期、路径规则、在网关里调用签名器。这些在网关里，属于 PAP-65。本设计只定它们用到的契约和可复用的类。
- 会话的写入与删除、登录记录、密码哈希的选型。属于 PAP-63、PAP-64。
- 密钥的生成与注入这两个运维动作。属于 PAP-66。本设计只提供生成任务和配置项。
- 角色与方法级权限注解。本版没有角色，`@EnableMethodSecurity` 不开；认证对象里的权限列表留空，做 admin 时往里加。
- 服务自己的机器身份（调用方是谁）。属于 release spec 边界 F。
- 异步场景里的用户身份。领域事件、定时任务、消息消费里不转发断言，发起人的用户 ID 作为数据传递，消费方以系统身份执行。
- 错误响应里的 traceId。现有错误响应本来就不带，这是全局的可观测性缺口，与本模块无关。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 会话用不透明令牌 + 自建的 Redis 会话；网关查完会话后签一个 60 秒的身份断言转给下游，下游用公钥验签 | release spec 决策 B、C（2026-10-08 改定） |
| 断言的受众是整个 Patra，服务之间同步调用时原样转发 | release spec 决策 C |
| 认证框架用 Spring Security，不用 Spring Session，不用 Sa-Token | release spec 决策 I |
| 全仓库只有 servlet 一套，网关切到 Spring Cloud Gateway 的 WebMVC 版 | release spec 决策 H，PAP-69 |
| 模块是 Patra 的身份约定，放 `patra-starters`，不进通用库 linqibin-commons | 设计过程 |
| linqibin-commons 已与 super-nb 分离，只为 Patra 服务，可以直接改 | main 上的 `6d538cf83` |
| 用户 ID 是雪花 Long；账号分前台用户和后台账号两类 | release spec 决策 A、D |
| 私钥只在网关，由 secret 注入；公钥不是机密 | release spec 决策 G |
| 签名算法 ES256，密钥用 JWK JSON，断言无效返回 401，跨服务原样转发 | PAP-70 计划页的四个决策，2026-10-08 定稿 |

版本：Spring Boot 4.0.8、Spring Security 7.0.7（JWT 支持来自 `spring-security-oauth2-jose`，内含 Nimbus JOSE + JWT）、Spring Cloud Gateway 5.0.3。下文的类名和默认行为都对着这几个版本的源码或文档核对过；没有实际运行验证的点单列在第 15 节。

## 4. 模块

新增两个模块，改动三个现有模块。

| 模块 | Gradle 路径 | 依赖 |
|---|---|---|
| `patra-common-security`（新） | `:patra-api:patra-common:patra-common-security` | `linqibin-commons-core` |
| `patra-spring-boot-starter-security`（新） | `:patra-starters:patra-spring-boot-starter-security` | `patra-common-security`、`linqibin-spring-boot-starter-web`、`spring-boot-starter-security`、`spring-security-oauth2-jose`；`linqibin-spring-boot-starter-jpa` 和 `linqibin-spring-boot-starter-http-interface` 为 `compileOnly` |
| `linqibin-spring-boot-starter-http-interface`（改） | 已有 | 不变；新增一个 SPI |
| `linqibin-spring-boot-starter-jpa`（改） | 已有 | 不变 |
| `linqibin-spring-boot-starter-web`（改） | 已有 | 不变 |

依赖方向：

```
某服务的 domain ──▶ patra-common-security ──▶ linqibin-commons-core
某服务的 boot  ──▶ patra-spring-boot-starter-security ──▶ patra-common-security
                                                    ├──▶ linqibin-spring-boot-starter-web
                                                    ├──▶ spring-boot-starter-security
                                                    └──▶ spring-security-oauth2-jose
                                                    ┈┈▶ linqibin-spring-boot-starter-http-interface（compileOnly，只为转发）
```

三条硬约束：

- **Spring Security 只经安全 starter 进入 classpath。** 不把它加进 starter-web 或任何公共模块。它一进 classpath，Boot 就给所有请求加上「必须登录」的默认规则；本版只有 identity 和 gateway 引安全 starter。
- **linqibin-commons 的模块不依赖这两个新模块。** http-interface starter 的新 SPI 是它自己定义的接口，安全 starter 来实现，方向不反。现有的 `checkBoundary` 任务验证。
- **私钥不出网关。** 安全 starter 里有签名器，但不提供签名用的配置项；只有网关自己的配置里有私钥。

构建接入：`settings.gradle.kts` 的两条 `includeAt` 首版已加；依赖关系变了，重新生成 `patra-infra/cd/module-graph.json`。starter 的 `integrationTest` 配置额外带上 http-interface starter，供第 14.2 节的转发用例使用；单元测试的 classpath 故意不带它。

## 5. 当前用户的抽象（`patra-common-security`）

包名 `dev.linqibin.patra.common.security`。纯 Java，不依赖 Spring。首版已实现，改版不动。

| 类型 | 内容 |
|---|---|
| `AccountType` | 枚举。本版只有 `USER`（前台用户），字符串 `user`。`fromCode(String)` 对不认识的值返回空结果 |
| `ClientType` | 枚举。本版只有 `WEB`，字符串 `web`。同样有 `fromCode` |
| `CurrentUser` | 记录类型：`userId`（long）、`sessionId`（long）、`accountType`、`clientType`。静态工厂 `of(...)`；紧凑构造器校验两个 ID 为正数、两个枚举非空 |
| `CurrentUserPort` | `Optional<CurrentUser> current()`；`default CurrentUser require()`，没有当前用户时抛 `AuthenticationRequiredException` |
| `AuthenticationRequiredException` | 继承 `DomainException`，带 `StandardErrorTrait.UNAUTHORIZED`。消息是固定的英文短句 |

`CurrentUser` 不放邮箱等个人信息。会话 ID 放进来，是因为 identity 登出时要靠它定位删哪条会话。

现有的错误解析引擎会把带 `UNAUTHORIZED` 特征的异常解析成 `{PREFIX}-0401`，不需要新增映射。

## 6. 身份断言

网关查完会话后签的一个短期 JWT。它是网关和下游之间唯一的身份载体，放在 `Authorization: Bearer` 头里。原来的五个 `X-Patra-*` 头全部废除。

### 6.1 格式

JWS 紧凑序列化，三段。

| 位置 | 字段 | 值 |
|---|---|---|
| 头 | `alg` | `ES256` |
| 头 | `typ` | `patra-identity+jwt`。显式类型，按 RFC 8725 的建议防止和别的 JWT 混用 |
| 头 | `kid` | 签名用的那把公钥的指纹 |
| 载荷 | `iss` | `patra-gateway` |
| 载荷 | `aud` | `patra`。整个 Patra 是一个信任域，所以能在服务之间转发（第 10 节） |
| 载荷 | `sub` | 用户 ID，十进制字符串 |
| 载荷 | `sid` | 会话 ID，十进制字符串 |
| 载荷 | `account_type` | `AccountType` 的字符串 |
| 载荷 | `client_type` | `ClientType` 的字符串 |
| 载荷 | `iat` | 签出时间 |
| 载荷 | `exp` | `iat` 加 60 秒 |

不放 `nbf`、`jti`，没有重放检测：断言只覆盖一次请求和它的同步调用，60 秒已经限制了泄露后的可用时间。

两个 ID 用字符串：和接口响应里的写法一致，别的语言的消费方不会因为 JSON 数字精度丢位。

常量和转换集中在 `IdentityAssertionClaims` 类里：`TYPE`、`ISSUER`、`AUDIENCE`、`SESSION_ID`、`ACCOUNT_TYPE`、`CLIENT_TYPE`、`LIFETIME`，以及 `toCurrentUser(Jwt)`：从已验签的断言得到 `CurrentUser`。`sub`、`sid` 不是正整数，或两个类型不认识时抛 `MalformedIdentityException`。

### 6.2 签名器 `IdentityAssertionSigner`

- 构造参数：含私钥的 `ECKey`（JWK）、`Clock`。`kid` 从 JWK 里取。
- `String sign(CurrentUser user)`：按 6.1 组装头和载荷，用 Nimbus 的 `ECDSASigner` 签名，返回紧凑序列化。
- 签名失败（`JOSEException`）包成 `IllegalStateException`：密钥坏了是配置错误，不是请求错误。
- 每次调用都现签，不缓存。

签名器放在 starter 里，不做成自动配置的 Bean：网关在自己的配置里用私钥构造它（第 17 节），测试支持用临时密钥构造它。这样断言的格式只有一份代码，而签名用的配置项不会出现在下游。

### 6.3 验签器 `IdentityAssertionDecoders`

静态工厂 `JwtDecoder forPublicKeys(JWKSet publicKeys, Clock clock)`，返回 Spring Security 的 `NimbusJwtDecoder`：

- `NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(publicKeys))`，算法只允许 `ES256`。Nimbus 按头里的 `kid` 在集合里选公钥。
- 不设 Nimbus 自己的 `typ` 校验（Spring 的构建器默认不查）：所有声明的校验都放在验签之后，验签之前不读未签名的内容，错误信息里也就不会带上它。
- 校验器用 `DelegatingOAuth2TokenValidator` 串起来：`JwtTypeValidator(TYPE)`、`JwtIssuerValidator(ISSUER)`、`JwtAudienceValidator(AUDIENCE)`、`JwtTimestampValidator`（容差 30 秒，注入 `Clock`，不允许缺 `exp`），再加一个校验器要求有 `iat` 且 `exp` 减 `iat` 不超过 60 秒。有效期的长度在验签这边也钉死，签名方签出长命断言同样被拒。Spring 默认的声明转换器会把缺失的 `iat` 补成 `exp` 减一秒，这里换成不补的。
- 任何一项不过，`decode` 抛 `JwtException`，原因在异常消息里。

### 6.4 密钥

密钥用 JWK JSON，不用 PEM：一行文本，放环境变量方便；多把公钥天然是 `keys` 数组；Nimbus 直接解析，没有自己写的解析代码。

| 哪边 | 内容 | 来源 |
|---|---|---|
| 网关 | 一把含私钥的 EC P-256 JWK，带 `kid` | secret 注入（PAP-66） |
| 下游 | JWK Set JSON `{"keys":[…]}`，只含公钥，可以多把 | 配置项 `patra.security.identity-assertion.public-keys`（8.3 节） |

`kid` 是公钥的 JWK 指纹（RFC 7638），生成时算好写进 JWK。签名器和验签器都只认 JWK 里的 `kid`，不需要另外配。

**生成**：starter 的 Gradle 任务 `generateIdentityAssertionKey -PkeyOut=<路径>`（`JavaExec`，跑 starter 里的 `IdentityAssertionKeyGenerator`）。它用 Nimbus 的 `ECKeyGenerator(Curve.P_256)` 生成一对密钥：含私钥的 JWK 只写进 `-PkeyOut` 指定的文件（权限 0600，文件不能已存在），标准输出只有只含公钥的 JWK Set。私钥不打到标准输出：Gradle 会把标准输出记进 daemon 日志。

**换密钥**：先把新公钥加进各下游的 `keys` 并重启，再给网关换私钥，最后从下游删掉旧公钥。因为按 `kid` 选钥，两把公钥并存期间新旧断言都能验。

密钥不进仓库：生产的由 PAP-66 注入；测试的每次在 JVM 里现生成（14.1 节）。

## 7. 认证对象

`CurrentUserAuthentication`，继承 Spring Security 的 `AbstractAuthenticationToken`：

- 主体（principal）是 `CurrentUser`。
- 凭据（credentials）是原始断言的紧凑序列化字符串；由 `CurrentUserRunner` 放进去的用户没有断言，凭据为 null。凭据只给第 10 节的转发用。
- 权限列表本版为空。做 admin 时由建立认证的一方把角色填进来，这个类不用改结构。
- `getName()` 返回用户 ID 的字符串。默认实现会把整个记录打进日志，所以要重写。`toString()` 沿用父类，它把凭据打成 `[PROTECTED]`。
- 构造出来就是已认证状态。

网关和下游都用这一个类型：网关查到会话后构造它（不带凭据），下游验签后构造它（带断言）。

## 8. 下游的处理

### 8.1 过滤器链

starter 提供一条默认的 `SecurityFilterChain`（服务自己声明了就让位）。配置：

| 项 | 设置 | 原因 |
|---|---|---|
| 会话 | `STATELESS` | 不创建 HttpSession；安全上下文只放在请求属性里，请求缓存自动变成空实现 |
| CSRF | 关 | 凭据在请求头里，不在 Cookie 里 |
| 登出 | 关 | 不关的话 `/logout` 路径会被框架劫持并重定向 |
| 匿名用户 | 保持框架默认（开） | 框架靠匿名认证对象来区分「没登录」和「登录了但不允许」：没登录时给 401，否则给 403。关掉的话安全上下文是空的，框架不把空当成匿名，没登录也会得到 403 |
| 表单登录、HTTP Basic | 不配置 | 登录是 identity 的普通接口，不走框架的登录机制 |
| 未登录的入口点 | 显式指定，输出 401 | 关掉表单登录后框架默认给 403 |
| 拒绝访问的处理器 | 显式指定，输出 403 | 同上 |
| 路径规则 | `ERROR`、`FORWARD` 两种转发类型放行；其余全部放行 | 下游不做路径级拦截，路由规则归网关；框架对每次转发都重新授权，不放行的话错误页会被 401 覆盖 |
| 响应头 | 保持框架默认 | 缓存相关的头只在响应还没有时才写，不覆盖业务自己设的 |

「无会话、关 CSRF、关登出、指定入口点和拒绝处理器」这一组抽成一个公开的静态方法 `StatelessSecurityDefaults.apply`。网关写自己的过滤器链时调用同一个方法，再加自己的规则。

下游全部放行的含义：需要登录的接口由业务代码调 `require()` 来保证，不靠路径规则。这样 `/_internal/**` 这类服务之间直连的接口不受影响：不带断言的调用按匿名通过，带断言的调用（第 10 节）对方能得到当前用户。

### 8.2 从断言建立认证

用 Spring Security 的通用认证过滤器 `AuthenticationFilter`，加一个自己的转换器 `IdentityAssertionAuthenticationConverter`。它持有一个 `JwtDecoder`（6.3 节）。

| 收到的请求 | 转换器的结果 | 最终 |
|---|---|---|
| 没有 `Authorization` 头 | null | 匿名，继续 |
| 恰好一个 `Authorization` 头，`Bearer` 方案，断言有效 | `CurrentUserAuthentication`，凭据是断言 | 已登录 |
| `Authorization` 头出现多次，或不是 `Bearer` 方案，或断言无效（签名、`typ`、`iss`、`aud`、过期任一不对） | 抛 `BadCredentialsException`，原因只进日志 | `{PREFIX}-0401` |
| 断言有效但内容不合法（`sub`、`sid` 不是正整数，账号或客户端类型不认识） | 抛 `MalformedIdentityException` | `{PREFIX}-0500` |

`Bearer` 的比较不区分大小写（RFC 6750）。取出的断言去掉首尾空白。

第三行返回 401 而不是当匿名：断言只可能来自网关，无效说明配置错了或有人伪造，静默当成未登录会把换密钥配错这类问题藏起来。日志记 WARN 一行，写原因（过期、签名不对、受众不对），不写断言内容；原因先去掉控制字符并截短，解码器的异常消息里可能有未签名的内容，不能让它伪造出日志行。

第四行只可能是网关自己的缺陷，所以按服务端错误处理并记错误日志，不伪装成 401。

不用 Spring Security 的 `oauth2ResourceServer` DSL，也不引 `spring-boot-starter-oauth2-resource-server`：它会带来自己的过滤器、入口点和 Boot 自动配置，和现有的 `AuthenticationFilter` 加统一写出器重叠；只引 `spring-security-oauth2-jose` 拿 `JwtDecoder` 和校验器就够了。

过滤器的几处必须改掉的默认行为（首版已做）：

- **成功处理器换成空实现。** 默认的会先回一个 302 重定向，然后还继续执行过滤器链。
- **失败处理器换成自己的。** 默认的遇到服务类异常会原样抛出，变成容器的 500 页面。转换器抛出的两种异常都是 `AuthenticationException`，走失败处理器，由统一写出器输出。
- **过滤器不声明成 Bean**，在配置过滤器链时直接 new。声明成 Bean 的话 Boot 会把它再注册成全局过滤器，在别的链上也执行。

`AuthenticationFilter` 需要一个认证管理器。下游用的是直通实现：转换器给出的已经是认证完成的对象，原样返回。不经过 `ProviderManager`，所以凭据不会被它擦掉，转发时还在。

### 8.3 公钥配置

- 配置项 `patra.security.identity-assertion.public-keys`：JWK Set JSON 字符串，只含公钥，至少一把，可以多把。
- 启动时校验：没配、为空、解析不了、没有 `keys`、任何一把不是 EC P-256 公钥或没有 `kid`，应用启动失败，错误信息指明配置项和原因。
- 它只在 servlet 应用里必填，因为只有验签时才用到。
- 公钥不是机密，日志里可以出现 `kid`，不输出整个 JWK。

容器里的给法由 PAP-66 定，`application-container.yml` 里写 `${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}` 一类的占位。

### 8.4 取当前用户

`CurrentUserPort` 的实现 `SecurityContextCurrentUserAdapter`：从 Spring Security 的安全上下文里取认证对象，主体是 `CurrentUser` 就返回它，否则返回空。判断的是主体的类型，不是 `isAuthenticated()`：匿名认证对象的 `isAuthenticated()` 也是 true，它的主体是一个字符串，按类型判断不会把它误认成用户。

线程上下文的设置和清理由 Spring Security 的过滤器负责，业务代码不碰。

## 9. 错误输出

三条路径，输出同一种格式。

| 情况 | 从哪来 | 结果 |
|---|---|---|
| 需要登录但没登录 | 业务代码调 `require()` | `{PREFIX}-0401` |
| 登录了但不允许 | 业务代码抛带 `FORBIDDEN` 特征的领域异常 | `{PREFIX}-0403` |
| 过滤器链里被拒绝 | 入口点、拒绝处理器、认证失败处理器 | `{PREFIX}-0401` / `0403` / `0500` / `0503` |
| 控制器或方法注解里抛出 Spring Security 的异常 | 以后的权限注解 | `{PREFIX}-0401` / `0403` |

前两行走现有的错误解析，本模块只用测试确认。后两行需要下面三样东西。

### 9.1 `SecurityProblemWriter`

过滤器链里的失败到不了全局异常处理器，由这个类输出。它同时充当入口点、拒绝处理器和认证失败处理器：

- 调 starter-web 的 `ProblemDetailAdapter` 把异常变成 ProblemDetail 对象。
- 状态码取适配结果里的 HTTP 状态。
- `Content-Type` 是 `application/problem+json`。
- 显式把 `instance` 设成请求路径。现有的 `ProblemDetailBuilder` 不设这个字段，控制器路径上是 Spring MVC 在写响应时补的；过滤器里没有这一步，不设就会少一个字段。
- 用容器里的 `JsonMapper` Bean 序列化。Boot 已经给它配了 ProblemDetail 的序列化规则，扩展字段会平铺在顶层。
- 401 的响应带 `WWW-Authenticate: Bearer`。

### 9.2 `SecurityErrorMappingContributor`

现有的错误解析不认识 Spring Security 的异常：拒绝访问会被解析成 500，凭据错误会被解析成 422。新增一个映射：

| 异常 | 错误码 |
|---|---|
| `MalformedIdentityException` | `{PREFIX}-0500` |
| 其他 `AuthenticationServiceException`（比如网关查会话时 Redis 不可用） | `{PREFIX}-0503` |
| 其他 `AuthenticationException`（含转换器抛的 `BadCredentialsException`） | `{PREFIX}-0401` |
| `AccessDeniedException` | `{PREFIX}-0403` |

输出给客户端的 `detail` 是固定短句，不带框架异常的原始消息。原始原因只进日志。`JwtException` 不会漏出转换器，不需要映射。

### 9.3 控制器里抛出的安全异常

`GlobalRestExceptionHandler` 以最高优先级兜住所有异常。控制器或方法注解里抛出的 Spring Security 异常会被它接走，到不了框架自己的处理器，而只有框架知道当前是匿名还是已登录，才能正确区分 401 和 403。

修法分两处（首版已做）：

- **starter-web**：`GlobalRestExceptionHandler` 的优先级从最高降一级。
- **安全 starter**：新增一个最高优先级的异常处理器，只接 `AccessDeniedException` 和 `AuthenticationException`，把它们原样抛出。原样抛出的异常 Spring MVC 当作未处理，会回到安全过滤器，由 9.1 的写出器输出。

本版不开方法级权限注解，这条路径暂时没有业务代码触发，但机制现在就做好并用测试覆盖，做 admin 时不用回头改异常处理。

## 10. 跨服务转发

服务替用户同步调用另一个服务时，把收到的断言原样放进出站请求，对方按第 8.2 节同一套规则验签。用户身份只能从网关产生，服务自己签不出来。

### 10.1 http-interface starter 的 SPI

- 新增接口 `InternalCallInterceptor extends ClientHttpRequestInterceptor`，没有新方法，只是标记：实现它的 Bean 只挂到内部服务的客户端上。
- `RestClientFactory.createRestClient` 创建客户端时，把容器里所有 `InternalCallInterceptor`（按顺序）加到该客户端上。现有的 `RestClientCustomizer` 照旧。
- `httpInterfaceLoadBalancedRestClientBuilder` 和 `httpInterfaceRestClientBuilder` 不动。

不用 `RestClientCustomizer` 来挂：它作用于 Boot 管理的每一个 `RestClient.Builder`，catalog 里调外部数据源（PubMed 等）的客户端也会被挂上，断言就带出去了。

### 10.2 安全 starter 的拦截器

`IdentityAssertionForwardingInterceptor` 实现 `InternalCallInterceptor`：

- 从 `SecurityContextHolder.getContextHolderStrategy()` 取当前认证对象。
- 是 `CurrentUserAuthentication` 且凭据是字符串时，`setBearerAuth(断言)`。
- 其余情况不动请求：匿名线程、`CurrentUserRunner` 放进去的用户（凭据为 null）、没有安全上下文的线程。

只在 classpath 上有 `InternalCallInterceptor` 时注册（类名字符串的 `@ConditionalOnClass`），由独立的 `SecurityForwardingAutoConfiguration` 注册：它不依赖 Web 环境，但只有请求线程里才有断言。

### 10.3 边界

- 断言只在同一个请求的同步调用里转发。每个请求网关都现签，一次请求在几秒内完成，60 秒绰绰有余。
- 领域事件、定时任务、消息消费里没有断言：发起人的用户 ID 作为数据写进事件或任务，消费方以系统身份执行。离开请求，用户身份就是数据，不是凭据。
- 对方得到的是用户身份，不是调用方的身份。调用方是谁，本版不验，见 release spec 边界 F。

## 11. JPA 审计列

`created_by`、`updated_by` 由 starter-jpa 里的 `auditorAware` Bean 决定填什么。它原来永远返回空。首版已改：

- **starter-jpa**：新增接口 `CurrentAuditorProvider`，一个方法，返回当前操作人的 ID，可以为空。默认的 `auditorAware` 改成：容器里有这个接口的实现就问它，没有就返回空。Bean 的名字和「别人没提供才生效」的条件都不变。
- **安全 starter**：提供 `CurrentAuditorProvider` 的实现，从 `CurrentUserPort` 取用户 ID。classpath 上有 starter-jpa 时才注册。

效果：

- 引了安全 starter 的服务，登录用户写入的记录自动带上他的用户 ID，不需要开关。转发来的断言同样生效：被调方写入的记录也带上发起人的用户 ID。
- 没引安全 starter 的服务行为不变，两列仍然是空。
- 匿名请求、不在请求里的线程，两列是空。

不用「安全 starter 提供一个 `auditorAware` 把默认的替换掉」的做法：各服务的启动类扫描整个 `dev.linqibin`，starter-jpa 的审计配置类会被提前扫到，默认的那个总是先注册，替换不掉，而且不报错。

## 12. 以某个用户的身份执行

`CurrentUserRunner`，两个静态方法：`runAs(CurrentUser, Runnable)` 和 `callAs(CurrentUser, Supplier<T>)`。首版已实现，改版不动。

- 进去时把一个 `CurrentUserAuthentication`（没有凭据）放进安全上下文。
- 出来时在 finally 里恢复原来的上下文；原来是空的就清空。
- 设置和清理都通过 `SecurityContextHolder.getContextHolderStrategy()`。

定时任务、消息消费需要带身份时用它。这些线程里不调它时，`current()` 得到空结果，审计列留空。它放进去的用户没有断言，所以内部调用不会转发（10.2 节）。

## 13. 自动配置

按「是否依赖 Web 环境」拆成三个，避免一个配置依赖另一个在某些环境下不存在的 Bean。

| 类 | 条件 | 注册的东西 |
|---|---|---|
| `SecurityCoreAutoConfiguration` | 无 | `CurrentUserPort` 的实现 |
| `SecurityForwardingAutoConfiguration` | classpath 上有 `InternalCallInterceptor`（按类名判断）；排在核心配置之后 | `IdentityAssertionForwardingInterceptor` |
| `SecurityAuditingAutoConfiguration` | classpath 上有 `CurrentAuditorProvider`；排在上一个之后 | `CurrentAuditorProvider` 的实现 |
| `SecurityServletAutoConfiguration` | servlet 应用 | 配置属性、`JwtDecoder`（6.3 节，Bean 名 `identityAssertionDecoder`，容器里没有同名 Bean 时；过滤器链按名字注入它，应用里别的 `JwtDecoder` 顶不掉这套校验）、`SecurityProblemWriter`、`SecurityErrorMappingContributor`、安全异常重抛处理器、默认的 `SecurityFilterChain`（容器里没有时） |

前三个不带 Web 条件：安全上下文和 `CurrentUserRunner` 在没有 Web 环境的应用里同样可用，比如只跑定时任务的进程或以非 Web 方式启动的测试。这样审计配置依赖的 `CurrentUserPort` 在任何环境下都存在。

转发拦截器单独一个自动配置类：它实现了 http-interface starter 的接口，没有那个 starter 时连类都加载不了，只有类级别的条件能在加载前挡住。

配置属性类 `PatraSecurityProperties`，前缀 `patra.security`，只有 `identity-assertion.public-keys` 一项（8.3 节）。原来的 `gateway-token` 删除。

验签用的 `Clock`：容器里有 `Clock` Bean 就用它，没有用 `Clock.systemUTC()`。测试可以注入固定时钟来验证过期。

Boot 在检测不到任何认证相关的 Bean 时，会生成一个带随机密码的内存用户并打进日志。下游没有这类 Bean，所以 `SecurityServletAutoConfiguration` 注册一个拒绝所有请求的 `AuthenticationManager` Bean 让它让位。应用里没有用户名密码登录，这个 Bean 不会被任何地方调用。

## 14. 测试支持与测试策略

### 14.1 测试支持（starter 的 `testFixtures`）

| 提供的东西 | 用途 |
|---|---|
| `TestSigningKey` | 本 JVM 第一次用到时生成一对 EC P-256 密钥，之后复用。`signer()` 返回用它构造的签名器，`publicJwkSet()` 返回只含公钥的 JWK Set JSON |
| `TestIdentity` | `user()`、`user(long)` 构造用户；`headers()`、`headers(long)`、`headers(CurrentUser)` 返回一个 `Authorization: Bearer` 头，值是用测试私钥签出的断言 |
| 测试配置 | 环境后处理器 `TestIdentityAssertionEnvironmentPostProcessor` 自动把 `patra.security.identity-assertion.public-keys` 设成 `TestSigningKey.publicJwkSet()`，最低优先级 |
| `spring-boot-security-test` | 作为 `testFixtures` 的 API 依赖带给使用方 |

已登录的请求：把 `TestIdentity` 返回的头加到请求上。匿名的请求：什么都不加。这种写法走的是真实的过滤器和真实的验签，切片测试和整应用测试用法相同，和首版一样。

私钥只在测试进程的内存里，不写文件，不进仓库。

切片测试必须带上 `spring-boot-security-test`。没有它，`@WebMvcTest` 里根本没有安全过滤器，测试会全部通过而什么都没验证。README 里写明切片测试的配置根类要导入 `SecurityCoreAutoConfiguration` 和 `SecurityServletAutoConfiguration`。

### 14.2 测试策略

单元测试，不起 Spring：

- 签名器签出的断言能被验签器接受，`toCurrentUser` 还原出同一个用户；头里的 `alg`、`typ`、`kid` 和载荷里的 `iss`、`aud`、`exp` 都是 6.1 节的值。
- 验签器拒绝：另一把私钥签的；时钟拨到 61 秒后签的；`typ`、`iss`、`aud` 不对的；`alg` 是 `none` 或 `HS256` 的。
- 公钥集合里有两把时，按 `kid` 选中正确的一把；`kid` 对不上时拒绝。
- `toCurrentUser`：`sub`、`sid` 不是正整数，账号或客户端类型不认识，各抛 `MalformedIdentityException`。
- 转换器：第 8.2 节那张表的四行，含 `Authorization` 出现多次、`Basic` 方案、`bearer` 小写。
- 配置属性校验：没配、空白、不是 JSON、没有 `keys`、不是 EC 公钥、缺 `kid`。
- 转发拦截器：有断言的用户带上 `Authorization`；匿名、`CurrentUserRunner` 里的用户、空上下文都不带。
- 密钥生成器的输出：私钥 JWK 能解析且含 `d`，公钥 JWK Set 能解析且不含 `d`，两边 `kid` 相同且等于指纹。
- `CurrentUser` 的构造校验；两个枚举的 `fromCode`。
- `CurrentUserAuthentication` 的 `getName()` 只含用户 ID；`toString()` 不含断言。
- `CurrentUserRunner`：执行中能取到用户；结束后恢复原状；执行中抛异常也恢复。

集成测试，起一个只在测试里存在的小应用，启动类和真实服务一样扫描整个 `dev.linqibin`：

- 第 8.2 节四行各一个用例，401 和 500 的用例断言状态码、`Content-Type` 和响应体字段。
- `require()` 未登录时返回 401，响应体字段与现有错误格式一致。
- 业务代码抛 `FORBIDDEN` 特征的异常时返回 403。
- 控制器里直接抛 `AccessDeniedException`：匿名时 401，已登录时 403。
- 过滤器里输出的错误响应和控制器里输出的错误响应字段集合相同，都有 `instance`。
- 任何请求都不创建 HttpSession；响应里没有 `Set-Cookie`。
- `POST` 请求不被 CSRF 拦截；`/logout` 路径不被框架劫持。
- 认证成功的请求不被重定向。
- 没配公钥时应用启动失败。
- 启动日志里没有生成的随机密码。
- 转发：测试应用里用 `RestClientFactory` 建一个指向自己的内部客户端；已登录的请求经它调自己的另一个接口，对方取到同一个用户；匿名请求调过去对方是匿名。用 `httpInterfaceRestClientBuilder` 建的客户端没有被挂上拦截器。
- 审计列（Testcontainers 起 PostgreSQL）：登录用户写入的记录 `created_by` 是他的用户 ID；匿名写入时为空。
- 没有 starter-jpa、没有 http-interface starter 的应用加上本 starter 能正常启动。
- 两个 starter 都在、以非 Web 方式启动的应用能正常启动，`CurrentUserRunner` 里写入的记录带上用户 ID。这个用例的测试应用只用自动配置，不扫描整个 `dev.linqibin`。

回归：

- http-interface starter：现有测试通过；没有 `InternalCallInterceptor` 实现时客户端行为不变。
- starter-jpa、starter-web：现有测试通过。
- 没引安全 starter 的服务（catalog、ingest、registry、object-storage）classpath 上没有 Spring Security；用它们的依赖清单确认。
- 仓库里搜不到 `X-Patra-`、`gateway-token`、`IdentityHeaders`。

构建层面：

- 对 linqibin-commons 的模块跑 `checkBoundary`。
- `patra-common-security` 的依赖里只有 `linqibin-commons-core`。
- `./gradlew dumpModuleGraph` 后 `module-graph.json` 与构建一致。

## 15. 实测结果

设计阶段的结论来自阅读源码和文档，实现时逐个用测试确认。

| # | 结论 | 结果 | 对应测试 |
|---|---|---|---|
| 1 | `SecurityProblemWriter` 补上 `instance` 之后，输出与全局异常处理器的字段集合一致 | 成立（首版） | `SecurityErrorResponseIT` |
| 2 | 安全异常重抛处理器原样抛出后回到安全过滤器，日志里不留多余的错误记录 | 成立（首版） | `SecurityErrorResponseIT` |
| 3 | 拒绝所有请求的 `AuthenticationManager` Bean 足以让 Boot 不生成随机密码用户 | 成立（首版） | `SecurityServletAutoConfigurationTest`、`StatelessSessionIT` |
| 4 | `STATELESS` 加自定义认证过滤器在 `@WebMvcTest` 加 `RestTestClient` 的切片测试里正常工作 | 成立（首版） | `SecurityWebMvcSliceIT` |
| 5 | `GlobalRestExceptionHandler` 降优先级后，对网关代理失败的异常表现 | 本 Issue 无法实测：网关还是 WebFlux 版，classpath 上没有 starter-web。移交 PAP-69，见第 17 节 | 无 |
| 6 | Nimbus 的 `DefaultJWTProcessor` 默认只接受 `typ` 为 `JWT` 或缺省，自定义 `typ` 要换 `JWSTypeVerifier` 才到得了 Spring 的校验器 | 不成立（评审实测）：Spring 的 `JwkSourceJwtDecoderBuilder` 默认不查 `typ`，自定义值直接到 `JwtTypeValidator`。因此不设 Nimbus 的 `typ` 校验，`typ` 在验签之后才查，缺省 `typ` 同样被拒 | `IdentityAssertionDecodersTest` |
| 7 | `NimbusJwtDecoder.withJwkSource` 配 ES256 时按头里的 `kid` 在集合里选钥，`kid` 对不上时拒绝 | 成立（PAP-70） | `IdentityAssertionDecodersTest` |
| 8 | 转换器抛出的 `BadCredentialsException` 和 `MalformedIdentityException` 都经 `AuthenticationFilter` 的失败处理器到达 `SecurityProblemWriter` | 成立（PAP-70）：401 带 `WWW-Authenticate: Bearer`，500 的字段集合与控制器路径一致 | `IdentityAssertionAuthenticationIT`、`SecurityErrorResponseIT` |
| 9 | 只引 `spring-security-oauth2-jose`、不引 resource server 时，Boot 不会注册任何 OAuth2 相关的过滤器链或自动配置 | 成立（PAP-70）：容器里只有 `patraSecurityFilterChain` 一条链 | `SecurityServletAutoConfigurationTest` |
| 10 | `RestClientFactory` 挂上的拦截器只出现在它创建的客户端上，`httpInterfaceRestClientBuilder` 建的客户端没有 | 成立（PAP-70） | `IdentityAssertionForwardingIT`、`RestClientFactoryTest` |

## 16. README

放在 starter 模块根目录，写七件事：

1. 怎么引入：boot 模块依赖 starter，domain 需要时依赖 `patra-common-security`；只有需要识别用户的服务才引。
2. 要配什么：网关的公钥（`patra.security.identity-assertion.public-keys`，JWK Set JSON）；怎么用生成任务拿到一对密钥。
3. 业务代码怎么取当前用户：注入 `CurrentUserPort`，需要登录时调 `require()`。
4. 定时任务、消息消费里怎么带身份：`CurrentUserRunner`。
5. 服务替用户调用另一个服务：用 http-interface starter 的内部客户端就自动带上断言；事件和任务里用户 ID 当数据传。
6. 测试怎么构造已登录请求，切片测试要导入什么。
7. 信任前提：私钥只在网关；服务不对外暴露；网关转发前删掉外部请求自带的 `Authorization` 头。

「给网关用的部分」一节列出网关复用的类：`StatelessSecurityDefaults.apply`、`CurrentUserAuthentication`、`IdentityAssertionSigner`、`SecurityProblemWriter`，以及生成密钥的任务。

## 17. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 网关自己写过滤器链，调 starter 的无状态默认配置方法，再加：从 `Authorization: Bearer` 取会话令牌、查会话、构造 `CurrentUserAuthentication`（不带凭据） | PAP-65 |
| 无效或过期的会话令牌按匿名处理，不直接 401；是否放行由路径规则决定。查会话时 Redis 不可用则抛 `AuthenticationServiceException`，输出 503 | PAP-65 |
| 网关的路径规则看到的是剥前缀之前的路径，要带服务前缀，如 `/*/_internal/**`。各服务的 actuator 现在也经网关对外转发，一并拦掉 | PAP-65 |
| 匿名请求和已登录请求撞上「全部拒绝」的规则时，框架分别给 401 和 403。`/_internal/**` 要对任何人返回同一个结果，需要单独处理 | PAP-65 |
| 转发前剥掉外部自带的 `Authorization` 头；已登录时用 `IdentityAssertionSigner.sign(user)` 签一个断言，`setBearerAuth` 写入；未登录不写 | PAP-65 |
| 网关自己的配置项放私钥（含私钥的 JWK JSON，建议 `patra.gateway.identity-assertion.private-key`），由 secret 注入；按字符串绑定，构造签名器时再解析（转换失败时 Boot 的失败报告会打印配置值）；用它和容器里的 `Clock` 构造签名器 Bean；没配时启动失败 | PAP-65 |
| 网关也是 servlet 应用，`SecurityServletAutoConfiguration` 照样生效：网关同样要配 `patra.security.identity-assertion.public-keys`（自己那把公钥），不要为了绕过它排除这个自动配置，那会连错误映射和重抛处理器一起丢掉。建议启动时自签一条断言、用 `identityAssertionDecoder` 验一次，提前发现密钥配对或 `kid` 写错 | PAP-65 |
| 会话 ID 是正的 Long | PAP-64 |
| identity 的需登录接口用 `require()` 取当前用户，不自己查会话；dev 配置里给公钥 | PAP-64 |
| 登出在网关上是公开路由，identity 用 `current()`：有当前用户就删会话，没有就直接返回成功 | PAP-64、PAP-65 |
| 密码哈希只需要 `spring-security-crypto`。算法必须支持设计简报定下的整个密码范围（8 到 64 个 Unicode 码点），不能为了迁就算法去缩小范围。BCrypt 对超过 72 字节的输入会抛异常，64 个码点的密码可能超过这个长度，所以直接用它不满足要求。具体选型由 PAP-63 决定 | PAP-63 |
| 用 `generateIdentityAssertionKey -PkeyOut=<路径>` 生成一对密钥；私钥文件的内容注入网关的 `.env.*.secret`，用完删掉文件；标准输出的公钥注入 identity 的配置；换密钥按 6.4 节的顺序，写进 runbook | PAP-66 |
| 网关切到 WebMVC 版并引入 starter-web 之后，实测全局异常处理器（已降一级优先级）对代理失败异常的表现（原第 15 节第 5 条） | PAP-69 |
