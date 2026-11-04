# identity 第三方登录工程设计（v0.19 后端）

> **状态**：实施中（2026-11-04，分支 `feat/v0.19-social-login-api`）
> **Issue**：PAP-180、PAP-181、PAP-182、PAP-183、PAP-184（Project P-PAP-16）
> **产品决策**：[`release-specs/v0.19-social-login.md`](../release-specs/v0.19-social-login.md) 第 4 节，本文只写落地细节，不重新讨论
> **改写**：`2026-10-20-identity-account-rebuild-design.md` 第 12.1 节（表结构）；`2026-10-22-identity-account-security-design.md` 的 `auth_at` 必填约定；v0.12 的两条决策（第 13 节）
> **版本依据**：Spring Boot 4.1.2、Spring Security 7.1.2，下面提到的类名与行为都对照过这两个版本的源码

## 1. 全景

```text
浏览器            门户（BFF）                   网关            identity                     平台
  │ 点「用 GitHub 登录」│                          │                │                           │
  ├──GET start────────▶│──GET /oauth2/authorization/github────────▶│ 存授权请求进 Redis         │
  │                    │◀────────────302 Location: 平台─────────────┤                           │
  │◀─302 平台+state Cookie                        │                │                           │
  ├──用户在平台上授权──────────────────────────────────────────────────────────────────────────▶│
  │◀─302 门户回调?code&state─────────────────────────────────────────────────────────────────── ┤
  ├──GET callback─────▶│ 核对 state Cookie        │                │                           │
  │                    │──GET /login/oauth2/code/github?code&state▶│ 取出并删除授权请求         │
  │                    │                          │                │──换令牌、验 id_token / 查资料▶│
  │                    │◀───────200 三种结果之一 / 错误──────────────┤ 丢弃令牌                  │
  │◀─写 Cookie，302 落地页                        │                │                           │
```

- 浏览器只和门户打交道。Cookie 只由门户写。
- 令牌只在 `OAuth2LoginAuthenticationFilter` 的一次请求里活着，不进 Redis、数据库、日志。
- 回调和发起都经 `/patra-identity/**`，网关不解析协议，只放行。

## 2. 给门户的接口契约

路径是 identity 上的路径，门户经网关时加前缀 `/patra-identity`。`{provider}` 只有 `google`、`github`、`orcid`，路径和 JSON 都用这三个小写值（数据库里是大写枚举）。

### 2.1 发起登录

```text
GET /oauth2/authorization/{provider}    公开
```

- 成功：`302`，`Location` 是平台的授权地址，已带 `state`、`client_id`、`redirect_uri`、`scope`，OIDC 平台带 `nonce`，开了 PKCE 的带 `code_challenge`。
- 不认识或这一家还没配的 `{provider}`：`404`。
- Redis 不可用：`500`（框架默认行为，门户只判「不是 302」）。

门户要做：

1. `redirect: "manual"` 请求它。
2. 从 `Location` 的查询串取 `state`，写 Cookie `patra_oauth_state`（`HttpOnly`、`SameSite=Lax`、线上 `Secure`、`Path=/api/auth/oauth`、`Max-Age=600`）。
3. 把 `Location` 原样 302 给浏览器。

### 2.2 回调

```text
GET /login/oauth2/code/{provider}?<平台给的查询串原样转发>    公开
```

门户先核对：查询串的 `state` 必须等于 Cookie `patra_oauth_state`，缺 Cookie 或不相等都不调 identity，直接显示失败。核对后清掉这个 Cookie。

转发时带这些头：

| 头 | 值 | 说明 |
|---|---|---|
| `X-Forwarded-For` | 浏览器 IP | `gatewayFetch` 的 `client` 已有 |
| `User-Agent` | 浏览器原值 | 同上 |
| `X-Patra-Device-Id` | 设备标识 Cookie 的值 | 可选。格式不对按没带处理，不报错（授权码已经用掉了，不能因此让用户重来） |
| `Authorization: Bearer <会话令牌>` | 浏览器当前的会话 | 有会话 Cookie 就带。绑定流程必须有，登录流程忽略 |

成功都是 `200`，用 `result` 区分。

**老用户，已发会话：**

```json
{
  "result": "AUTHENTICATED",
  "sessionToken": "patra_user_...",
  "user": { "userId": "352303128713027974", "...": "与 /auth/me 同构，含 hasPassword" }
}
```

门户写会话 Cookie。`user.status` 为 `PENDING_DELETION` 时和密码登录一样进取消注销页。

**新用户，要补填：**

```json
{
  "result": "REGISTRATION_REQUIRED",
  "ticket": "43 个 base64url 字符",
  "provider": "github",
  "accountLabel": "octocat",
  "suggestedUsername": "octocat",
  "suggestedEmail": null,
  "expiresAt": "2026-11-04T09:30:00Z"
}
```

门户把 `ticket` 写 Cookie `patra_oauth_ticket`（`HttpOnly`、`SameSite=Lax`、线上 `Secure`、`Path=/`、`Max-Age=1800`），302 到补填页。`ticket` 不进 URL。

**绑定成功：**

```json
{ "result": "LINKED", "provider": "orcid", "accountLabel": "0000-0002-1825-0097" }
```

门户 302 回设置页。

**回调的错误**（`application/problem+json`，门户清掉相关 Cookie，跳回发起页显示文案）：

