# identity 人机验证实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (Patra 约定内联执行，不派子代理). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** identity 里加人机验证：登录按 IP 和邮箱失败次数触发，注册和找回密码每次校验，Turnstile 和 TRUSTTURN 改 Nacos 配置就能切换。

**Architecture:** 领域层定义 `HumanVerificationPort`，infra 放两家适配器，`HumanVerificationGuard` 是唯一决定“要不要验证、结果怎么处理”的地方，三个 Handler 调它。校验在事务外、`loginThrottle.begin` 之前。

**Tech Stack:** Java 25、Spring Boot 4、Redis Lua、`RestClient` + JDK `HttpClient`、WireMock、Testcontainers。

**Spec:** [`docs/patra/specs/2026-11-05-identity-human-verification-design.md`](../specs/2026-11-05-identity-human-verification-design.md)，产品规则见 [release spec](../release-specs/v0.20-human-verification.md)。

## Global Constraints

- 工作目录：工作树 `.claude/worktrees/v0.20-human-verification`，分支 `feat/v0.20-human-verification-api`。路径一律用绝对路径，不 `cd` 出工作树。
- 门槛默认值：邮箱 3 次、IP 5 次、IP 窗口 15 分钟；邮箱门槛必须小于 `login-throttle.max-failures`（默认 5）。
- 线上 `action` 值：`login`、`register`、`password_reset`。
- 字段错误：`field` 是 `verificationToken`，`code` 是 `VERIFICATION_REQUIRED` 或 `VERIFICATION_FAILED`；不可用是 503，`traits` 含 `VERIFICATION_UNAVAILABLE`。
- 出站超时：连接 2 秒，读 3 秒。
- 令牌、`secret`、`app_secret` 不进日志和仓库。`toString` 不输出令牌原文。
- 代码规范：每个方法写 `///` 注释；参数 ≥ 5 个的 record 用 `@Builder`，否则 `of()`；集合字段在紧凑构造器里 `copyOf`；注入 `Clock`，不直接调 `Instant.now()`。
- 改完先 `spotlessApply`；提交信息用 `type(scope):` 加中文动词开头，body 每行不超过 100 字符。名字含 TOKEN 的测试字面量用 `"F".repeat(43)` 这种写法，避开 gitleaks。
- WireMock 用 `wireMockConfig().http2PlainDisabled(true)`，否则 JDK `HttpClient` 会升级 h2c。
- 流程测试测 429 用 JDK `HttpClient`，`RestTestClient` 会按 `Retry-After` 自动重发。

## Review Focus

1. **令牌是空字符串或全空白**：等同没带，返回 `VERIFICATION_REQUIRED`，不调第三方。测试放任务 7。
2. **拿不到客户端 IP**（没有转发头或值不合法）：只按邮箱触发，不抛异常，不把所有请求算到同一个键。测试放任务 3、任务 8。
3. **同一 IPv6 的不同写法，以及 IPv4 映射地址**：`2001:DB8::1`、`2001:db8:0:0::2`、`::ffff:1.2.3.4` 和 `1.2.3.4` 要各自落到正确的计数键。测试放任务 3。
4. **第三方返回 200 但内容异常**（不是 JSON、缺字段、`success` 不是布尔）：绝不能算 `PASSED`。测试放任务 4、任务 5。
5. **运行中配置被刷新成不合法的值**（邮箱门槛 ≥ 锁定门槛、切到缺凭据的厂商）：登录不出 500，也不放行。测试放任务 6。

---

### Task 1: 迁入 PAP-190

**Files:** 来自 `.claude/worktrees/pap-190-login-usecase-refactor-d925c1` 的未提交改动。

**Interfaces:**
- Produces: `ThrottledPasswordCheck#run(EmailAddress, PlainPassword, Supplier<Optional<T>>, Supplier<? extends RuntimeException>)`、`LoginInput.parse(LoginUserCommand)`、`User#requireLoginEligible()`。

