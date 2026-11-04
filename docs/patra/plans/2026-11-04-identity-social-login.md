# identity 第三方登录实施计划（v0.19 后端）

> **For agentic workers:** 本计划用 superpowers:executing-plans 在当前会话内联执行（用户决定，不派子代理）。步骤用 `- [ ]` 跟踪。

**Goal:** 让 identity 用 Spring 官方 `oauth2Login` 接入 Google、GitHub、ORCID，完成老用户登录、验证邮箱后才建账号的首次注册、绑定与解绑。

**Architecture:** 框架类型只在 adapter 与 boot；domain、app 只见 `ProviderAccount`。授权请求和待补填记录在 Redis，令牌不留存，门户转发发起和回调。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring Security 7.1.2、PostgreSQL 17、Redis 7、RabbitMQ、WireMock 3.10。

**Spec:** [`docs/patra/specs/2026-11-04-identity-social-login-design.md`](../specs/2026-11-04-identity-social-login-design.md)，产品决策 [`release-specs/v0.19-social-login.md`](../release-specs/v0.19-social-login.md)。接口、错误码、Redis 键、表结构都以工程设计为准，这里不重复。

## Global Constraints

- 框架的 `org.springframework.security.oauth2.client..` 只出现在 adapter、boot；domain、app、infra 不依赖。
- 事务里只放数据库操作，例外只有会话签发（`transaction.md`）。
- 唯一约束冲突用 `INSERT ... ON CONFLICT DO NOTHING`，不 catch 后继续用同一事务。
- 令牌不进 Redis、数据库、日志；验证码原文只在邮件事件里。
- 方法都写 `///` 注释；record 参数 ≤ 4 用 `of()`，≥ 5 用 `@Builder`。
- 提交只加自己的路径；subject 中文动词开头，body 每行 ≤ 100 字符；提交要等用户同意。

## Review Focus

1. 用户在平台上点拒绝：回调带 `error=access_denied`，应得 `OAUTH_CANCELLED`，不是 500。→ Task 12
2. 同一回调链接点两次（浏览器刷新）：第二次 `OAUTH_REJECTED`，不重复建账号。→ Task 12
3. GitHub 的 `login` 全是数字或带大写：预填要规范化，过不了规则就留空，不能让补填页一打开就报错。→ Task 5
4. 注册进行到一半邮箱被别人注册：`complete` 回 409，账号不建，能回到表单。→ Task 9
5. 第三方注册的用户走找回密码：外部身份不变，能继续用第三方登录。→ Task 12

---

### Task 1: starter 让附加链与默认链共存

**Files:**
- Create: `patra-tool/patra-tool-security/src/main/java/dev/patra/tool/security/autoconfigure/AdditionalSecurityFilterChain.java`
- Modify: `.../autoconfigure/SecurityServletAutoConfiguration.java`、`.../error/SecurityProblemWriter.java`、`patra-tool/patra-tool-security/README.md`
- Test: `.../autoconfigure/SecurityServletAutoConfigurationTest.java`、`.../error/SecurityProblemWriterTest.java`

**Interfaces:**
- Produces: `new AdditionalSecurityFilterChain(SecurityFilterChain delegate)`；`SecurityProblemWriter.writeProblem(HttpServletRequest, HttpServletResponse, RuntimeException) throws IOException`

- [ ] 写失败测试：`should_keep_default_chain_when_only_additional_chain_declared`（断言 `patraSecurityFilterChain` 还在）；`should_back_off_when_plain_chain_declared` 已有则确认；`writeProblem` 对领域异常输出其状态码
- [ ] 跑 `./gradlew :patra-tool:patra-tool-security:test --tests "*SecurityServletAutoConfigurationTest" --tests "*SecurityProblemWriterTest"`，确认失败
- [ ] 实现，默认链条件改成 `@ConditionalOnMissingBean(value = SecurityFilterChain.class, ignored = AdditionalSecurityFilterChain.class)`
- [ ] 跑通，提交

### Task 2: `auth_at` 可空与密码证明

**Files:**
- Create: `domain/session/model/vo/SessionPasswordProof.java`
- Modify: `SessionStorePort`、`ReauthenticationGuard`、`NewUserSession`、`SessionIssuer`、`infra/session/store/{NewSession,StoredSession,RedisSessionStore,SessionStoreAdapter}`、`session-create.lua`
- Test: `ReauthenticationGuardTest`、`SessionIssuerTest`、`RedisSessionStoreIT`