| 场景 | 状态 | `code` | `traits` |
|---|---|---|---|
| 用户在平台上点了拒绝 | 422 | `IDN-0422` | `RULE_VIOLATION`、`OAUTH_CANCELLED` |
| `state` 不存在或已用过、`id_token` 签名 / nonce / 受众 / 签发方不对、平台拒绝授权码 | 422 | `IDN-0422` | `RULE_VIOLATION`、`OAUTH_REJECTED` |
| 封禁账号登录 | 403 | `IDN-0403` | `FORBIDDEN` |
| 绑定：回调时没带会话或会话已失效 | 401 | `IDN-0401` | |
| 绑定：当前用户或会话与发起时不一致 | 403 | `IDN-0403` | `FORBIDDEN`、`LINK_SESSION_MISMATCH` |
| 绑定：这个第三方账号已绑给别的用户 | 409 | `IDN-0409` | `CONFLICT`、`PROVIDER_ACCOUNT_TAKEN` |
| 绑定：当前账号已绑了这一家的另一个账号 | 409 | `IDN-0409` | `CONFLICT`、`PROVIDER_ALREADY_LINKED` |
| 连不上平台（换令牌、取公钥、取资料）；Redis 不可用 | 503 | `IDN-0503` | `DEP_UNAVAILABLE` |

绑定时重复绑定同一个第三方账号到自己，幂等返回 `LINKED`，不发邮件。

### 2.3 首次登录注册

三个接口都公开，都带请求头 `X-Patra-Registration-Ticket: <Cookie patra_oauth_ticket 的值>`。头缺失、记录过期或已用完，一律 `422 IDN-0422`，`errors[{ field: "ticket", reason: "REQUIRED" | "INVALID" }]`，门户让用户回登录页重走。

**取补填页要显示的内容：**

```text
GET /auth/oauth/registration
```

```json
{
  "provider": "github",
  "accountLabel": "octocat",
  "suggestedUsername": "octocat",
  "suggestedEmail": null,
  "expiresAt": "2026-11-04T09:30:00Z",
  "pendingCode": { "username": "chen-yu", "email": "chen@example.com", "codeExpiresAt": "...", "nextResendAt": "..." }
}
```

`pendingCode` 没发过码或已失效时为 `null`；有值时刷新页面可以直接回到输验证码这一步，重发要提交的用户名和邮箱都在里面。

**提交用户名、邮箱并发验证码（重发也调它）：**

```text
POST /auth/oauth/registration/code
{ "username": "chen-yu", "email": "chen@example.com" }
```

成功 `202`：

```json
{ "email": "chen@example.com", "codeExpiresAt": "2026-11-04T09:20:00Z", "nextResendAt": "2026-11-04T09:11:00Z" }
```

| 场景 | 状态 | 内容 |
|---|---|---|
| 字段不合法 | 422 | `errors[]`，原因码同注册：`email` / `username` 的现有原因码 |
| 邮箱已注册 | 409 | `IDN-0409`，`errors[{ email, ALREADY_REGISTERED }]`；门户提示「先用原方式登录，再到设置页绑定」。不发码、不建账号 |
| 用户名被占用（含别人保留期内的旧名） | 409 | `IDN-0409`，`errors[{ username, ALREADY_TAKEN }]` |
| 发送太频繁（同邮箱 60 秒冷却、每小时 5 封；同 IP 每小时 20 封） | 429 | `IDN-0429`，`traits` 含 `QUOTA_EXCEEDED`，`Retry-After`、`retryAfterSeconds` |

验证码 6 位数字，有效 10 分钟，重发后旧码作废。码绑定提交时的用户名和邮箱，下一步不能换。

**提交验证码，建账号：**

```text
POST /auth/oauth/registration/complete
{ "code": "483920", "clientType": "web", "deviceId": "..." }
```

`clientType`、`deviceId` 同登录。成功 `201`，和注册同构：`{ "sessionToken": "...", "user": { ... } }`。门户写会话 Cookie，清 `patra_oauth_ticket`。

| 场景 | 状态 | 内容 |
|---|---|---|
| 验证码为空 / 不是 6 位数字 | 422 | `errors[{ code, REQUIRED \| INVALID_FORMAT }]` |
| 验证码不对 | 422 | `errors[{ code, INVALID }]` |
| 没有可用的验证码：过期、已用、错满 5 次 | 422 | `errors[{ code, EXPIRED }]`，门户提示重新发送 |
| 并发下邮箱或用户名被别人抢先 | 409 | 同上一步的 `errors[]`，门户回到表单。验证码已用掉，要重新发 |
| 同一个第三方账号已注册（两个标签页并发） | 409 | `IDN-0409`，`PROVIDER_ACCOUNT_TAKEN` |

结果：`email_verified_at` 有值，没有密码凭据（`hasPassword: false`），会话不开放再认证窗口。

### 2.4 设置页登录方式

三个接口都在 `/me/**`，需要登录。

**列表：**

```text
GET /me/login-methods
```

```json
{
  "hasPassword": true,
  "linked": [
    { "provider": "github", "accountLabel": "octocat", "linkedAt": "...", "lastLoginAt": null }
  ]
}
```

`accountLabel`：Google 是邮箱，GitHub 是 `login`，ORCID 是 iD。没绑的平台不在 `linked` 里，门户按三家画卡片。

**发起绑定：**

```text
POST /me/login-methods/{provider}/link    无请求体
```

成功 `200`：`{ "authorizationUrl": "https://github.com/login/oauth/authorize?...&state=..." }`。门户和 2.1 一样从 URL 取 `state` 写 Cookie，把 `authorizationUrl` 还给浏览器，由前端 `location.assign`。用 POST 而不走 2.1：`REAUTH_REQUIRED` 要以 JSON 回给弹框。