- [ ] **Step 1:** 从 PAP-190 工作树导出补丁（`git add -N` 新文件后 `git diff --binary HEAD -- patra-api/patra-identity`，再撤销 `add -N`），在本工作树 `git apply --3way`。2026-11-05 预检已确认能干净应用。
- [ ] **Step 2:** 核对 #395 新增的代码是否也该用 `requireLoginEligible`：`grep -rn "isBanned\|isDeleted" patra-api/patra-identity/patra-identity-app/src/main`。只记录，不扩大范围。
- [ ] **Step 3:** 运行 `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-app:test`。Expected: PASS。
- [ ] **Step 4:** 提交 `refactor(identity): 收拢登录用例的字段校验、限流结算和资格判断 (PAP-190)`。

### Task 2: 领域类型与失败状态查询

**Files:**
- Create（`domain/account`）：`port/verification/HumanVerificationPort.java`、`model/enums/HumanVerificationProvider.java`、`model/enums/HumanVerificationAction.java`、`model/enums/HumanVerificationResult.java`、`policy/HumanVerificationPolicy.java`、`exception/HumanVerificationErrorTrait.java`、`exception/HumanVerificationUnavailableException.java`、`port/throttle/LoginThrottleStatus.java`
- Modify: `domain/account/port/throttle/LoginThrottlePort.java`、`domain/session/model/vo/UserFieldViolations.java`、`infra/account/throttle/LoginThrottleAdapter.java`
- Test: `HumanVerificationPolicyTest`、`HumanVerificationActionTest`、`LoginThrottleAdapterIT`（追加）

**Interfaces:**
- Produces:
  - `HumanVerificationPort`：`HumanVerificationProvider provider()`、`HumanVerificationResult verify(String token, HumanVerificationAction action)`
  - `HumanVerificationProvider { TURNSTILE, TRUST_TURN }`，`String wireValue()` 为 `turnstile`、`trustturn`
  - `HumanVerificationAction { LOGIN, REGISTER, PASSWORD_RESET }`，`String wireValue()`
  - `HumanVerificationResult { PASSED, REJECTED, UNAVAILABLE }`
  - `record HumanVerificationPolicy(HumanVerificationProvider provider, int emailFailureThreshold, int ipFailureThreshold, Duration ipFailureWindow)`，`boolean requiresVerification(int emailFailures, int ipFailures)`；构造时校验门槛至少为 1、窗口为正
  - `record LoginThrottleStatus(int failures, Optional<Duration> lockRemaining)`
  - `LoginThrottlePort#status(AccountType, EmailAddress)`：只读，Redis 不可用抛 `TemporarilyUnavailableException`
  - `UserFieldViolations.verificationRequired()`、`verificationFailed()`，字段名常量 `VERIFICATION_TOKEN`
  - `HumanVerificationUnavailableException`：特征 `DEP_UNAVAILABLE` + `HumanVerificationErrorTrait.VERIFICATION_UNAVAILABLE`

- [ ] **Step 1: 写失败测试**
  - `HumanVerificationPolicyTest`：`requires_verification_when_email_failures_reach_threshold`（`(3,0)` 为真，`(2,0)` 为假）、`requires_verification_when_ip_failures_reach_threshold`（`(0,5)` 为真，`(0,4)` 为假）、`rejects_threshold_below_one`。
  - `LoginThrottleAdapterIT`：`status_returns_failures_and_no_lock_after_two_failures`、`status_returns_lock_remaining_when_locked`、`status_returns_zero_for_unknown_email`。