**Interfaces:**
- Produces: `SessionStorePort.findPasswordProof(AccountType, long, long): Optional<SessionPasswordProof>`；`SessionPasswordProof.verifiedAt(): Optional<Instant>`；`SessionIssuer.issueWithoutPasswordProof(long, LoginClient, ClientOrigin): IssuedUserSession`

- [ ] 改测试：守卫 `should_require_reauth_when_session_has_no_password_proof`；`RedisSessionStoreIT` 没有 `auth_at` 的会话能读出 `authenticatedAt == null`，能 `rotate` 并写入
- [ ] 确认失败，实现（工程设计第 8 节的表逐行改）
- [ ] 跑 `./gradlew :patra-api:patra-identity:patra-identity-domain:test :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*RedisSessionStoreIT"`，通过后提交

### Task 3: `hasPassword`

**Files:** `UserAccountReadModel`、`UserReadAdapter`、`CurrentUserResponse`；Test: `UserReadAdapterIT`、`AuthControllerIT`/`MeControllerIT` 的断言

**Interfaces:** Produces: `UserAccountReadModel.hasPassword(): boolean`，`CurrentUserResponse.hasPassword`

- [ ] 写失败测试：有凭据为真、没有为假；响应 JSON 带 `hasPassword`
- [ ] 实现，跑通，提交

### Task 4: 外部身份聚合与持久化

**Files:**
- Create: `domain/account/model/enums/IdentityProvider.java`、`domain/account/model/aggregate/ExternalIdentity.java`、`domain/account/port/repository/ExternalIdentityRepository.java`、`infra/account/persistence/{ExternalIdentityRepositoryAdapter,entity/ExternalIdentityEntity,dao/ExternalIdentityDao,mapper/ExternalIdentityJpaMapper}.java`、`db/migration/V20261104120000__create_external_identity.sql`
- Modify: `ExecuteDueAccountDeletionsHandler`
- Test: `IdentityProviderTest`、`ExternalIdentityTest`、`ExternalIdentityRepositoryAdapterIT`、`IdentitySchemaConstraintsIT`

**Interfaces:**
- Produces: `IdentityProvider{GOOGLE,GITHUB,ORCID}`：`registrationId()`、`fromRegistrationId(String): Optional`、`normalizeSubject(String): Optional<String>`；`ExternalIdentityRepository`：`findByProviderAndSubject`、`findByUserIdAndProvider`、`findAllByUserId`、`countByUserId`、`insertIfAbsent(ExternalIdentity): boolean`、`save`、`delete`、`deleteAllByUserId`

- [ ] 测试：ORCID `https://orcid.org/0000-0002-1825-0097` 与裸 iD 都规范化成裸 iD，`abc` 为空；`insertIfAbsent` 第二次（同 `(provider, subject)` 或同 `(user_id, provider)`）返回 `false` 且事务可继续；检查约束拒绝 `WECHAT`；到期注销后外部身份行没了
- [ ] 实现，`./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest --tests "*ExternalIdentity*"` 与 `checkMigrationColumns` 通过，提交

### Task 5: `ProviderAccount`、待补填记录与老用户登录用例

**Files:**
- Create: `domain/account/model/vo/{ProviderAccount,PendingRegistration}.java`、`domain/account/port/oauth/PendingRegistrationPort.java`、`app/account/usecase/oauth/{CompleteProviderLoginCommand,CompleteProviderLoginHandler,ProviderLoginResult}.java`、`app/account/service/NewDeviceLoginNotifier.java`、`infra/account/oauth/RedisPendingRegistrationStore.java`
- Modify: `LoginUserHandler`（抽出提醒）
- Test: `ProviderAccountTest`、`CompleteProviderLoginHandlerTest`、`RedisPendingRegistrationStoreIT`、`LoginUserHandlerTest` 仍绿

**Interfaces:**
- Produces: `ProviderAccount` 带 `@Builder`，紧凑构造器规范化 `suggestedUsername`（转小写；过不了 `^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$` 或纯数字或长度不在 3–30 就置空）；`ProviderLoginResult` sealed：`Authenticated(AuthenticatedUser)`、`RegistrationRequired(String ticket, PendingRegistration pending)`；`PendingRegistrationPort`：`String create(PendingRegistration)`、`Optional<PendingRegistration> find(String ticket)`、`void delete(String ticket)`