检查按这个顺序：

| 场景 | 状态 | `traits` |
|---|---|---|
| 不认识或还没配的 `{provider}` | 404 | |
| 账号注销中 | 409 | `CONFLICT`、`PENDING_DELETION` |
| 邮箱未验证 | 409 | `CONFLICT`、`EMAIL_NOT_VERIFIED` |
| 这一家已经绑过 | 409 | `CONFLICT`、`PROVIDER_ALREADY_LINKED` |
| 超过再认证窗口，或第三方登录没有密码证明 | 403 | `FORBIDDEN`、`REAUTH_REQUIRED` |

**解绑：**

```text
DELETE /me/login-methods/{provider}
```

成功 `204`，本来就没绑也是 `204`（不发邮件）。

| 场景 | 状态 | `traits` |
|---|---|---|
| 没有密码且只剩这一种登录方式 | 409 | `CONFLICT`、`LAST_LOGIN_METHOD` |
| 账号注销中 | 409 | `CONFLICT`、`PENDING_DELETION` |
| 超过再认证窗口，或没有密码证明 | 403 | `FORBIDDEN`、`REAUTH_REQUIRED` |

`LAST_LOGIN_METHOD` 排在再认证之前。没有密码的人永远过不了再认证，放在后面这条规则就碰不到了，界面也会把用户错引去设置密码。

### 2.5 `/auth/me` 增加 `hasPassword`

`user` 结构（`/auth/me`，登录、注册、再认证、改密码、取消注销响应里的 `user`，回调的 `AUTHENTICATED`）多一个布尔字段 `hasPassword`。

门户拿到 `REAUTH_REQUIRED` 时：

- `hasPassword: true`：弹密码框，`POST /auth/reauth` 后重试。
- `hasPassword: false`：提示先设置密码，走现有 `POST /auth/password/forgot`。重设处理器没有凭据行时会新建。设好后所有设备下线，重新登录一次。

### 2.6 网关放行

`GatewaySecurityConfiguration.IDENTITY_PUBLIC_PATHS` 增加：

```text
/patra-identity/oauth2/authorization/*
/patra-identity/login/oauth2/code/*
/patra-identity/auth/oauth/registration
/patra-identity/auth/oauth/registration/**
```

`/me/login-methods/**` 落在现有的「其余需要登录」里，不用改。放行的路径带会话时网关照样翻译成断言，绑定回调靠这一点。

## 3. 回调地址（每个环境要在三家后台填的）

门户路由定为 `/api/auth/oauth/callback/{provider}`，和现有 `/api/auth/*` 一致；发起路由是 `/api/auth/oauth/{provider}/start`（门户内部，不用注册）。

| 平台 | 环境 | 回调地址 | 应用 |
|---|---|---|---|
| Google | 本机 | `http://localhost:4000/api/auth/oauth/callback/google` | 一个 OAuth 客户端可以填多个地址，也可以分开建；按决策 H 各建一套 |
| Google | 线上 | `https://patra.linqibin.dev/api/auth/oauth/callback/google` | |
| GitHub | 本机 | `http://localhost:4000/api/auth/oauth/callback/github` | OAuth App，只能填一个，所以要两个应用 |
| GitHub | 线上 | `https://patra.linqibin.dev/api/auth/oauth/callback/github` | |
| ORCID 沙箱 | 本机 | `http://localhost:4000/api/auth/oauth/callback/orcid` | `sandbox.orcid.org` 的公共 API 客户端。若沙箱不收 `http`，PAP-181 记下实际可用的写法 |
| ORCID 正式 | 线上 | `https://patra.linqibin.dev/api/auth/oauth/callback/orcid` | `orcid.org` 的公共 API 客户端（非商业限制见 release spec） |

## 4. 配置与密钥

注册信息写在 jar 里的 `application.yml`：`patra.identity.oauth.providers.<id>`。

`~/.patra/secrets/oauth.env` 只放密钥（本机由启动器读，mini 经 compose 的 `env_file` 注入，值不进仓库和对话）：

```bash
PATRA_OAUTH_GOOGLE_CLIENT_ID=
PATRA_OAUTH_GOOGLE_CLIENT_SECRET=
PATRA_OAUTH_GITHUB_CLIENT_ID=
PATRA_OAUTH_GITHUB_CLIENT_SECRET=
PATRA_OAUTH_ORCID_CLIENT_ID=
PATRA_OAUTH_ORCID_CLIENT_SECRET=
```

随环境变的非密钥值：本机走默认值，mini 写在 `docker-compose.apps.yaml` 的 `environment`：

| 变量 | 默认（本机） | mini |
|---|---|---|
| `PATRA_OAUTH_REDIRECT_BASE_URL` | `http://localhost:4000` | `https://patra.linqibin.dev` |
| `PATRA_OAUTH_ORCID_BASE_URL` | `https://sandbox.orcid.org` | `https://orcid.org` |
| `PATRA_OAUTH_ORCID_REQUIRE_PKCE` | `false` | 同默认，PAP-181 实测后定 |

**某一家暂缺时**：客户端 ID 或密钥为空的平台不注册，启动记一条 `WARN`，它的 `/oauth2/authorization/{provider}` 和 `POST /me/login-methods/{provider}/link` 回 404，其余平台和 identity 照常。一家都没配也能启动（MacBook 还没放这个文件时）。所以 `oauth.env` 在 compose 里是 `required: false`。

每家的注册信息：