- [ ] **Step 2:** 运行 `./gradlew :patra-api:patra-identity:patra-identity-domain:test`。Expected: 编译失败。
- [ ] **Step 3:** 实现上面的类型。`status` 用现有 `keys()` 读失败计数（`GET`）和锁（`PTTL`），两次读取不要求原子。
- [ ] **Step 4:** 运行 domain 的 `test` 和 `:patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*LoginThrottleAdapterIT*"`。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 增加人机验证的领域类型和失败状态查询 (PAP-192)`。

### Task 3: 按 IP 的失败计数

**Files:**
- Create: `domain/account/port/throttle/LoginIpFailurePort.java`、`infra/account/throttle/LoginIpFailureAdapter.java`、`infra/account/throttle/ClientNetworkKey.java`、`resources/redis/login-ip-failure-record.lua`
- Test: `ClientNetworkKeyTest`、`LoginIpFailureAdapterIT`

**Interfaces:**
- Produces: `LoginIpFailurePort`：`int count(String clientIp)`、`void record(String clientIp, Duration window)`；`clientIp` 为 `null` 时分别返回 0、什么都不做。

- [ ] **Step 1: 写失败测试**
  - `ClientNetworkKeyTest`：`ipv4_uses_whole_address`（`1.2.3.4` 和 `1.2.3.5` 不同）；`ipv6_uses_first_64_bits`（`2001:db8:0:0::1` 和 `2001:db8:0:0:ffff::2` 相同，`2001:db8:0:1::1` 不同）；`ipv6_spellings_are_equivalent`（`2001:DB8::1` 和 `2001:db8:0:0::1` 相同）；`ipv4_mapped_ipv6_equals_ipv4`（`::ffff:1.2.3.4` 和 `1.2.3.4` 相同）；`returns_empty_for_invalid_literal`（`not-an-ip`、`null`）。
  - `LoginIpFailureAdapterIT`：`record_increments_count`、`count_is_zero_for_null_ip`、`record_is_noop_for_null_ip`、`first_failure_sets_expiry_and_later_failures_keep_it`（断言第二次后 `PTTL` 不大于第一次）、`key_does_not_contain_raw_ip`。
- [ ] **Step 2:** 运行相关测试。Expected: 编译失败。
- [ ] **Step 3:** 实现。`ClientNetworkKey.of(String)` 返回 `Optional<String>`，用 `InetAddress.ofLiteral`，IPv6 清零后 64 位，结果做 SHA-256 十六进制；键 `idn:login-ip-failures:<hex>`。Lua：`INCR`，返回 1 时 `PEXPIRE`。Redis 暂时不可用转 `TemporarilyUnavailableException`，判定方式和 `ActionThrottleScript` 一致。
- [ ] **Step 4:** 运行相关测试。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 增加按客户端网络的登录失败计数 (PAP-193)`。

### Task 4: TurnstileAdapter

**Files:**
- Create: `infra/account/verification/TurnstileAdapter.java`、`TurnstileSettings.java`、`VerificationTokenRules.java`
- Modify: `patra-identity-infra/build.gradle.kts`（如 integrationTest 还没有 WireMock，按 `CatalogReferenceAdapterIT` 现状确认后不改）
- Test: `TurnstileAdapterIT`、`VerificationTokenRulesTest`

**Interfaces:**
- Consumes: 任务 2 的 `HumanVerificationPort`。
- Produces:
  - `record TurnstileSettings(String baseUrl, String secret, Set<String> allowedHostnames)`
  - `TurnstileAdapter(RestClient client, Supplier<TurnstileSettings> settings)`
  - `VerificationTokenRules.isSendable(String token, int maxLength)`：非空、长度不超限、不含空白和控制字符

- [ ] **Step 1: 写失败测试** `TurnstileAdapterIT`（响应体都用 WireMock 构造，`action` 用 `LOGIN`）：
  - `passed_when_success_and_hostname_and_action_match`
  - `rejected_when_hostname_not_allowed`、`rejected_when_action_mismatch`、`rejected_when_hostname_missing`
  - `rejected_on_timeout_or_duplicate`、`rejected_on_invalid_input_response`
  - `passed_with_testing_key_marker_skips_hostname_and_action`（响应 `hostname: example.com`、无 `action`、`metadata.result_with_testing_key: true`，这是 2026-11-05 实测的真实形状）
  - `rejected_when_success_false_even_with_testing_key_marker`
  - `unavailable_on_invalid_input_secret`、`unavailable_on_internal_error`
  - `retries_once_on_500_with_same_idempotency_key`（两次请求体的 `idempotency_key` 相同）；`unavailable_after_second_failure`
  - `unavailable_on_read_timeout`（`withFixedDelay` 超过读超时；连接超时不测）
  - `unavailable_when_body_is_not_json`、`unavailable_when_success_is_not_boolean`
  - `unavailable_when_secret_blank_without_calling_remote`
  - `rejected_without_calling_remote_when_token_too_long`（2049 字符）、`..._when_token_has_whitespace`
  - `request_contains_no_remoteip`
