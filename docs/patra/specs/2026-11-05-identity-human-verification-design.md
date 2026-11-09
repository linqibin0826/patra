# identity 人机验证：工程设计

> 对应 Issue：PAP-192、PAP-193。产品规则以 [v0.20 release spec](../release-specs/v0.20-human-verification.md) 为准，本文只写实现怎么落。门户的工程设计单独写，本文第 3 节是两边的接口约定。

## 1. 目的

现有登录限制只按邮箱计数，防不住撞库；注册和找回密码也没有人机验证，而门户已经通过 `https://patra.linqibin.dev` 对外。这次在 identity 里加一层人机验证：

- 登录：同一 IP 或同一邮箱短时间内失败到门槛后，再次提交必须先过验证。
- 注册、找回密码的申请：每次都要先过验证。
- Turnstile 和 TRUSTTURN 改 Nacos 配置就能切换，业务代码不依赖具体厂商。

**不做**：网关流量限流、按 IP 封禁、改账号锁定规则、加载失败兜底、给 `/reset`、`/reauth` 和第三方登录接口加验证。

**失败的标准**：下面任何一条出现，都算没做好，哪怕测试全绿。

- 正常用户输对密码却被要求验证。
- 校验失败的请求占了失败限制名额，或者做了密码哈希。
- 第三方出问题时放行了请求。
- 响应差异能让人看出某个邮箱是否存在。
- 令牌、`secret`、`app_secret` 出现在日志或仓库里。

## 2. 基线

后端 PR 第一个提交是 PAP-190。本文按重构后的结构写：`LoginUserHandler#handle` 依次是 `LoginInput.parse`、`authenticate`（调 `ThrottledPasswordCheck#run`）、`requireLoginEligible`、发会话。PAP-190 的改动基于 `25b6264bc`，原样迁到本分支，和 #395 对 identity 的改动一起核对；用户在开 PR 前连同后续改动一起验收，验收时如果改了 `ThrottledPasswordCheck` 的签名，后面的提交跟着调整。

## 3. 接口约定（门户依赖这一节）

### 3.1 读取当前厂商

`GET /patra-identity/auth/human-verification`，匿名可访问，网关要加进 `IDENTITY_PUBLIC_PATHS`。响应带 `Cache-Control: no-store`：

```json
{ "provider": "turnstile", "siteKey": "0x4AAA..." }
```

`provider` 取值 `turnstile` 或 `trustturn`。响应里没有任何密钥。

### 3.2 提交令牌

以下三个接口的请求体增加可选字段 `verificationToken`（字符串）：

| 接口 | `action` | 何时必须带令牌 |
|---|---|---|
| `POST /auth/login` | `login` | 被触发时 |
| `POST /auth/register` | `register` | 每次 |
| `POST /auth/password/forgot` | `password_reset` | 每次 |

门户渲染组件时必须传这里的 `action`，服务端会核对。

### 3.3 错误响应

| 情况 | 状态码 | 响应 |
|---|---|---|
| 需要验证但没带令牌 | 422 | `errors: [{ field: "verificationToken", code: "VERIFICATION_REQUIRED" }]` |
| 令牌校验没通过 | 422 | `errors: [{ field: "verificationToken", code: "VERIFICATION_FAILED" }]` |
| 第三方不可用或配置缺失 | 503 | `traits` 含 `VERIFICATION_UNAVAILABLE` |
| 账号已锁定 | 429 | 不变，带 `retryAfterSeconds` |

沿用现有的 `InvalidUserFieldsException` 表达前两种，门户的字段错误解析不用改；第三种用服务自己的错误特征，写法和 `PostErrorTrait.DUPLICATE_SUBMISSION` 一样，门户的 `Problem.traits` 已经能读。令牌只能用一次，门户在收到任何失败响应后都要重置组件。字段校验失败（其他字段的 422）发生在校验令牌之前，令牌没被消费，但门户仍按同样规则重置，不做区分。

## 4. 请求流程

### 4.1 登录

```text
LoginInput.parse                      字段错误：422，不调第三方
  │
读失败状态（邮箱失败次数、锁、IP 失败次数）
  ├─ 已锁定                           429，不要求验证，不调第三方
  ├─ 没到门槛                         不校验令牌，带了也忽略
  └─ 到门槛：邮箱 ≥ 3 或 IP ≥ 5
       ├─ 没带令牌                    422 VERIFICATION_REQUIRED
       └─ 校验令牌（事务外）
            ├─ 通过                   继续
            ├─ 没通过                 422 VERIFICATION_FAILED
            └─ 不可用                 503
  │
ThrottledPasswordCheck#run            loginThrottle.begin → 哈希 → 结算
  ├─ 失败：邮箱计数（现有）和 IP 计数都加一
  └─ 成功：清零邮箱计数，IP 计数不动
```