- [ ] 测试：GitHub `login` 为 `12345` 或 `Foo_` 时预填为空，`OctoCat` 为 `octocat`；老用户登录发不带密码证明的会话、更新 `account_label`、`last_login_at`；封禁 403；注销中照常；没有外部身份返回 `RegistrationRequired`，不写库；新设备发提醒
- [ ] 实现，跑通，提交

### Task 6: `oauth2Login` 接入

**Files:**
- Modify: `patra-identity-adapter/build.gradle.kts`、`patra-identity-boot/src/main/resources/application.yml`、`integrationTest/resources/application-test.yml`、`IdentityFlowITSupport`
- Create: `domain/account/port/oauth/AuthorizationRequestStorePort.java`、`infra/account/oauth/RedisAuthorizationRequestStore.java`、`adapter/account/oauth/{RedisAuthorizationRequestRepository,DiscardingAuthorizedClientRepository,ProviderAwareAuthorizationRequestResolver,ProviderAccountMapper,ProviderLoginSuccessHandler,ProviderLoginFailureHandler}.java`、`boot/config/{IdentityOAuth2ClientConfiguration,IdentityOAuth2SecurityConfiguration,IdentityOAuthProperties}.java`、`integrationTest/.../config/{IdentityITFakeProviderInitializer,FakeProviderFixture}.java`
- Test: `OAuth2LoginFlowIT`、`ProviderAccountMapperTest`、`ProviderLoginFailureHandlerTest`、`RedisAuthorizationRequestRepositoryTest`

**Interfaces:**
- Consumes: Task 1、Task 5
- Produces: 响应 JSON 见工程设计 2.2；`FakeProviderFixture.startLogin(provider): state`、`callback(provider, state, ...)`，供后续 IT 用

- [ ] 写 IT（失败）：`should_redirect_to_provider_without_session`、`should_return_registration_required_for_new_oidc_account`、`should_return_registration_required_for_new_github_account`、`should_not_set_jsessionid`、`should_keep_assertion_chain_for_me_paths`、`should_not_call_provider_on_startup`、`should_404_for_unknown_provider`
- [ ] 改 `build.gradle.kts`，向编排会话报告让用户点加载
- [ ] 实现配置与链（工程设计第 4、5、6 节），跑通，提交

### Task 7: 验证码存储与限流

**Files:**
- Create: `domain/account/model/vo/RegistrationCode.java`、`domain/account/policy/ProviderRegistrationPolicy.java`、`domain/account/port/throttle/RegistrationCodeThrottlePort.java`、`infra/account/throttle/RegistrationCodeThrottleAdapter.java`、`resources/redis/oauth-code-verify.lua`
- Modify: `PendingRegistrationPort`、`RedisPendingRegistrationStore`、`boot/config/IdentityConfiguration`
- Test: `RegistrationCodeTest`、`RedisPendingRegistrationStoreIT`、`RegistrationCodeThrottleAdapterIT`

**Interfaces:**
- Produces: `PendingRegistrationPort.storeCode(String ticket, PendingCode)`、`findCode(String ticket): Optional<PendingCode>`、`verifyCode(String ticket, String code): CodeCheck`；`CodeCheck` sealed：`Matched(username, email)`、`Mismatch`、`NoActiveCode`（过期或错满）

- [ ] 测试：错 5 次后键没了，正确的码也 `NoActiveCode`；对的码只能用一次；Redis 里 `code_hash` 不含原文；重发覆盖旧码；冷却与窗口
- [ ] 实现，跑通，提交

### Task 8: identity 侧三种邮件

**Files:** `domain/account/model/vo/mail/{RegistrationCodeMail,LoginMethodLinkedMail,LoginMethodUnlinkedMail}.java`、`identity-api/.../event/` 三个事件、`SecurityMailEvent`、`SecurityMailEventMapper`；notification 的 `SecurityMailKind`、`SecurityMailEventConverter`、`mail/html|text/` 六个模板（sealed 穷举，必须同一任务改完）

**Test:** `SecurityMailEventMapperTest`、`SecurityMailEventConverterTest`、`SecurityMailKindTest`、`ThymeleafMailRendererTest`、`SecurityMailFlowIT`