- [ ] **Step 2:** 运行 `./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*TurnstileAdapterIT*"`。Expected: 编译失败。
- [ ] **Step 3:** 实现。表单提交 `secret`、`response`、`idempotency_key`（UUID）。响应按 `JsonNode` 读，不绑死类型，异常形状归 `UNAVAILABLE`。`invalid-input-secret`、`missing-input-secret` 打 ERROR；走测试密钥分支打 WARN。日志不含令牌。
- [ ] **Step 4:** 运行测试。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 接入 Cloudflare Turnstile 校验 (PAP-192)`。

### Task 5: TrustTurnAdapter

**Files:**
- Create: `infra/account/verification/TrustTurnAdapter.java`、`TrustTurnSettings.java`、`TrustTurnRequestSigner.java`
- Test: `TrustTurnRequestSignerTest`、`TrustTurnAdapterIT`

**Interfaces:**
- Produces:
  - `record TrustTurnSettings(String baseUrl, String siteKey, String appSecret, Set<String> allowedOrigins)`
  - `TrustTurnRequestSigner.sign(String appSecret, long timestampMillis, String nonce, byte[] body)` 返回 `v1=<hex>`
  - `TrustTurnAdapter(RestClient client, Supplier<TrustTurnSettings> settings, Clock clock)`

- [ ] **Step 1: 写失败测试**
  - `TrustTurnRequestSignerTest`：`signs_canonical_string`，按 spec 第 5.3 节的六行规范串，用 JDK `Mac` 在测试里独立算一遍期望值比对；`signature_changes_when_body_changes`。
  - `TrustTurnAdapterIT`：`passed_when_success_and_site_key_action_origin_match`；`rejected_when_site_key_differs`、`rejected_when_action_differs`、`rejected_when_origin_not_allowed`；`rejected_on_invalid_ticket`、`rejected_on_action_mismatch`、`rejected_on_replayed_token`、`rejected_on_environment_risk`；`unavailable_on_invalid_request_signature`、`unavailable_on_replayed_request`、`unavailable_on_site_authorization_expired`；`unavailable_on_500_without_retry`（WireMock 只收到一次请求）；`unavailable_on_read_timeout`；`unavailable_when_body_is_not_json`；`unavailable_when_app_secret_blank_without_calling_remote`；`request_headers_are_signed_correctly`（从 WireMock 收到的请求取时间戳、nonce 和原始请求体重算签名）；`nonce_differs_between_requests`；`body_contains_only_site_key_ticket_expected_action`。
- [ ] **Step 2:** 运行相关测试。Expected: 编译失败。
- [ ] **Step 3:** 实现。先序列化请求体成字节，签名和发送用同一份字节。错误码依次读 `error`、`code` 字段，读不到归 `UNAVAILABLE`。`risk` 只打 DEBUG。
- [ ] **Step 4:** 运行相关测试。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 接入 TRUSTTURN 校验 (PAP-192)`。

### Task 6: 配置与装配

**Files:**
- Create: `boot/config/HumanVerificationProperties.java`、`boot/config/HumanVerificationConfiguration.java`、`boot/config/HumanVerificationProviderNotice.java`
- Modify: `boot/config/IdentityConfiguration.java`（注册 properties）、`src/integrationTest/resources/application-test.yml`
- Test: `HumanVerificationConfigurationTest`（`ApplicationContextRunner`）

**Interfaces:**
- Consumes: 任务 4、5 的 settings record 和适配器。
- Produces: Bean `Supplier<HumanVerificationPolicy>`、`Supplier<TurnstileSettings>`、`Supplier<TrustTurnSettings>`、两个 `HumanVerificationPort`。

