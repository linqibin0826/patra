# 安全 starter 工程设计（PAP-62）

> **Issue**：[PAP-62](https://linear.app/papertrace/issue/PAP-62)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)
> **日期**：2026-10-05
> **状态**：已实现

## 1. 要解决的问题

v0.8 引入账号与登录。网关按会话令牌查 Redis 会话，通过后把身份写进请求头转给下游（release spec 决策 B、C）。下游服务需要一个统一的办法得到「当前用户」，并在需要登录的地方给出一致的 401。以后的 admin 后台还要在此之上做角色与权限。

本设计交付这个办法：一份纯 Java 的当前用户抽象，加一个基于 Spring Security 的 servlet starter，以及 linqibin-commons 里两处配套修改。

**使用方**：本版是 identity 和 gateway。identity 用它从请求头得到当前用户；gateway 用它里面的认证对象、请求头约定和错误输出，自己再加查会话与路径规则（PAP-65）。

**完成标准**：PAP-62 的 Acceptance Criteria。

## 2. 范围

做：

- 当前用户的抽象与端口，domain 可以依赖。
- 下游的 Spring Security 默认配置：无会话，从网关请求头建立认证。
- 401 / 403 接到现有的统一错误格式。
- JPA 审计列自动填当前用户 ID。
- 「以某个用户的身份执行」的入口，给定时任务和消息消费用。
- 测试支持与 README。

不做：

- 查会话、续期、路径规则、往下游写身份头。这些在网关里，属于 PAP-65。本设计只定它们用到的契约和可复用的类。
- 会话的写入与删除、登录记录、密码哈希的选型。属于 PAP-63、PAP-64。
- 角色与方法级权限注解。本版没有角色，`@EnableMethodSecurity` 不开；认证对象里的权限列表留空，做 admin 时往里加。
- 服务替用户调用另一个服务时传递身份。本版没有这种调用。
- 错误响应里的 traceId。现有错误响应本来就不带，这是全局的可观测性缺口，与本模块无关。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 会话用不透明令牌 + 自建的 Redis 会话；下游信任网关传来的身份头 | release spec 决策 B、C |
| 认证框架用 Spring Security，不用 Spring Session，不用 Sa-Token | release spec 决策 I |
| 全仓库只有 servlet 一套，网关切到 Spring Cloud Gateway 的 WebMVC 版 | release spec 决策 H，PAP-69 |
| 模块是 Patra 的身份约定，放 `patra-starters`，不进通用库 linqibin-commons | 设计过程 |
| linqibin-commons 已与 super-nb 分离，只为 Patra 服务，可以直接改 | main 上的 `6d538cf83` |
| 用户 ID 是雪花 Long；账号分前台用户和后台账号两类 | release spec 决策 A、D |
| 身份头没有签名，安全性依赖服务不对外暴露和内部令牌不泄露 | release spec 决策 C 的已知代价 |

版本：Spring Boot 4.0.8、Spring Security 7.0.7、Spring Cloud Gateway 5.0.3。下文的类名和默认行为都对着这几个版本的源码核对过；没有实际运行验证的点单列在第 14 节。

## 4. 模块

新增两个模块，改动两个现有模块。

| 模块 | Gradle 路径 | 依赖 |
|---|---|---|
| `patra-common-security`（新） | `:patra-api:patra-common:patra-common-security` | `linqibin-commons-core` |
| `patra-spring-boot-starter-security`（新） | `:patra-starters:patra-spring-boot-starter-security` | `patra-common-security`、`linqibin-spring-boot-starter-web`、`spring-boot-starter-security`；`linqibin-spring-boot-starter-jpa` 为 `compileOnly` |
| `linqibin-spring-boot-starter-jpa`（改） | 已有 | 不变 |
| `linqibin-spring-boot-starter-web`（改） | 已有 | 不变 |

依赖方向：

```
某服务的 domain ──▶ patra-common-security ──▶ linqibin-commons-core
某服务的 boot  ──▶ patra-spring-boot-starter-security ──▶ patra-common-security
                                                    ├──▶ linqibin-spring-boot-starter-web
                                                    └──▶ spring-boot-starter-security
```

两条硬约束：

- **Spring Security 只经安全 starter 进入 classpath。** 不把它加进 starter-web 或任何公共模块。它一进 classpath，Boot 就给所有请求加上「必须登录」的默认规则；本版只有 identity 和 gateway 引安全 starter。
- **linqibin-commons 的模块不依赖这两个新模块。** 现有的 `checkBoundary` 任务验证。

构建接入：`settings.gradle.kts` 加两条 `includeAt`；重新生成 `patra-infra/cd/module-graph.json`。两个新模块都落在现有的 `foundation` 单元。

## 5. 当前用户的抽象（`patra-common-security`）

包名 `dev.linqibin.patra.common.security`。纯 Java，不依赖 Spring。

| 类型 | 内容 |
|---|---|
| `AccountType` | 枚举。本版只有 `USER`（前台用户），字符串 `user`。`fromCode(String)` 对不认识的值返回空结果 |
| `ClientType` | 枚举。本版只有 `WEB`，字符串 `web`。同样有 `fromCode` |
| `CurrentUser` | 记录类型：`userId`（long）、`sessionId`（long）、`accountType`、`clientType`。静态工厂 `of(...)`；紧凑构造器校验两个 ID 为正数、两个枚举非空 |
| `CurrentUserPort` | `Optional<CurrentUser> current()`；`default CurrentUser require()`，没有当前用户时抛 `AuthenticationRequiredException` |
| `AuthenticationRequiredException` | 继承 `DomainException`，带 `StandardErrorTrait.UNAUTHORIZED`。消息是固定的英文短句 |

`CurrentUser` 不放邮箱等个人信息。会话 ID 放进来，是因为 identity 登出时要靠它定位删哪条会话。

现有的错误解析引擎会把带 `UNAUTHORIZED` 特征的异常解析成 `{PREFIX}-0401`，不需要新增映射。

## 6. 请求头约定

| 请求头 | 内容 |
|---|---|
| `X-Patra-User-Id` | 用户 ID，十进制字符串 |
| `X-Patra-Session-Id` | 会话 ID，十进制字符串 |
| `X-Patra-Account-Type` | `AccountType` 的字符串 |
| `X-Patra-Client-Type` | `ClientType` 的字符串 |
| `X-Patra-Gateway-Token` | 内部令牌 |

约定集中在 starter 的 `IdentityHeaders` 类里：

- 五个请求头的名字常量。
- `parse`：从一组请求头得到三种结果之一：当前用户、没有身份、格式不合法。
- `write`：把一个 `CurrentUser` 写成四个身份头。写入是覆盖，不是追加。

五个头都是单值。同一个头出现多次按格式不合法处理。

网关和下游用同一个类读写。

## 7. 认证对象

`CurrentUserAuthentication`，继承 Spring Security 的 `AbstractAuthenticationToken`：

- 主体（principal）是 `CurrentUser`。
- 凭据（credentials）为 null。
- 权限列表本版为空。做 admin 时由建立认证的一方把角色填进来，这个类不用改结构。
- `getName()` 返回用户 ID 的字符串。默认实现会把整个记录打进日志，所以要重写。
- 构造出来就是已认证状态。

网关和下游都用这一个类型：网关查到会话后构造它，下游从请求头解析后构造它。

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

「无会话、关 CSRF、关登出、指定入口点和拒绝处理器」这一组抽成一个公开的静态方法。网关写自己的过滤器链时调用同一个方法，再加自己的规则。

下游全部放行的含义：需要登录的接口由业务代码调 `require()` 来保证，不靠路径规则。这样 `/_internal/**` 这类服务之间直连的接口不受影响，它们不带网关的内部令牌。

### 8.2 从请求头建立认证

用 Spring Security 的通用认证过滤器 `AuthenticationFilter`，加一个自己的转换器 `GatewayHeaderAuthenticationConverter`。

| 收到的请求 | 转换器的结果 | 最终 |
|---|---|---|
| 内部令牌正确，四个身份头齐全且合法 | `CurrentUserAuthentication` | 已登录 |
| 内部令牌正确，四个身份头都没有 | null | 匿名，继续 |
| 内部令牌缺失或不对 | null；请求里带了任何身份头时记一条警告日志 | 匿名，继续 |
| 内部令牌正确，身份头只有一部分或格式不合法 | 抛 `MalformedIdentityException` | `{PREFIX}-0500` |

格式不合法指：用户 ID 或会话 ID 不是正整数，账号类型或客户端类型不认识，或某个头出现多次。内部令牌头出现多次按令牌不对处理。

第四种只可能是网关自己的缺陷，所以按服务端错误处理并记错误日志，不伪装成 401。

过滤器的几处必须改掉的默认行为：

- **成功处理器换成空实现。** 默认的会先回一个 302 重定向，然后还继续执行过滤器链。
- **失败处理器换成自己的。** 默认的遇到服务类异常会原样抛出，变成容器的 500 页面。
- **过滤器不声明成 Bean**，在配置过滤器链时直接 new。声明成 Bean 的话 Boot 会把它再注册成全局过滤器，在别的链上也执行。

`AuthenticationFilter` 需要一个认证管理器。下游用的是直通实现：转换器给出的已经是认证完成的对象，原样返回。

### 8.3 内部令牌

- 配置项 `patra.security.gateway-token`，由部署时的 secret 注入（PAP-66）。
- 比较用 `MessageDigest.isEqual`，恒定时间。
- 配置项为空或只有空白时应用启动失败，错误信息指明缺哪个配置项。
- 日志里不输出令牌的值。

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
| 其他 `AuthenticationException` | `{PREFIX}-0401` |
| `AccessDeniedException` | `{PREFIX}-0403` |

输出给客户端的 `detail` 是固定短句，不带框架异常的原始消息。原始原因只进日志。

### 9.3 控制器里抛出的安全异常

`GlobalRestExceptionHandler` 以最高优先级兜住所有异常。控制器或方法注解里抛出的 Spring Security 异常会被它接走，到不了框架自己的处理器，而只有框架知道当前是匿名还是已登录，才能正确区分 401 和 403。

修法分两处：

- **starter-web**：`GlobalRestExceptionHandler` 的优先级从最高降一级。
- **安全 starter**：新增一个最高优先级的异常处理器，只接 `AccessDeniedException` 和 `AuthenticationException`，把它们原样抛出。原样抛出的异常 Spring MVC 当作未处理，会回到安全过滤器，由 9.1 的写出器输出。

本版不开方法级权限注解，这条路径暂时没有业务代码触发，但机制现在就做好并用测试覆盖，做 admin 时不用回头改异常处理。

## 10. JPA 审计列

`created_by`、`updated_by` 由 starter-jpa 里的 `auditorAware` Bean 决定填什么。它现在永远返回空。

改法是让它去问当前操作人是谁，而不是把它整个换掉：

- **starter-jpa**：新增接口 `CurrentAuditorProvider`，一个方法，返回当前操作人的 ID，可以为空。默认的 `auditorAware` 改成：容器里有这个接口的实现就问它，没有就返回空。Bean 的名字和「别人没提供才生效」的条件都不变。
- **安全 starter**：提供 `CurrentAuditorProvider` 的实现，从 `CurrentUserPort` 取用户 ID。classpath 上有 starter-jpa 时才注册。

效果：

- 引了安全 starter 的服务，登录用户写入的记录自动带上他的用户 ID，不需要开关。
- 没引安全 starter 的服务行为不变，两列仍然是空。
- 匿名请求、不在请求里的线程，两列是空。

不用「安全 starter 提供一个 `auditorAware` 把默认的替换掉」的做法：各服务的启动类扫描整个 `dev.linqibin`，starter-jpa 的审计配置类会被提前扫到，默认的那个总是先注册，替换不掉，而且不报错。

## 11. 以某个用户的身份执行

`CurrentUserRunner`，两个静态方法：`runAs(CurrentUser, Runnable)` 和 `callAs(CurrentUser, Supplier<T>)`。

- 进去时把一个 `CurrentUserAuthentication` 放进安全上下文。
- 出来时在 finally 里恢复原来的上下文；原来是空的就清空。
- 设置和清理都通过 `SecurityContextHolder.getContextHolderStrategy()`。

定时任务、消息消费需要带身份时用它。这些线程里不调它时，`current()` 得到空结果，审计列留空。

本版没有业务代码用到它。现在就提供，是因为测试支持和以后的任务代码需要同一个入口，清理逻辑只写一处。

## 12. 自动配置

按「是否依赖 Web 环境」拆成三个，避免一个配置依赖另一个在某些环境下不存在的 Bean。

| 类 | 条件 | 注册的东西 |
|---|---|---|
| `SecurityCoreAutoConfiguration` | 无 | `CurrentUserPort` 的实现 |
| `SecurityAuditingAutoConfiguration` | classpath 上有 `CurrentAuditorProvider`；排在上一个之后 | `CurrentAuditorProvider` 的实现 |
| `SecurityServletAutoConfiguration` | servlet 应用 | 配置属性、`SecurityProblemWriter`、`SecurityErrorMappingContributor`、安全异常重抛处理器、默认的 `SecurityFilterChain`（容器里没有时） |

前两个不带 Web 条件：安全上下文和 `CurrentUserRunner` 在没有 Web 环境的应用里同样可用，比如只跑定时任务的进程或以非 Web 方式启动的测试。这样审计配置依赖的 `CurrentUserPort` 在任何环境下都存在。

配置属性类 `PatraSecurityProperties`，前缀 `patra.security`，只有一个配置项：`gateway-token`。它只在 servlet 应用里必填，因为只有从请求头建立认证时才用到。

Boot 在检测不到任何认证相关的 Bean 时，会生成一个带随机密码的内存用户并打进日志。下游没有这类 Bean，所以 `SecurityServletAutoConfiguration` 注册一个拒绝所有请求的 `AuthenticationManager` Bean 让它让位。应用里没有用户名密码登录，这个 Bean 不会被任何地方调用。

## 13. 测试支持与测试策略

### 13.1 测试支持（starter 的 `testFixtures`）

| 提供的东西 | 用途 |
|---|---|
| `TestIdentity` | 返回一组请求头（四个身份头 + 测试用的内部令牌），可指定各字段，都有默认值 |
| 测试配置 | 自动把 `patra.security.gateway-token` 设成 `TestIdentity` 用的那个值 |
| `spring-boot-security-test` | 作为 `testFixtures` 的 API 依赖带给使用方 |

已登录的请求：把 `TestIdentity` 返回的头加到请求上。匿名的请求：什么都不加。这种写法走的是真实的过滤器，切片测试和整应用测试用法相同。

切片测试必须带上 `spring-boot-security-test`。没有它，`@WebMvcTest` 里根本没有安全过滤器，测试会全部通过而什么都没验证。README 里写明切片测试的配置根类要导入 `SecurityCoreAutoConfiguration` 和 `SecurityServletAutoConfiguration`。

### 13.2 测试策略

单元测试，不起 Spring：

- `IdentityHeaders.parse` 的各种输入；`write` 的结果能被 `parse` 还原，已有的同名头被覆盖。
- 转换器：第 8.2 节那张表的四行。
- 内部令牌比较：正确、不对、缺失、出现多次。
- `CurrentUser` 的构造校验；两个枚举的 `fromCode`。
- `CurrentUserAuthentication` 的 `getName()` 只含用户 ID。
- `CurrentUserRunner`：执行中能取到用户；结束后恢复原状；执行中抛异常也恢复。

集成测试，起一个只在测试里存在的小应用，启动类和真实服务一样扫描整个 `dev.linqibin`：

- 第 8.2 节四行各一个用例，第四行断言状态码、`Content-Type` 和响应体字段。
- `require()` 未登录时返回 401，响应体字段与现有错误格式一致。
- 业务代码抛 `FORBIDDEN` 特征的异常时返回 403。
- 控制器里直接抛 `AccessDeniedException`：匿名时 401，已登录时 403。
- 过滤器里输出的错误响应和控制器里输出的错误响应字段集合相同，都有 `instance`。
- 任何请求都不创建 HttpSession；响应里没有 `Set-Cookie`。
- `POST` 请求不被 CSRF 拦截；`/logout` 路径不被框架劫持。
- 认证成功的请求不被重定向。
- 没配内部令牌时应用启动失败。
- 启动日志里没有生成的随机密码。
- 审计列（Testcontainers 起 PostgreSQL）：登录用户写入的记录 `created_by` 是他的用户 ID；匿名写入时为空。
- 没有 starter-jpa 的应用加上本 starter 能正常启动。
- 两个 starter 都在、以非 Web 方式启动的应用能正常启动，`CurrentUserRunner` 里写入的记录带上用户 ID。这个用例的测试应用只用自动配置，不扫描整个 `dev.linqibin`：starter-web 的全局异常处理器会被组件扫描注册，而它依赖的 Bean 只在 servlet 环境下才有，所以大范围扫描的应用本来就不能以非 Web 方式启动，这与本模块无关。

回归：

- starter-jpa：没有 `CurrentAuditorProvider` 实现时两列为空，时间列照常填充。
- starter-web：`GlobalRestExceptionHandler` 降一级优先级后，现有服务的集成测试全部通过。
- 没引安全 starter 的服务（catalog、ingest、registry、object-storage）classpath 上没有 Spring Security；用它们的依赖清单确认。

构建层面：

- 对 linqibin-commons 的模块跑 `checkBoundary`。
- `patra-common-security` 的依赖里只有 `linqibin-commons-core`。
- `./gradlew dumpModuleGraph` 后 `module-graph.json` 与构建一致。

## 14. 实测结果

设计阶段有五个结论来自阅读源码，实现时逐个用测试确认。

| # | 结论 | 结果 | 对应测试 |
|---|---|---|---|
| 1 | `SecurityProblemWriter` 补上 `instance` 之后，输出与全局异常处理器的字段集合一致 | 成立 | `SecurityErrorResponseIT` |
| 2 | 安全异常重抛处理器原样抛出后回到安全过滤器，日志里不留多余的错误记录 | 成立 | `SecurityErrorResponseIT` |
| 3 | 拒绝所有请求的 `AuthenticationManager` Bean 足以让 Boot 不生成随机密码用户 | 成立 | `SecurityServletAutoConfigurationTest`、`StatelessSessionIT` |
| 4 | `STATELESS` 加自定义认证过滤器在 `@WebMvcTest` 加 `RestTestClient` 的切片测试里正常工作 | 成立 | `SecurityWebMvcSliceIT` |
| 5 | `GlobalRestExceptionHandler` 降优先级后，对网关代理失败的异常表现 | 本 Issue 无法实测：网关还是 WebFlux 版，classpath 上没有 starter-web。移交 PAP-69，见第 16 节 | 无 |

## 15. README

放在 starter 模块根目录，写六件事：

1. 怎么引入：boot 模块依赖 starter，domain 需要时依赖 `patra-common-security`；只有需要识别用户的服务才引。
2. 要配什么：内部令牌。
3. 业务代码怎么取当前用户：注入 `CurrentUserPort`，需要登录时调 `require()`。
4. 定时任务、消息消费里怎么带身份：`CurrentUserRunner`。
5. 测试怎么构造已登录请求，切片测试要导入什么。
6. 信任前提：身份头没有签名，安全性依赖服务不对外暴露、内部令牌不泄露，以及网关转发前删掉外部请求自带的身份头和内部令牌头。

## 16. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 网关自己写过滤器链，调 starter 的无状态默认配置方法，再加：从 `Authorization: Bearer` 取令牌、查会话、构造 `CurrentUserAuthentication` | PAP-65 |
| 无效或过期的令牌按匿名处理，不直接 401；是否放行由路径规则决定。查会话时 Redis 不可用则抛 `AuthenticationServiceException`，输出 503 | PAP-65 |
| 网关的路径规则看到的是剥前缀之前的路径，要带服务前缀，如 `/*/_internal/**`。各服务的 actuator 现在也经网关对外转发，一并拦掉 | PAP-65 |
| 匿名请求和已登录请求撞上「全部拒绝」的规则时，框架分别给 401 和 403。`/_internal/**` 要对任何人返回同一个结果，需要单独处理 | PAP-65 |
| 转发前剥掉外部自带的五个身份头和 `Authorization` 头，再用 `IdentityHeaders.write` 写入；已登录写五个，未登录只写内部令牌 | PAP-65 |
| 会话 ID 是正的 Long | PAP-64 |
| identity 的需登录接口用 `require()` 取当前用户，不自己查会话 | PAP-64 |
| 登出在网关上是公开路由，identity 用 `current()`：有当前用户就删会话，没有就直接返回成功 | PAP-64、PAP-65 |
| 密码哈希只需要 `spring-security-crypto`。算法必须支持设计简报定下的整个密码范围（8 到 64 个 Unicode 码点），不能为了迁就算法去缩小范围。BCrypt 对超过 72 字节的输入会抛异常，64 个码点的密码可能超过这个长度，所以直接用它不满足要求。具体选型由 PAP-63 决定 | PAP-63 |
| 内部令牌由 secret 注入，网关和 identity 拿到同一个值 | PAP-66 |
| 网关切到 WebMVC 版并引入 starter-web 之后，实测全局异常处理器（已降一级优先级）对代理失败异常的表现（原第 14 节第 5 条） | PAP-69 |