- [ ] 测试：路由键 `security-mail.registration-code` 等；`toString` 不含验证码；模板渲染含验证码、平台名、账号标识
- [ ] 实现，跑 notification 的 test 与 integrationTest，提交

### Task 9: 首次注册用例与接口

**Files:** `app/account/usecase/oauth/{SendRegistrationCodeCommand,SendRegistrationCodeHandler,RegistrationCodeSent,CompleteProviderRegistrationCommand,CompleteProviderRegistrationHandler}.java`、`app/account/usecase/query/PendingRegistrationQueryService.java`、`adapter/account/rest/ProviderRegistrationController.java` 及请求响应；`domain.session.exception.IdentityErrorTrait`、`ProviderAccountTakenException`、`RegistrationCodeLimitedException`

**Test:** 两个 Handler 单测、`ProviderRegistrationControllerIT`、`ProviderRegistrationFlowIT`（工程设计第 12 节五个用例 + Review Focus 4）

- [ ] 写流程测试（失败）：完整走通后 `email_verified_at` 有值、`idn_user_password_credential` 没有行、`auth_at` 不存在；邮箱撞车不发码；并发抢注回 409；日志和 Redis 无验证码原文
- [ ] 实现，跑通，提交

### Task 10: 绑定、解绑与列表

**Files:** `domain/account/policy/LoginMethodPolicy.java`、`domain/account/model/read/LoginMethodsReadModel.java`、异常；`app/account/usecase/oauth/{PrepareProviderLinkHandler,LinkProviderAccountHandler,UnlinkProviderAccountHandler}` 及命令；`app/account/usecase/query/LoginMethodQueryService.java`；`adapter/account/oauth/ProviderLinkAuthorizationStarter.java`、`adapter/account/rest/MeLoginMethodController.java`；`ProviderLoginSuccessHandler` 加 `LINK` 分支

**Test:** `LoginMethodPolicyTest`、Handler 单测、`MeLoginMethodControllerIT`、`ProviderLinkFlowIT`

- [ ] 写流程测试（失败）：别人的回调因会话不一致 403 且不写表；已绑给别人 409；重复绑自己幂等不发信；没有密码只剩一种 `LAST_LOGIN_METHOD`（先于再认证）；没有密码两种 `REAUTH_REQUIRED`；绑定、解绑各一封邮件；第三方登录后 `POST /me/deletion` 403 且 `hasPassword` 为假，经找回密码再登录、再认证后成功
- [ ] 实现，跑通，提交

### Task 11: 网关、部署与文档

**Files:** `GatewaySecurityConfiguration`、网关测试；`patra-infra/docker/docker-compose.apps.yaml`、`patra-infra/docker/README.md`；`patra-api/patra-identity/README.md`、`patra-api/patra-gateway-boot/README.md`、`patra-tool/patra-tool-security/README.md`、`patra-notification/README.md`

- [ ] 网关测试（失败）：四组新路径匿名可达，`/patra-identity/me/login-methods` 匿名 401
- [ ] 实现，跑网关测试，同步 README（接口表、错误码、Redis 键、会话字段、第 9 节 `auth_at`、邮件种数、新一节第三方登录），提交

### Task 12: 协议攻击与令牌留痕

**Files:** `OAuth2ProtocolAttackIT`、`TokenNotRetainedIT`，补充 `ProviderRegistrationFlowIT`

- [ ] D7：`state` 不存在、回放；`id_token` 错签名、错 nonce、错 `aud`、错 `iss`；用户拒绝；令牌端点 400 / 500 / 连不上。每个断言错误码、`idn_user` 无新行、Redis 无会话
- [ ] D10：流程后扫 Redis 全部键值、外部身份表，无 access token 字面量；`OAuth2AuthorizedClientService.loadAuthorizedClient` 为空
- [ ] Review Focus 5：第三方注册的账号找回密码后仍能第三方登录
- [ ] 发现的问题按 TDD 修，提交

### Task 13: 全量门控

- [ ] `./gradlew spotlessApply && ./gradlew check`
- [ ] identity 三个模块、notification boot、gateway、security 的 `integrationTest`
- [ ] `/simplify` 式自查与代码评审后修正；报告里写未验证项与学习入口