- [ ] **Step 1: 写失败测试**
  - `fails_startup_when_active_provider_has_no_secret`、`fails_startup_when_turnstile_site_key_missing`、`fails_startup_when_email_threshold_not_below_lock_threshold`
  - `starts_when_inactive_provider_has_no_credentials`（当前是 Turnstile，TRUSTTURN 没配）
  - `policy_supplier_reads_current_values_after_change`（改 properties 后再取）
  - `policy_supplier_clamps_email_threshold_after_invalid_refresh`（运行中改成 ≥ 锁定门槛，取到“锁定门槛减一”，不抛异常）
  - `logs_warn_when_active_provider_is_trust_turn`（启动和 `EnvironmentChangeEvent` 各一次）
- [ ] **Step 2:** 运行 `./gradlew :patra-api:patra-identity:patra-identity-boot:test --tests "*HumanVerificationConfigurationTest*"`。Expected: 编译失败。
- [ ] **Step 3:** 实现。`HumanVerificationProperties` 写法照 `LoginThrottleProperties`（可变、`volatile`、`@Validated`），前缀 `patra.identity.human-verification`，字段见 spec 第 6 节。`application.yml` **不写**默认密钥和 `site-key`。`RestClient` 自建，请求工厂照 `IdentityOAuth2ClientConfiguration#outboundRequestFactory`，超时 2 秒和 3 秒。`application-test.yml` 配假密钥和 WireMock 占位地址。
- [ ] **Step 4:** 运行测试。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 增加人机验证配置，支持在线切换厂商 (PAP-192)`。

### Task 7: HumanVerificationGuard

**Files:**
- Create: `app/account/service/HumanVerificationGuard.java`、`app/account/usecase/query/HumanVerificationQueryService.java`、`app/account/usecase/query/HumanVerificationView.java`
- Modify: `boot/config/HumanVerificationConfiguration.java`（`HumanVerificationView` 所需的 `site-key` 来源）
- Test: `HumanVerificationGuardTest`、`HumanVerificationQueryServiceTest`

**Interfaces:**
- Consumes: 任务 2、3。
- Produces:
  - `HumanVerificationGuard#requireForLogin(EmailAddress email, String clientIp, String token)`
  - `HumanVerificationGuard#require(HumanVerificationAction action, String token)`
  - `HumanVerificationQueryService#current()` 返回 `HumanVerificationView(String provider, String siteKey)`；`site-key` 经 `Supplier<Map<HumanVerificationProvider, String>>` 取

- [ ] **Step 1: 写失败测试** `HumanVerificationGuardTest`：
  - `throws_locked_without_calling_port_when_account_locked`
  - `does_nothing_below_thresholds`（带了令牌也不调 port）
  - `requires_token_when_email_threshold_reached`、`requires_token_when_ip_threshold_reached`：抛 `InvalidUserFieldsException`，含 `VERIFICATION_REQUIRED`
  - `treats_blank_token_as_missing`（`""`、`"   "`，不调 port）
  - `passes_when_port_passes`、`failed_when_port_rejects`（`VERIFICATION_FAILED`）、`unavailable_when_port_unavailable`（`HumanVerificationUnavailableException`）
  - `selects_port_by_current_provider`，以及切换后选另一家
  - `null_client_ip_uses_email_trigger_only`
  - `require_always_verifies`（注册和找回密码，门槛无关）
  - `HumanVerificationQueryServiceTest`：`returns_current_provider_and_site_key`，切换后返回新值。
- [ ] **Step 2:** 运行 `./gradlew :patra-api:patra-identity:patra-identity-app:test --tests "*HumanVerification*"`。Expected: 编译失败。
- [ ] **Step 3:** 实现。注入 `List<HumanVerificationPort>` 并转成按 `provider()` 索引的 Map。
- [ ] **Step 4:** 运行测试。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 增加人机验证守卫和厂商查询 (PAP-193)`。

### Task 8: 接入登录

**Files:**
- Modify: `app/account/service/ThrottledPasswordCheck.java`、`app/account/usecase/login/LoginUserHandler.java`、`LoginUserCommand.java`
- Test: `ThrottledPasswordCheckTest`、`LoginUserHandlerTest`（追加）

**Interfaces:**
- Consumes: 任务 3、7。
- Produces: `ThrottledPasswordCheck#run(email, password, check, mismatch, Runnable onFailure)`；原四参方法转调，传空操作。`LoginUserCommand` 增加 `verificationToken`。