| | Google | GitHub | ORCID |
|---|---|---|---|
| scope | `openid email` | `read:user` | `openid` |
| 流程 | OIDC | 纯 OAuth2 | OIDC |
| 授权 | `https://accounts.google.com/o/oauth2/v2/auth` | `https://github.com/login/oauth/authorize` | `${ORCID}/oauth/authorize` |
| 令牌 | `https://oauth2.googleapis.com/token` | `https://github.com/login/oauth/access_token` | `${ORCID}/oauth/token` |
| 公钥 | `https://www.googleapis.com/oauth2/v3/certs` | | `${ORCID}/oauth/jwks` |
| 用户信息 | 不配，邮箱在 `id_token` 里 | `https://api.github.com/user` | 不配，`sub` 就是 iD |
| 主体属性 | `sub` | `id` | `sub` |
| 校验 `iss` | `https://accounts.google.com` | | `${ORCID}` |
| PKCE | 开 | 开 | 看实测 |
| 换令牌认证方式 | `client_secret_post` | `client_secret_post` | `client_secret_post` |

不用 Boot 的 `spring.security.oauth2.client.registration` 属性：`OAuth2ClientProperties.validate` 要求客户端 ID 非空，一家没配整个应用就起不来；它的 `issuer-uri` 还会让启动时联网读发现文档。`ConfiguredClientRegistrations` 按同样的字段自己建 `ClientRegistration`，声明成 `ClientRegistrationRepository` 后 Boot 的默认实现让位。它不用框架的 `InMemoryClientRegistrationRepository`：后者不接受空集合。`issuerUri` 只被 `OidcIdTokenValidator` 拿来比较 `iss`，不会触发联网。

`redirect-uri` 写 `${PATRA_OAUTH_REDIRECT_BASE_URL}/api/auth/oauth/callback/<id>`。框架把它原样放进授权请求，换令牌时从存下的授权请求里取，所以 identity 处理回调时看到的请求地址是内网地址也没关系（已核对 `OAuth2AuthorizationCodeGrantRequest` 和 `OAuth2AuthorizationCodeAuthenticationProvider`：不比较回调地址和请求地址）。

出站：`IdentityOAuth2ClientConfiguration` 建一个带超时的客户端（连接 3 秒、读 5 秒，`JdkClientHttpRequestFactory`，跟随 JVM 默认的 `ProxySelector`），给三处用：

- `RestClientAuthorizationCodeTokenResponseClient.setRestClient`（保留框架要求的消息转换器和错误处理器）；
- `DefaultOAuth2UserService.setRestOperations`；
- 自己声明的 `JwtDecoderFactory<ClientRegistration>`：`OidcIdTokenDecoderFactory` 在 7.1.2 没有设置 HTTP 客户端的口子，默认无超时。工厂做的事和框架默认一样，按注册 ID 缓存 `NimbusJwtDecoder.withJwkSetUri(...).restOperations(...)`，校验器是 `JwtTimestampValidator` + `OidcIdTokenValidator`，声明成 Bean 后 `OAuth2LoginConfigurer` 自动取用。

## 5. 过滤器链与 starter 改动

`SecurityServletAutoConfiguration.patraSecurityFilterChain` 的条件是 `@ConditionalOnMissingBean(SecurityFilterChain.class)`，identity 一声明新链它就消失，`/me/**` 就没有断言认证了。网关又依赖这个让位。

做法：starter 新增公开类 `AdditionalSecurityFilterChain implements SecurityFilterChain`，包一个已构建的链；默认链的条件改成 `@ConditionalOnMissingBean(value = SecurityFilterChain.class, ignored = AdditionalSecurityFilterChain.class)`。

- 声明普通 `SecurityFilterChain`：替代默认链（网关，行为不变）。
- 声明 `AdditionalSecurityFilterChain`：只是加一条，默认链留着。工厂方法的返回类型必须写这个类，条件才认得出来。

starter 的 `SecurityProblemWriter` 加公开方法 `writeProblem`，`detail` 保留领域文案：回调的成功处理器里抛的业务异常到不了 `GlobalRestExceptionHandler`（`AbstractAuthenticationProcessingFilter` 只接 `AuthenticationException`，而且它在 `ExceptionTranslationFilter` 之前），要由处理器自己写。

成功响应同样绕开了 MVC，而雪花 ID 转字符串只挂在 MVC 的转换器上（`LongToStringHttpMessageConvertersCustomizer`）。它加一个 `jsonConverter()`，处理器用它写响应，不另建 mapper。

identity 的新链（`IdentityOAuth2SecurityConfiguration`，`@Order(1)`）：

```java
http.securityMatcher("/oauth2/authorization/<配好了的每一家>", "/login/oauth2/code/*");
StatelessSecurityDefaults.apply(http, problemWriter);
http.oauth2Login(login -> login
        .authorizationEndpoint(a -> a
            .authorizationRequestRepository(authorizationRequests)
            .authorizationRequestResolver(resolver))
        .tokenEndpoint(t -> t.accessTokenResponseClient(tokenClient))
        .userInfoEndpoint(u -> u.userService(userService))
        .authorizedClientRepository(discardingClients)
        .successHandler(successHandler)
        .failureHandler(failureHandler))
    .authorizeHttpRequests(a -> a.anyRequest().permitAll());
return new AdditionalSecurityFilterChain(http.build());
```

- `securityMatcher` 只收这两组路径：其余仍走默认链；框架的默认登录页 `/login` 不在匹配范围里，到不了。
- `STATELESS`：不建 `HttpSession`，没有 `JSESSIONID`。
- 发起路径只收配好了的平台：不认识或没配的不进这条链，由 MVC 回 404，而不是框架重定向过滤器默认的 500。
- `authorizedClientRepository`：什么都不存的 `DiscardingAuthorizedClientRepository`，同时声明成 Bean，Boot 的 `AuthenticatedPrincipalOAuth2AuthorizedClientRepository`（默认实现，存内存）随之让位。