要点：

- **校验顺序**：令牌校验在 `begin` 之前，所以校验失败的请求不占失败限制名额，也不做哈希。
- **不泄露邮箱**：不存在的邮箱也走假哈希并计失败（现有行为），所以存在和不存在的邮箱都在相同次数后要求验证。
- **IP 计数只记确认的失败**：凡是按失败结算的都计，包括密码错、账号已注销、密码超长，以及这次失败刚好触发上锁的情况，都在抛异常之前计。取消结算（中途出错）不计。只有密码登录计 IP，再认证、改密码、第三方登录的就地绑定都不计。
- **IP 计数写失败**：只记 WARN，不盖掉本次的错误。读失败状态时 Redis 不可用则 503，和现有登录限制一致。
- **门槛是软的**：同一时刻并发的请求可能都读到“没到门槛”，超出几个可以接受。这版不为此加锁。

### 4.2 注册和找回密码

字段校验通过后、其他任何检查之前校验令牌：注册在查邮箱和用户名占用之前，找回密码在 `passwordResetThrottle` 之前。这样机器人既探测不了占用情况，也占不了找回密码的限流名额。

**代价**：攻击者可以用假令牌让我们反复调用第三方校验接口。限制它属于网关流量限流（PAP-189），这版只在调第三方之前做长度和字符检查。

## 5. 代码结构

分包按 `.claude/rules/tech/package-layout.md`，放在 `account` 子域。

### 5.1 domain

```java
// port/verification
public interface HumanVerificationPort {
  HumanVerificationProvider provider();
  HumanVerificationResult verify(String token, HumanVerificationAction action);
}
```

- `HumanVerificationProvider`：`TURNSTILE`、`TRUST_TURN`。
- `HumanVerificationAction`：`LOGIN`、`REGISTER`、`PASSWORD_RESET`，带线上值。
- `HumanVerificationResult`：`PASSED`、`REJECTED`、`UNAVAILABLE`。适配器不抛异常表达这三种结果。
- `HumanVerificationPolicy`：门槛和窗口，校验邮箱门槛必须小于锁定门槛。
- `port/throttle/LoginIpFailurePort`：`int count(String clientIp)`、`void record(String clientIp, Duration window)`。IP 为 `null` 时 `count` 返回 0，`record` 什么都不做。
- `LoginThrottlePort` 增加只读方法 `status(accountType, email)`，返回失败次数和锁的剩余时间。

### 5.2 app

`app/account/service/HumanVerificationGuard` 是唯一决定“要不要验证、结果怎么处理”的地方：

- `requireForLogin(email, clientIp, token)`：读状态，已锁定抛 `LoginTemporarilyLockedException`，到门槛则校验。
- `require(action, token)`：注册和找回密码用，每次校验。
- 从 `Supplier<HumanVerificationPolicy>` 取当前厂商，在注入的 `List<HumanVerificationPort>` 里按 `provider()` 选一个，再加一家不用改这个类。

`LoginUserHandler` 在 `parse` 和 `authenticate` 之间调 guard。`ThrottledPasswordCheck#run` 增加一个失败时调用的回调，`LoginUserHandler` 用它计 IP；再认证和改密码不传。`LoginUserCommand`、`RegisterUserCommand`、`RequestPasswordResetCommand` 增加 `verificationToken`，`toString` 不输出原文。

读取当前厂商用 `app/account/usecase/query/HumanVerificationQueryService`。

### 5.3 infra

`infra/account/verification/`，出站用自建的 `RestClient`，不用容器里的 `RestClient.Builder`（它带 `lb` 解析），写法和 `IdentityOAuth2ClientConfiguration` 一致：JDK `HttpClient`，`ProxySelector.getDefault()`，连接超时 4 秒，单次请求 5 秒（JDK 的读超时从发起请求算起，包含建连）。2026-11-05 实测 identity 容器到 Cloudflare 的握手要 2.1～2.6 秒，整个请求 2.5～3 秒，最早定的 3 秒会经常超时。

**`TurnstileAdapter`**