- [ ] **Step 1: 写失败测试**
  - `ThrottledPasswordCheckTest`：`calls_on_failure_when_password_mismatch`、`calls_on_failure_before_throwing_locked`、`calls_on_failure_when_password_too_long`、`does_not_call_on_failure_on_success`、`does_not_call_on_failure_when_cancelled`。
  - `LoginUserHandlerTest`：`guard_runs_before_throttle_begin`（`InOrder`）；`guard_failure_skips_password_check`（不调 `begin`、不做哈希）；`records_ip_failure_on_wrong_password`；`records_ip_failure_on_unknown_email`；`does_not_record_ip_failure_on_success`；`ip_record_failure_does_not_hide_invalid_credentials`（`record` 抛异常时仍抛 `InvalidCredentialsException`）；`command_to_string_hides_verification_token`。
- [ ] **Step 2:** 运行 app 模块相关测试。Expected: 编译失败。
- [ ] **Step 3:** 实现。IP 窗口从 `Supplier<HumanVerificationPolicy>` 取。更新 `LoginUserHandler` 类注释里的流程。
- [ ] **Step 4:** 运行 app 模块全部 `test`。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 登录按 IP 和邮箱失败次数要求人机验证 (PAP-193)`。

### Task 9: 接入注册和找回密码

**Files:**
- Modify: `RegisterUserCommand.java`、`RegisterUserHandler.java`、`RequestPasswordResetCommand.java`、`RequestPasswordResetHandler.java`
- Test: `RegisterUserHandlerTest`、`RequestPasswordResetHandlerTest`（追加）

**Interfaces:**
- Consumes: 任务 7 的 `require`。
- Produces: 两个命令增加 `verificationToken`。`RequestPasswordResetCommand.of(email, clientIp, verificationToken)`。

- [ ] **Step 1: 写失败测试**
  - `RegisterUserHandlerTest`：`field_errors_win_over_verification`（字段错误时不调 guard）；`verification_runs_before_uniqueness_check`（guard 抛异常时不查占用、不做哈希）；`passes_register_action`；`command_to_string_hides_verification_token`。
  - `RequestPasswordResetHandlerTest`：`verification_runs_before_throttle`（guard 抛异常时不调 `acquireRequest`）；`passes_password_reset_action`。
- [ ] **Step 2:** 运行相关测试。Expected: 编译失败。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 运行 app 模块全部 `test`。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 注册和找回密码每次校验人机验证 (PAP-193)`。

### Task 10: 接口与网关

**Files:**
- Modify: `adapter/account/rest/AuthController.java`、`PasswordResetController.java`、`request/LoginRequest.java`、`RegisterRequest.java`、`ForgotPasswordRequest.java`、`patra-gateway-boot/.../GatewaySecurityConfiguration.java`
- Create: `adapter/account/rest/response/HumanVerificationResponse.java`
- Test: `AuthControllerIT`、`PasswordResetControllerIT`（追加）、`GatewayAuthenticationIT`（追加）

**Interfaces:**
- Consumes: 任务 7 的 `HumanVerificationQueryService#current()`，任务 8、9 的命令字段。
- Produces: spec 第 3 节的接口。

- [ ] **Step 1: 写失败测试**
  - `AuthControllerIT`：`login_passes_verification_token_to_command`、`register_passes_verification_token_to_command`；`get_human_verification_returns_provider_and_site_key`，断言响应没有 `secret` 字样且 `Cache-Control` 含 `no-store`；`verification_required_maps_to_422_with_field_code`；`verification_unavailable_maps_to_503_with_trait`。
  - `PasswordResetControllerIT`：`forgot_passes_verification_token_to_command`。
  - `GatewayAuthenticationIT`：`human_verification_endpoint_is_public`。
- [ ] **Step 2:** 运行 adapter 和 gateway 的相关 IT。Expected: 失败。
- [ ] **Step 3:** 实现。`@WebMvcTest` 切片要 `@Import` 控制器（仓库写法）。
- [ ] **Step 4:** 运行相关 IT。Expected: PASS。
- [ ] **Step 5:** 提交 `feat(identity): 开放人机验证接口并放行网关路径 (PAP-192)`。