失败处理器把 `OAuth2AuthenticationException` 的错误码翻译成第 2.2 节的错误：

| 错误码 | 结果 |
|---|---|
| `access_denied` | `OAUTH_CANCELLED` |
| `invalid_token_response`、`invalid_user_info_response`、`server_error`、`temporarily_unavailable`，以及 `InternalAuthenticationServiceException`（Redis） | 503 |
| 其余（`authorization_request_not_found`、`invalid_state_parameter`、`invalid_id_token`、`invalid_nonce`、`invalid_grant` 等） | `OAUTH_REJECTED` |

`StandardErrorTrait` 没有 400，所以协议失败用 `RULE_VIOLATION`（422）。`OAuth2Error` 的描述不进响应。回调查询串带来的 `server_error`、`temporarily_unavailable`，描述是匿名请求写的，只进 `debug` 日志；换令牌、取资料失败的描述来自服务端，随 503 进 `ERROR` 日志，方便排查。

## 6. 授权请求存储（决策 B）

`AuthorizationRequestRepository<OAuth2AuthorizationRequest>` 的实现 `StoredAuthorizationRequestRepository` 放 adapter（框架类型不出 adapter），Redis 操作经 domain 的 `AuthorizationRequestStorePort`（`infra.account.oauth` 实现）：

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `idn:oauth:authz:<state>` | STRING | 授权请求的 JSON：`authorizationUri`、`clientId`、`redirectUri`、`scopes`、`state`、`additionalParameters`、`attributes`、`authorizationRequestUri` | 10 分钟 |

- `save`：`SET ... EX`。
- `remove`：`GETDEL`，取出即删。同一个 `state` 第二次回调拿不到记录，框架报 `authorization_request_not_found`。Redis 版本 7.0.15，支持 `GETDEL`。
- `load`：`GET`。`oauth2Login` 流程不调它，接口要求实现。
- `attributes` 里有框架放的 `registration_id`、`nonce`、`code_verifier`，这几样是授权请求的一部分，不是令牌。令牌不经过这个仓库。
- `remove` 把取出的请求放进请求属性，成功处理器据此知道用途（第 7 节）。框架在 `attemptAuthentication` 里调用 `remove` 后不再暴露它，这是最小的做法。
- Redis 暂时不可用时抛 `InternalAuthenticationServiceException`，`AbstractAuthenticationProcessingFilter` 接住后交给失败处理器，得到 503。

## 7. 三条流程的落地

### 7.1 用途与发起人

授权请求的 `attributes` 里加：`patra_purpose`（`LOGIN` / `LINK`），绑定时再加 `patra_user_id`、`patra_session_id`。没有 `patra_purpose` 按 `LOGIN`。

- 登录：`GET /oauth2/authorization/{provider}` 走框架的 `OAuth2AuthorizationRequestRedirectFilter`，默认就是 `LOGIN`。
- 绑定：`MeLoginMethodController` 先经 CommandBus 跑 `PrepareProviderLinkHandler`（第 2.4 节的检查），再调 adapter 的 `ProviderLinkAuthorizationStarter`：用官方的 `OAuth2AuthorizationRequestResolver.resolve(request, registrationId)` 得到授权请求，经 `OAuth2AuthorizationRequest.from(...).attributes(...)` 加上三个属性，存进仓库，返回 `authorizationRequestUri`。

不用「在 `/oauth2/authorization/{id}` 上加参数表示绑定」：框架的重定向过滤器捕获 resolver 抛的所有异常并 `sendError(500)`，`REAUTH_REQUIRED` 回不到门户。

回调时，`ProviderLoginSuccessHandler`（`AuthenticationSuccessHandler`）：

1. 用 `ProviderAccountMapper` 把 `OAuth2User` 转成 `ProviderAccount`。
2. 从请求属性取授权请求，读用途。
3. `LOGIN` → `CompleteProviderLoginCommand`；`LINK` → 用 `IdentityAssertionAuthenticationConverter` + `identityAssertionDecoder` 解出当前用户（此时 `SecurityContext` 里已经是 `OAuth2AuthenticationToken`，不能用 `CurrentUserPort`），和发起人一起交给 `LinkProviderAccountCommand`。
4. 写 JSON 响应；抛出的任何运行时异常交给 `SecurityProblemWriter.writeProblem`。

`ProviderAccount`（domain 值对象，决策 F）：`provider`、`subject`、`accountLabel`、`suggestedUsername`、`suggestedEmail`。映射规则：

| | subject | accountLabel | suggestedUsername | suggestedEmail |
|---|---|---|---|---|
| Google | `sub` | `email`，缺失时 `sub` | | `email` |
| GitHub | `id`（数字转字符串） | `login` | `login` 转小写，过不了用户名规则就留空 | `email`（公开邮箱，常为空） |
| ORCID | `sub` 规范化成裸 iD | 裸 iD | | |

`subject` 规范化放 `IdentityProvider.normalizeSubject`：ORCID 去掉 `https://orcid.org/` 前缀，校验 `^\d{4}-\d{4}-\d{4}-\d{3}[\dX]$`，不合格按 `OAUTH_REJECTED`。

### 7.2 登录（`CompleteProviderLoginHandler`）