- 请求 `POST {base-url}/turnstile/v0/siteverify`，带 `secret`、`response`、`idempotency_key`（每次校验生成一个 UUID）。不传 `remoteip`。
- `PASSED` 要同时满足：`success` 为真、`hostname` 在 `allowed-hostnames` 里、`action` 一致。
- `REJECTED`：`success` 为假且错误码是 `timeout-or-duplicate`、`invalid-input-response`；或者 `hostname`、`action` 不符；或者令牌超过 2048 字符、含空白或控制字符（不发请求）。
- `UNAVAILABLE`：超时、网络错误、5xx、`internal-error`。`invalid-input-secret`、`missing-input-secret` 也是 `UNAVAILABLE`，并打 ERROR。
- 超时、网络错误、5xx、`internal-error` 重试一次，用同一个 `idempotency_key`。
- **官方测试密钥**：2026-11-05 实测，always-pass 测试密钥返回 `hostname: example.com`，不返回 `action`，并带 `metadata.result_with_testing_key: true`。响应带这个标记且 `success` 为真时，跳过 `hostname` 和 `action` 核对，算 `PASSED`，并打 WARN。只有配置了测试密钥才会收到这个标记，所以不需要额外的放宽开关。线上用错密钥的风险靠第 6 节的“主配置不带默认密钥”和第 8 节的实测把关。

**`TrustTurnAdapter`**

- 请求 `POST {base-url}/api/tickets/consume`，请求体 `site_key`、`ticket`、`expected_action`。
- 请求头 `X-TrustTurn-Timestamp`（13 位毫秒）、`X-TrustTurn-Nonce`（18 字节随机数的 base64url）、`X-TrustTurn-Signature: v1=<hex>`。
- 签名串六行用 `\n` 拼接：`TRUSTTURN-TICKET-CONSUME-V1`、`POST`、`/api/tickets/consume`、时间戳、nonce、请求体字节的 SHA-256 十六进制。用 `app_secret` 做 HMAC-SHA256。签名用的请求体字节必须和实际发出的完全相同。
- `PASSED` 要同时满足：`success` 为真、`site_key` 相同、`action` 一致、`origin` 在 `allowed-origins` 里。`risk` 只记 DEBUG 日志，不参与判断，文档没有说明它的取值。
- `REJECTED`：`invalid_ticket`、`action_mismatch`、`challenge_binding`、`replayed_token`、`expired_token`、`session_binding`、`environment_risk`、`site_domain_not_allowed`；或者字段不符；或者令牌含空白或控制字符。
- `UNAVAILABLE`：超时、网络错误、5xx、`invalid_request_signature`、`replayed_request`、`site_authorization_*`、无法识别的响应。后三类打 ERROR。
- **不重试**：文档没有提供幂等机制，票据可能已被消费。
- 只发上面这些字段，不传任何用户数据。

**`LoginIpFailureAdapter`**

- 键 `idn:login-ip-failures:<sha256>`，单键，用 Lua 原子执行 `INCR`，第一次时设过期。窗口从第一次失败算起，和邮箱计数一致。
- IPv4 按整个地址计；IPv6 按 /64 计，取前 64 位规范化后再哈希。解析用 `InetAddress.ofLiteral`，不触发 DNS。

### 5.4 adapter、boot、gateway

- `AuthController`、`PasswordResetController` 的请求对象加 `verificationToken`，新增 `GET /auth/human-verification`。
- `HumanVerificationProperties`：可变类，前缀 `patra.identity.human-verification`，和 `LoginThrottleProperties` 一样经 Spring Cloud 重新绑定，每次调用经 `Supplier` 重新读取。
- `GatewaySecurityConfiguration#IDENTITY_PUBLIC_PATHS` 增加 `/patra-identity/auth/human-verification`。
- 没有“关闭”这个选项：`provider` 只能是两家之一，避免配置失误把验证关掉。

## 6. 配置

```yaml
patra:
  identity:
    human-verification:
      provider: turnstile           # 默认
      email-failure-threshold: 3    # 必须小于 login-throttle.max-failures
      ip-failure-threshold: 5
      ip-failure-window: 15m
      turnstile:
        base-url: https://challenges.cloudflare.com
        site-key: ${PATRA_IDENTITY_TURNSTILE_SITE_KEY:}
        secret: ${PATRA_IDENTITY_TURNSTILE_SECRET:}
        allowed-hostnames: [patra.linqibin.dev]
      trust-turn:
        base-url: https://api.trustturn.cc
        site-key: ${PATRA_IDENTITY_TRUSTTURN_SITE_KEY:}
        app-secret: ${PATRA_IDENTITY_TRUSTTURN_APP_SECRET:}
        allowed-origins: [https://patra.linqibin.dev]
```

| 项目 | 放在哪 |
|---|---|
| `secret`、`app_secret`、`site-key` | `~/.patra/secrets/identity.env`，compose 已经加载，不进仓库。`site-key` 可以公开，放这里是为了本机用环境变量换成测试值 |
| `allowed-*`、`provider`、门槛 | Nacos 的 `patra-identity.yaml` |
| 自动化测试 | `application-test.yml` 指向 WireMock，用假密钥，不用官方测试密钥，这样 `hostname` 和 `action` 核对也被测到 |
| 本机联调 | 环境变量传 Turnstile 官方测试密钥，写进 identity README |