### Task 11: 流程测试

**Files:**
- Create: `boot/src/integrationTest/.../HumanVerificationFlowIT.java`
- Modify: `IdentityFlowITSupport.java` 及依赖它的注册、登录辅助方法

**Interfaces:**
- Consumes: 全部接口。JVM 级共用一个静态 `WireMockServer`（不用每个类换端口的 `WireMockExtension`，否则共用上下文记住旧端口）。

- [ ] **Step 1:** 给 `IdentityFlowITSupport` 加 WireMock 默认全部通过，注册和被触发的登录统一带令牌。运行现有 boot IT。Expected: 和改动前一样通过，新文件不存在时先确认没有回归。
- [ ] **Step 2: 写流程测试** `HumanVerificationFlowIT`（spec 第 7 节）：
  - `credential_stuffing_requires_verification_after_five_failures_from_one_ip`：第 6 个邮箱 `VERIFICATION_REQUIRED`
  - `brute_force_requires_verification_after_three_failures_on_one_email`：换 3 个 IP，第 4 次要求验证
  - `locks_on_fifth_failure_and_locked_account_skips_verification`：第 5 次失败 429，之后不要求验证，WireMock 没有多余请求
  - `unknown_and_existing_email_get_identical_responses`
  - `successful_login_does_not_clear_ip_counter`
  - `failed_verification_does_not_consume_failure_quota`：连续 `VERIFICATION_FAILED` 后 `status().failures` 不变
  - `failed_verification_skips_password_hashing`
  - `register_and_forgot_without_token_are_rejected`
  - `third_party_down_returns_503_on_all_three_endpoints`
  - `switching_provider_uses_other_adapter_and_query_returns_it`：刷新配置后走 TRUSTTURN 的桩
  - `request_without_forwarded_ip_uses_email_trigger_only`
- [ ] **Step 3:** 运行 `./gradlew :patra-api:patra-identity:patra-identity-boot:integrationTest --tests "*HumanVerificationFlowIT*"`。Expected: PASS。如有不符合设计的失败，修实现，不改断言。
- [ ] **Step 4:** 提交 `test(identity): 覆盖撞库、爆破和第三方不可用的流程 (PAP-193)`。

### Task 12: 配置落地与文档

**Files:**
- Modify: `patra-infra/nacos/dev/patra-identity.yaml`、`patra-infra/docker/README.md`、`patra-api/patra-identity/README.md`、`patra-api/patra-gateway-boot/README.md`（公开路径说明）

- [ ] **Step 1:** Nacos 文件加 spec 第 6 节的配置。Turnstile 真实 `site-key` 在建好组件后填入；密钥只写占位变量。
- [ ] **Step 2:** identity README：模块表、错误、配置、本机联调要配的两个环境变量（官方测试密钥）、切换方法、切到 TRUSTTURN 的风险。对照 `git diff --stat` 的新增文件逐个核对模块表。
- [ ] **Step 3:** 基础设施 README 删掉“没有人机验证”这条已知限制。`192.168.97.1` 那条由 PAP-195 删。
- [ ] **Step 4:** 提交 `docs(identity): 记录人机验证的配置、切换和本机联调 (PAP-192)`。

### Task 13: 全量验证

- [ ] **Step 1:** `./gradlew spotlessApply`，有改动就进上一个提交的 fixup 或单独提交。
- [ ] **Step 2:** `./gradlew compileJava compileTestJava`。Expected: BUILD SUCCESSFUL。
- [ ] **Step 3:** `./gradlew :patra-api:patra-identity:patra-identity-domain:check`。Expected: 六边形纯净性通过。
- [ ] **Step 4:** `./gradlew check integrationTest`（负载高时看 `uptime`，网关超时类 IT 抖动要单独重跑确认）。Expected: BUILD SUCCESSFUL。
- [ ] **Step 5:** `git diff origin/main --stat` 核对只有本版路径；gitleaks 通过；确认没有混入主检出的 Redis 哈希标签改动。
- [ ] **Step 6:** 停在本地，交给用户验收。不推送、不开 PR，等门户部分也准备好后按 release spec 决策 I 一起走。