1. 按 `(provider, subject)` 查外部身份。没有 → 建待补填记录，返回 `RegistrationRequired`。
2. 有 → 解析设备与属地（事务外，纯计算）。
3. 事务里：`findByIdForUpdate` 锁用户行（和封禁互斥）；封禁 403；已注销（到期执行时外部身份已删，正常到不了）401；查这台设备见过没有；更新 `account_label` 和 `last_login_at`；建不带密码证明的会话；读账号信息。
4. 提交后新设备提醒，和密码登录一样。`LoginUserHandler.notifyNewDevice` 抽到 `app.account.service.NewDeviceLoginNotifier`，两边共用。

### 7.3 首次注册（决策 D）

Redis：

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `idn:oauth:pending:<ticket 的 SHA-256>` | STRING | JSON：`provider`、`subject`、`accountLabel`、`suggestedUsername`、`suggestedEmail`、`expiresAt` | 30 分钟 |
| `idn:oauth:code:<ticket 的 SHA-256>` | HASH | `username`、`email`、`code_hash`、`attempts`、`expires_at`、`next_resend_at` | 10 分钟，不超过待补填记录的余量 |
| `idn:oauth-code:cooldown:email:<h>` | | 同邮箱 60 秒冷却 | 走 `action-throttle.lua` |
| `idn:oauth-code:hour:email:<h>` | | 同邮箱每小时 5 封 | 同上 |
| `idn:oauth-code:hour:ip:<h>` | | 同 IP 每小时 20 封 | 同上 |

- `ticket`：32 字节 `SecureRandom`，base64url，43 个字符，Redis 里只存哈希。
- `code_hash`：`SHA-256(ticket 哈希 + ":" + 码)`，原文只在邮件事件里。邮件记录的 `toString` 不输出码，日志只记 `userId` 级别的事实。
- 校验在 `oauth-code-verify.lua` 里原子完成：键不存在返回 `NONE`；匹配则删键并返回 `username`、`email`；不匹配 `HINCRBY attempts`，到 5 次删键返回 `EXHAUSTED`，否则返回 `MISMATCH`。

`SendRegistrationCodeHandler`：校验字段 → 查占用（`existsByEmail`、`existsByUsername`、`formerUsernames.existsReserved`）→ 限流 → 写验证码 → 短事务登记邮件。限流放在查占用之后：填错的不消耗配额。

`CompleteProviderRegistrationHandler`：校验字段 → 读待补填 → 校验验证码 → 解析设备（事务外）→ 事务里再查占用、`User.register` + `markEmailVerified`、存空资料、`ExternalIdentityRepository.insertIfAbsent`（`false` 回滚并报 `PROVIDER_ACCOUNT_TAKEN`）、建不带密码证明的会话、读账号 → 提交后删待补填，失败只记日志（TTL 兜底，唯一约束挡重复）。

**不建密码凭据，不签 `EMAIL_VERIFY` 令牌，不发验证邮件**：邮箱刚证明过。

撞上已注册邮箱不是账号合并：第 2.3 节在发码前就拒绝，事务里再拒绝一次。

### 7.4 绑定与解绑（决策 E）

绑定回调 `LinkProviderAccountHandler`：

1. 断言为空 → 401。断言的用户 ID、会话 ID 和授权请求里的不一致 → 403 `LINK_SESSION_MISMATCH`。不重新判再认证窗口：授权请求只活 10 分钟，发起时已判过。
2. 解析来源（事务外）。
3. 事务里锁用户行：`requireActive`；`(provider, subject)` 已有 → 自己的幂等 `LINKED`，别人的 `PROVIDER_ACCOUNT_TAKEN`；`(user_id, provider)` 已有 → `PROVIDER_ALREADY_LINKED`；`insertIfAbsent`，`false`（并发）→ `PROVIDER_ACCOUNT_TAKEN`；登记「已绑定」邮件。

解绑 `UnlinkProviderAccountHandler`：

1. 事务外预读：`LoginMethodPolicy.ensureCanUnlink(hasPassword, 已绑定数)` → 409；再过再认证守卫 → 403。
2. 事务里锁用户行，**重新**按当前凭据和绑定数判一次规则（并发解绑两家、并发撤销改邮箱删凭据，都要先拿这把锁），删行，登记「已解绑」邮件。

注销到期：`ExecuteDueAccountDeletionsHandler.completeOne` 事务里加 `externalIdentities.deleteAllByUserId(id)`。

撤销改邮箱（`RevertEmailChangeHandler`）也删全部外部身份。攻击者拿到密码和会话后先改邮箱，绑定的安全邮件就发给了自己；原主点「不是我」时，系统改回邮箱、删密码、踢会话，而登录只看 `(provider, subject)`，不删的话攻击者的第三方账号还能进来。原主自己绑的也一起删，重新登录后再绑。找回密码不碰外部身份（第 12 节）。

## 8. `auth_at` 可空的改动面

现在 `SessionStorePort.findAuthenticatedAt` 的空值表示「会话不在」，而 `auth_at` 又可能合法地没有。两个含义不能挤在一个 `Optional` 里。