**主配置 `application.yml` 不带默认密钥**：如果默认放 always-pass 测试密钥，线上一旦漏配就会全部放行，违反第 1 节。缺密钥就启动失败。代价是本机启动 identity 要多配两个环境变量。

**校验和切换**：

- 启动时：当前厂商缺 `site-key` 或密钥、门槛不合法，启动失败。
- 刷新时：切到缺凭据的厂商，校验接口返回 503（`VERIFICATION_UNAVAILABLE`）并打 ERROR，查询接口照常返回。选的是拒绝，不是回退到另一家。邮箱门槛被刷新成不小于锁定门槛时，按“锁定门槛减一”生效并打 ERROR，不让登录出错。
- 切到 TRUSTTURN：启动和刷新后各打一条 WARN，写明访客的 IP 和浏览器信息会发给它。

## 7. 测试

适配器测试（WireMock 在本仓库只进 `integrationTest` 源集，放 infra 模块）：

- `TurnstileAdapterIT`、`TrustTurnAdapterIT`：通过、拒绝、令牌重复、`hostname` 或 `action` 或 `origin` 不符、超时、5xx、密钥错误。Turnstile 证明重试带同一个 `idempotency_key`，TRUSTTURN 证明不重试。

单测（JUnit、AssertJ、Mockito）：

- TRUSTTURN 的签名抽成纯函数，用固定输入比对已知结果，并证明每次 nonce 不同。
- `HumanVerificationGuardTest`：门槛边界、已锁定不调第三方、三种结果的处理、按配置选厂商。
- `HumanVerificationPolicyTest`：门槛的校验。
- `LoginIpFailureAdapter` 的 IPv6 /64 归一化。

集成测试（Testcontainers + WireMock，放 `patra-identity-boot`）：

- **撞库**：同一 IP 换 5 个邮箱失败，第 6 个邮箱不带令牌返回 `VERIFICATION_REQUIRED`，且没做哈希。
- **爆破**：同一邮箱换 3 个 IP 失败，第 4 次不带令牌返回 `VERIFICATION_REQUIRED`。
- **不泄露**：不存在的邮箱和存在的邮箱，每一步的响应都相同。
- **成功不清 IP**：IP 计数到门槛后，用这个 IP 输对另一个账号的密码（带令牌）成功，再换邮箱仍要求验证。
- **校验失败不占名额**：连续 `VERIFICATION_FAILED` 后，邮箱失败计数不变。
- **防绕过**：注册、找回密码不带令牌被拒绝。
- **第三方不可用**：WireMock 返回 5xx 或超时，三个接口都 503。
- **配置切换**：刷新配置后用另一家校验，查询接口返回新厂商。

现有的集成测试里，注册和被触发的登录要带令牌：`IdentityFlowITSupport` 等共用入口统一处理，WireMock 默认全部通过。

验证门控：`./gradlew check integrationTest`。

## 8. 上线与验收

**上线顺序**：后端和门户的 PR 都准备好后再一起合并。只上后端的话，门户还不会传令牌，注册和找回密码全部被拒绝；门户还没取真实 IP，所有外网用户共用一个 IP 计数，任何人累计失败 5 次后，所有外网用户都登录不了。这版不留关闭开关，所以不走“后端先合 main”的惯例，前端开发期间连本机后端，不连 mini。

1. 合并前：建 Turnstile 组件，绑定 `patra.linqibin.dev`，密钥写进 mini 的 `identity.env`，`site-key` 等配置推到 Nacos。
2. 合并前：确认 mini 能出站访问 `challenges.cloudflare.com` 和 `api.trustturn.cc`（JVM 经系统代理，曾有拨号超时的情况）。
3. 一起合并，等 CD 部署完，两个 PR 之间有几分钟的不一致窗口，现在只有少数体验用户，可以接受。
4. 按 release spec 第 5 节走线上实测。TRUSTTURN 的凭据由用户提供。

## 9. 留给实施者决定

- `HumanVerificationGuard` 的具体方法签名、异常类名。
- 请求和响应的 DTO 写法。
- Turnstile 请求用表单还是 JSON（文档两种都支持）。
- TRUSTTURN 错误响应的 JSON 字段名：文档只列了错误码，没有给响应示例。11/06 实测是 `{"success":false,"code":"invalid_ticket","error":"中文说明"}`，只按 `code` 分类，读不到按 `UNAVAILABLE`。
- 启动校验和刷新校验的具体实现位置。