| 位置 | 改动 |
|---|---|
| `SessionStorePort` | `findAuthenticatedAt` 换成 `Optional<SessionPasswordProof> findPasswordProof(...)`。外层空 = 会话不在；`SessionPasswordProof.verifiedAt()` 是 `Optional<Instant>` |
| `SessionPasswordProof`（新 domain 值对象） | 只有 `verifiedAt` 一个可空字段，工厂 `of(Instant)`、`none()` |
| `ReauthenticationGuard.requireRecent` | 会话不在 401；`verifiedAt` 为空或超窗 403 `REAUTH_REQUIRED` |
| `NewUserSession` | 加 `passwordVerifiedAt`（可空） |
| `SessionIssuer` | 现有 `issue` 语义不变（`passwordVerifiedAt = now`），新增 `issueWithoutPasswordProof` |
| `NewSession`、`RedisSessionStore.create` | 带上 `authenticatedAt`（可空），脚本参数多一个 |
| `session-create.lua` | 新增 `ARGV[12]`：空串不写 `auth_at`，否则写这个值。注释同步 |
| `session-rotate.lua` | 不用改：已经写了 `authAt and ...`，没有 `auth_at` 时不判宽限期，直接换并写入 |
| `session-find.lua`、`session-touch.lua`、`session-list.lua` | 不用改：都是整条读 |
| `StoredSession.authenticatedAt` | 可空；去掉 `requireNonNull` |
| `RedisSessionStore` 解析 | 缺 `auth_at` 映射成 `null`。`RedisSessionStoreIT.should_reject_session_without_auth_at` 改成断言能读出空值 |

`ReauthenticateUserHandler` 不用改：第三方登录后第一次输对密码，`rotate` 写入 `auth_at`，窗口打开。

## 9. 外部身份表（决策 C）

新脚本 `V20261104120000__create_external_identity.sql`：

```sql
CREATE TABLE idn_user_external_identity
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    provider        VARCHAR(16)     NOT NULL,
    subject         VARCHAR(255)    NOT NULL,
    account_label   VARCHAR(255)    NOT NULL,
    linked_at       timestamptz(6)  NOT NULL,
    last_login_at   timestamptz(6)  NULL,
    -- 审计列同 idn_user_password_credential
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_external_identity_subject UNIQUE (provider, subject),
    CONSTRAINT uk_idn_user_external_identity_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_idn_user_external_identity_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_external_identity_provider CHECK (provider IN ('GOOGLE', 'GITHUB', 'ORCID'))
);
```

`ExternalIdentity` 聚合继承现有聚合基类，`insertIfAbsent` 用 `INSERT ... ON CONFLICT DO NOTHING`（`transaction.md` 第 3 节禁止 catch 唯一约束后继续用同一事务），返回影响行数。没有用户名列，R6 通过。

## 10. 邮件

`OutgoingMail`、`SecurityMailEvent`、`SecurityMailEventMapper`、notification 的 `SecurityMailKind`、`SecurityMailEventConverter`、模板各加三种（sealed 穷举，漏了编译不过）：

| 模板名 / 路由键后半段 | 触发 | 事实 | 过期后还发吗 |
|---|---|---|---|
| `registration-code` | 发验证码 | 验证码、过期时间 | 不发（`linkExpiresAt` 用验证码过期时间） |
| `login-method-linked` | 绑定成功 | 平台名、账号标识、操作上下文 | 发 |
| `login-method-unlinked` | 解绑成功 | 平台名、账号标识、操作上下文 | 发 |

- 没有密码的用户申请重设时，`PasswordResetMailEvent.settingPassword` 为真，主题和正文说「设置密码」。注销两封的「用这个邮箱和密码登录」改成「重新登录」：只用第三方的人也要看得懂。
- 验证码那封写明「不要把验证码告诉任何人；如果不是你在注册 Patra，忽略这封邮件，不会有账号被创建」。收件人还没有账号，`displayName` 用提交的用户名。
- 验证码和现有一次性令牌链接一样，在事件里只待到 broker 确认（`completion-mode: delete`）。
- 路由键 `security-mail.*` 已被 `notification.security-mail` 绑定，不用改队列。

## 11. 代码放在哪

全部在 `account` 子域（决策 F），新用例族 `usecase.oauth`：

| 模块 | 内容 |
|---|---|
| `domain.account` | `model.vo.ProviderAccount`、`PendingRegistration`、`RegistrationCode`；`model.enums.IdentityProvider`；`model.aggregate.ExternalIdentity`；`model.read.LoginMethodsReadModel`；`policy.LoginMethodPolicy`、`ProviderRegistrationPolicy`；`port.repository.ExternalIdentityRepository`；`port.oauth.AuthorizationRequestStorePort`、`PendingRegistrationPort`；`port.throttle.RegistrationCodeThrottlePort`；三种邮件；异常 |
| `domain.session` | `SessionPasswordProof`；`IdentityErrorTrait` 加 `EMAIL_NOT_VERIFIED`、`OAUTH_CANCELLED`、`OAUTH_REJECTED`、`LINK_SESSION_MISMATCH`、`PROVIDER_ACCOUNT_TAKEN`、`PROVIDER_ALREADY_LINKED`、`LAST_LOGIN_METHOD` |
| `app.account.usecase.oauth` | `CompleteProviderLoginHandler`、`SendRegistrationCodeHandler`、`CompleteProviderRegistrationHandler`、`PrepareProviderLinkHandler`、`LinkProviderAccountHandler`、`UnlinkProviderAccountHandler`；查询 `app.account.usecase.query.LoginMethodQueryService`、`PendingRegistrationQueryService` |
| `infra.account` | `oauth.RedisAuthorizationRequestStore`、`oauth.RedisPendingRegistrationStore`、`throttle.RegistrationCodeThrottleAdapter`、持久化四件套、`oauth-code-verify.lua` |
| `adapter.account.security` | `StoredAuthorizationRequestRepository`、`DiscardingAuthorizedClientRepository`、`ProviderAccountMapper`、`ProviderLoginSuccessHandler`、`ProviderLoginFailureHandler`、`ProviderLinkAuthorizationStarter`（分包白名单为此加了 `security`） |
| `adapter.account.rest` | `ProviderRegistrationController`、`MeLoginMethodController` |
| `boot.config` | `ConfiguredClientRegistrations`、`IdentityOAuth2ClientConfiguration`、`IdentityOAuth2SecurityConfiguration`；配置在 `IdentityProperties.OAuth` |
| `patra-tool-security` | `AdditionalSecurityFilterChain`、默认链条件、`SecurityProblemWriter.writeProblem` |
| `patra-gateway-boot` | 公开路径 |
| `patra-notification` | 三种邮件的枚举、转换、模板 |
| `patra-infra` | compose 加可选的 `oauth.env` 和两个环境值，`docker/README` 的密钥清单 |

新依赖：`spring-boot-starter-security-oauth2-client` 加到 identity 的 adapter（`api`，boot 经它拿到）。adapter 再加 `patra-tool-security`（断言转换器、错误写出器）。domain、app、infra 不沾 `spring-security-oauth2-*`。

## 12. 测试

自动化测试全部用 WireMock 模拟平台：OIDC 平台（`/authorize` 不用真实现，测试自己拼回调；`/token` 返回用测试 RSA 私钥签的 `id_token`；`/jwks`）、GitHub 式平台（`/token`、`/user`）。

| 测试 | 守什么 |
|---|---|
| `SecurityServletAutoConfigurationTest` | 附加链不让默认链让位；普通链照样让位 |
| `OAuth2LoginFlowIT`（boot） | 现有 `/me/**` 断言链不受影响；不设 `JSESSIONID`；OIDC 和 GitHub 式都产出正确结果；启动后到第一次流程前 WireMock 收到 0 个请求 |
| `OAuth2ProtocolAttackIT`（boot，D7） | `state` 不存在、已用过；`id_token` 签名、nonce、受众、签发方不对；用户拒绝；令牌端点返回错误或 500。都不建账号、不发会话 |
| `TokenNotRetainedIT`（boot，D10） | 走完流程后 Redis 全部键、外部身份表、`OAuth2AuthorizedClientService` 里都没有 access token 字面量 |
| `ProviderLoginFlowIT`（boot，PAP-182） | 同一 `(provider, subject)` 进同一账号；封禁 403；注销中建会话带 `deletionDueAt`；网关式查会话正常；到期注销后可重新注册 |
| `ProviderRegistrationFlowIT`（boot，PAP-183、D8） | 见下 |
| `ProviderLinkFlowIT`（boot，PAP-184、D8） | 别人的回调被拒；已绑给别人被拒；解绑最后一种被拒；两封邮件；第三方会话 `REAUTH_REQUIRED` + `hasPassword: false`；经找回密码设密码后能再认证 |
| `RedisSessionStoreIT`、`ReauthenticationGuardTest` | `auth_at` 可空的语义 |
| 各 domain 策略、值对象、handler 单测 | 按 TDD 逐条 |
| `GatewaySecurity*`（网关） | 新公开路径匿名可达，`/me/login-methods` 仍要登录 |
| `SecurityMailFlowIT`、`ThymeleafMailRendererTest`、`SecurityMailKindTest`（notification） | 三种邮件能发、能渲染 |

预劫持路径的专门用例（`ProviderRegistrationFlowIT`）：

1. 没过验证码：`idn_user` 没有新行，同一个邮箱用密码正常注册成功。
2. 老王用自己的 GitHub 填小陈的邮箱，发了码但没填对；小陈正常注册并经找回密码重设；老王的 GitHub 没有绑到小陈的账号上，再用它登录仍是 `REGISTRATION_REQUIRED`。
3. 第三方注册的账号走一遍找回密码：外部身份不变，邮箱仍已验证（确认重设流程不碰外部身份）。
4. 填已注册邮箱：不发码（捕获队列里没有 `registration-code`），原账号所有列不变。
5. 错 5 次后验证码作废，正确的码也进不去。

## 13. 被替代的 v0.12 决策

| 位置 | 原决策 | 现在 |
|---|---|---|
| `v0.12-user-center.md` 决策 B 之下的默认流程 | 账号先建、邮箱后验证（第三方同理） | 第三方首次登录时，账号在邮箱验证通过后才创建（决策 D）。邮箱密码注册不变，仍先建后验 |
| `v0.12-user-center.md` 再认证决策 | 「没有密码的 OAuth 账号以后跳回提供方再认证，同样写 `auth_at`」 | 再认证只认密码（决策 E）；`auth_at` 可空，第三方登录不写 |
| `2026-10-20-identity-account-rebuild-design.md` 第 12.1 节 | 预留 `email_at_provider`、`display_name_at_provider`、`raw_profile` | 不收这些，改成 `account_label`（决策 C） |
| identity README 第 9 节 | `auth_at` 必填，缺失按数据损坏 | 可空 |

决策 B 的其余部分（永不按邮箱合并、邮箱一律由 Patra 验证、补填用户名和邮箱）继续生效。

## 14. PAP-181 的实测项（等用户注册好应用）

- mini 上的 JVM 到三家授权、令牌、公钥、用户信息接口的连通性，确定是否走代理（`reference_macmini_jvm_tls_handshake_flaky` 记过 Clash 对 JVM 的坑）。
- 沙箱实测 ORCID 的 `code_challenge`：接受、忽略还是报错，定 `PATRA_OAUTH_ORCID_REQUIRE_PKCE`。
- 结果写回本节。

在此之前全部用 WireMock 推进。
