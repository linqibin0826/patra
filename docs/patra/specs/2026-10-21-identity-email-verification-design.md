# identity 邮件发送与邮箱验证工程设计（PAP-96）

> **Issue**：[PAP-96](https://linear.app/papertrace/issue/PAP-96)（GitHub #262）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)（2.1.6 邮箱验证、2.1.7 一次性令牌、2.1.10 安全通知邮件、决策 C / D）
> **前置设计**：[identity 账号重建（PAP-93）](2026-10-20-identity-account-rebuild-design.md)（表结构、`IdentityErrorTrait`、注册处理器、第 16 节交给本 Issue 的约束）、[identity 会话（PAP-64）](2026-10-09-identity-session-design.md)（会话存储、Lua 约定）、[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)（公开名单）
> **日期**：2026-10-21
> **状态**：已实施（2026-10-21，分支 `feat/v0.12-user-center-api`）：服务商定为 Resend，待拍板 2 按建议选 (a)，3、4 按默认（第 20 节）；实测结果见第 16 节，mini 上的一次性步骤（第 14.4 节）随后端 PR 部署时做

## 1. 要解决的问题

系统现在没有发邮件的能力，所以没有邮箱验证、找回密码、改邮箱、安全通知（release spec「规划时的现状」）。本 Issue 一次建好发信链路和一次性令牌，并把邮箱验证做成闭环：注册即发验证邮件；未验证的用户可以重发（四个维度限流）；邮件里的链接把令牌放在 URL 片段里，由门户落地页 POST 消费；验证成功写 `email_verified_at`，并撤销该用户此前的其他会话（当前会话保留）。

本 Issue 交付之后，PAP-98（找回密码、改邮箱）、PAP-99（新设备邮件）、PAP-100（注销三封邮件）都只在这条链路上加东西：一种新邮件 = 一个 `OutgoingMail` 记录 + 两个模板；一种新令牌用途 = 枚举里已经有的一个值。

设计过程中定下的、影响别的 Issue 的事：

- 四种用途共用一张令牌表 `idn_email_token`，`EmailTokenRepository` 提供「消费（一条带条件的 `UPDATE … RETURNING`）」「按用户 + 用途作废」「按用户全部作废」，PAP-98 / PAP-100 直接调用（第 7.6 节）。
- 邮件发送端口收的是类型化的邮件对象 `OutgoingMail`（收件人、模板名、主题、模板变量），适配器按模板名渲染文本 + HTML 两部分并异步发出；安全通知邮件共用一个 `SecurityNotice` 片段（时间、设备、IP 属地、不是我怎么办）（第 9 节）。
- 会话存储加「撤销该用户除某一条之外的全部会话」，登录记录加结束原因 `REVOKED`；PAP-98 的改密码（保留本机）和 PAP-99 的踢设备用同一个方法（第 7.6 节）。
- 邮件链接一律 `{门户地址}{路径}#token=<令牌>`，门户落地页读片段后 POST 消费，不由 GET 消费；域名来自配置 `patra.identity.mail.links.base-url`，不取请求的 Host（第 9.5 节）。PAP-102 的三个落地页（验证邮箱、重设密码、撤销改邮箱）同一做法。
- 重发限流是一段参数化的 Lua（冷却 + 三个滑动窗口），时间由 Java 按注入的 `Clock` 传入；PAP-98 的找回密码限流复用同一段脚本（第 10 节）。

**完成标准**：PAP-96 的 Acceptance Criteria，逐条对应见第 15.5 节。

## 2. 范围

做：

- `V1` 加 `idn_email_token`；`idn_user_login_record.end_reason` 加 `REVOKED`；本机 `patra_identity` 库重建（决策 J）。
- 领域：`EmailToken` 聚合、`EmailTokenPurpose`、`EmailTokenSecret`、`EmailTokenRepository`；`EmailDeliveryPort` 与 `OutgoingMail` / `EmailVerificationMail` / `SecurityNotice`；`EmailResendThrottlePort` 与 `EmailResendPolicy`；`MailLinkPolicy`；领域服务 `EmailVerificationIssuer`；`SessionStorePort.revokeAllExcept`；两个新异常、一个新特征、一组新原因码。
- 应用：注册成功后签发令牌并发验证邮件；`ResendEmailVerificationHandler`；`VerifyEmailHandler`；`PurgeExpiredEmailTokensHandler`；`BanUserHandler` 封禁时作废该用户全部令牌（release spec 2.1.7）。
- 基础设施：令牌表的 Entity / Dao / Mapper / RepositoryAdapter；`SmtpEmailDeliveryAdapter` + `ThymeleafMailRenderer` + 模板；`EmailResendThrottleAdapter` + Lua；`SessionStoreAdapter.revokeAllExcept`；`patra-identity-session` 加 `deleteAllExcept` + Lua。
- 适配器：`EmailVerificationController`（`POST /auth/email/verify`）、`MeEmailController`（`POST /me/email/verification/resend`）；identity 第一次接 XXL-Job：`XxlJobConfig` + `EmailTokenCleanupScheduleJob`。
- 启动层：配置项、装配、发信用的异步执行器。
- 网关：`/patra-identity/auth/email/verify` 进公开名单，补集成测试。
- 测试工具：`patra-tool-test` 加 `GreenMailSmtpInitializer`（JVM 内单例的 SMTP 桩，和 Redis 容器初始化器同一用法）。
- `patra-infra`：core 栈加 Mailpit；`secrets/mail.env.example`；apps 栈的 identity 加载 `~/.patra/secrets/mail.env`；`patra-infra/docker/README.md`「密钥」「服务访问 URL」更新。
- identity README；Linear 与 release spec 的同步（第 19 节）。

不做（都已有归属）：

- 找回密码、改邮箱确认与撤销、改密码（PAP-98）。它们的令牌用途只在枚举和表的检查约束里预留，没有签发入口。
- 新设备邮件、设备名、IP 属地（PAP-99）。`SecurityNotice` 的设备、属地字段允许为空，渲染成「未知」；PAP-99 接上 Yauaa / ip2region 后只是把值填进去。
- 注销的三封邮件、到期任务（PAP-100）。
- 真实客户端 IP 的受信转发链路（PAP-99、决策 I）。本 Issue 的「请求 IP」取 `HttpServletRequest.getRemoteAddr()`（identity 开着 `forward-headers-strategy: framework`，网关加的 `X-Forwarded-For` 会被解析）；经门户 BFF 进来时这是门户容器的地址，所以每 IP 维度在链路接通前几乎等于全局维度，开发期可接受（第 10.4 节）。
- 发信失败的重试与持久化队列（outbox）。失败只记日志；用户可以重发；量级是单人项目的个位数用户。
- 退信、投诉回执、发信统计、通知偏好（边界 I）。
- 模板国际化：只有中文。
- 未验证账号的能力限制：主页 404、链接不展示归 PAP-95，发言限制归以后的 interaction。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 注册即登录；注册后发验证邮件；未验证期间顶部常驻提示 | 决策 C、2.1.6 |
| 验证链接 24 小时有效、一次性；重发使旧链接作废；重发冷却 60 秒，每账号每小时 ≤ 5 次、每天 ≤ 10 次，每 IP 每小时 ≤ 30 次 | 2.1.6 |
| 邮件链接不由 GET 消费：令牌放 URL 片段，落地页点按钮后 POST 消费 | 2.1.6 |
| 未验证的账号不自动清理 | 2.1.6 |
| 四种令牌共用一张表：32 字节 CSPRNG、base64url，库里只存 SHA-256；带用途、用户、发往的邮箱、过期 / 消费 / 撤销时间；同一用户同一用途只留一个有效令牌，签发前在同一事务里作废旧的；消费是一条带条件的 `UPDATE … RETURNING`；改密码、重设、改邮箱生效、注销、封禁时按用户批量作废；过期 30 天后删行 | 2.1.7 |
| 用途与有效期：邮箱验证 24 小时、找回密码 1 小时、改邮箱确认 1 小时、改邮箱撤销 7 天 | 2.1.6、2.1.7、PAP-96 Overview |
| 验证 / 重设时撤销此前全部会话（预劫持防线）；本 Issue 的验证保留当前会话 | 决策 C、PAP-96 To-Do |
| 安全通知邮件内容：时间、设备、IP 与属地，「不是我」该怎么办；不放登录链接，从不索要密码；链接域名取配置，不取 Host 头 | 2.1.10 |
| 发信：Spring Mail 走 SMTP，Thymeleaf 模板出文本 + HTML；开发环境接本地邮件收集器容器；mini 接真实服务商，凭据在 `~/.patra/secrets/`，仓库只留 `.example`；服务商在 mini 实测出站后定 | 决策 D |
| 发信异步，失败只记日志不抛给用户 | PAP-96 To-Do |
| `email_verified_at` 列与 `User.markEmailVerified(now)` 已有（幂等，`DELETED` 抛 409）；注册发信的钩子放在 `RegisterUserHandler` 事务提交之后 | PAP-93 第 7.1、16 节 |
| `/me/**` 写处理器先 `findByIdForUpdate` 再 `requireActive()`：`PENDING_DELETION` 409 带特征、`BANNED` / `DELETED` 401 | PAP-93 第 7.2、16 节 |
| 409 的原因码走 `traits[]`（`IdentityErrorTrait`），字段错误走 `errors[]`；429 带 `Retry-After` 与 `retryAfterSeconds`（`HasRetryAfter`） | PAP-93 第 10 节、`patra-tool-core` |
| 会话存储：Lua 原子执行，时间由 Java 按 `Clock` 传入、脚本不调 `TIME`，「没有结果」返回空表；只支持单机 Redis | PAP-64 设计、`patra-identity-session` README |
| 网关：`/patra-identity/**` 默认需登录，公开名单例外，公开路由上照样翻译令牌 | PAP-65 设计 |
| `V1` 是唯一一份脚本，各 Issue 在它上面原地改写并重建库；开发数据可丢弃 | 决策 J、PAP-93 第 6 节 |
| 随环境变的值（基础设施坐标、账号、密钥引用）在 Nacos（`patra-infra/nacos/dev/`，合入 main 时 CD 先推送再部署），jar 的 `application.yml` 只放随代码变的默认值，同一个键不两处写（`JarConfigOverlapCheckerTest`）；Nacos 里的密钥只写 `${变量}` 或 `${变量:公开默认值}`（`NacosConfigFilesTest`），值放 `~/.patra/secrets/*.env`，本机由 `patra-tool-launch` 读入、容器经 `env_file` 注入 | 配置迁移设计（2026-10-13）决策 1、`patra-hexagonal` 的配置指南、`patra-infra/nacos/dev/README.md`、`patra-infra/docker/README.md`「密钥」 |
| 定时任务：catalog 用 XXL-Job（执行器端口 = 服务端口 + 1，`xxl.job.admin.addresses` 在 Nacos 公共配置，测试里 `xxl.job.enabled: false`） | catalog 的 `XxlJobConfig` |
| 邮箱、密码、令牌不进日志；用户名可以 | PAP-93 第 10 节 |

版本：Java 25、Spring Boot 4.0.8（BOM：Jakarta Mail 2.1.5 / Angus Mail 2.0.5、Thymeleaf 3.1.5.RELEASE）、PostgreSQL 17、Redis 7.0.15、GreenMail 2.1.14（本 Issue 新增）、Mailpit v1.31.6（本 Issue 新增）、xxl-job-core 3.2.0（已有）。

## 4. 业界参照

决策层面的调研在 release spec 的「调研依据」里，这里只记工程层面照抄或对照的细节：

| 事 | 参照 | 本设计 |
|---|---|---|
| 令牌的生成与存放 | OWASP Forgot Password Cheat Sheet：令牌用 CSPRNG、足够长、一次性、短有效期、库里只存哈希；ASVS 5.0 认证一章对一次性验证码 / 令牌的随机性与时效要求 | 32 字节 `SecureRandom` → base64url 43 字符；库里存 SHA-256 十六进制；用途内一次性；有效期按用途 |
| 链接被邮件安全网关预取 | 企业邮箱的链接扫描（Outlook Safe Links 之类）会先 GET 一次邮件里的链接，把 GET 消费的一次性链接用掉；Supabase、Auth0 等把令牌放在 URL 片段里，片段不会随请求发给服务器 | 令牌只出现在 `#token=` 片段里；消费接口只收 POST |
| 验证 / 重设后撤销会话 | 预劫持论文（Sudhodanan & Paverd, USENIX Security 2022）的「经典联合」「未过期会话」两条路径 | 验证成功撤销该用户除当前会话外的全部会话 |
| 重发限流 | Redis 有序集合做滑动窗口日志是通用做法（成员 = 时间戳，`ZREMRANGEBYSCORE` 清窗口外，`ZCARD` 计数） | 冷却用字符串键、三个窗口用有序集合，一段 Lua 原子执行，时间由 Java 传入（和会话脚本一致） |
| 自动邮件的头 | RFC 3834 `Auto-Submitted: auto-generated`；Microsoft 的 `X-Auto-Response-Suppress: All` 抑制自动回复 | 两个头都加；不加 `List-Unsubscribe`（事务邮件） |
| 文本 + HTML 双模板 | Thymeleaf 官方的「Sending email in Spring with Thymeleaf」用 HTML 模式出 HTML、TEXT 模式出纯文本，`MimeMessageHelper.setText(text, html)` 组成 `multipart/alternative` | 同样做法；两个 `ClassLoaderTemplateResolver` 按模板名前缀（`html/`、`text/`）分流 |
| 开发环境收件桩 | Mailpit（SMTP + Web UI + REST API，单二进制容器）是 MailHog 的继任者 | compose core 栈加 Mailpit：1025 收信、8025 看信 |
| 集成测试的 SMTP 桩 | GreenMail 2.x：JVM 内启动、支持 Jakarta Mail 2.1、能从 Java 取出收到的 `MimeMessage` | 集成测试用 GreenMail，不起容器：无镜像拉取、可直接断言 MIME 结构 |

来源：[OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)、[OWASP ASVS 5.0](https://github.com/OWASP/ASVS/tree/v5.0.0/5.0/en)、[Pre-hijacked accounts](https://arxiv.org/abs/2205.10174)、[RFC 3834](https://www.rfc-editor.org/rfc/rfc3834)、[Thymeleaf + Spring 邮件教程](https://www.thymeleaf.org/doc/articles/springmail.html)、[Mailpit](https://mailpit.axllent.org/)、[GreenMail](https://greenmail-mail-test.github.io/greenmail/)。

## 5. 模块与改动面

没有新模块。改七个，另加一个测试工具类：

| 模块 | 改动 |
|---|---|
| `patra-identity-domain` | 聚合 `EmailToken`；枚举 `EmailTokenPurpose`；值对象 `EmailTokenSecret`；规则 `EmailResendPolicy`、`MailLinkPolicy`；领域服务 `EmailVerificationIssuer`；端口 `EmailTokenRepository`（`port/repository/`）、`EmailDeliveryPort` 与 `OutgoingMail` / `EmailVerificationMail` / `SecurityNotice`（`port/mail/`）、`EmailResendThrottlePort`（`port/throttle/`）、`SessionStorePort` 加 `revokeAllExcept`；`LoginEndReason` 加 `REVOKED`；异常 `EmailAlreadyVerifiedException`、`EmailResendLimitedException`；`IdentityErrorTrait` 加 `EMAIL_ALREADY_VERIFIED`；`UserFieldViolations` 加 `token` 的 `REQUIRED` / `INVALID` |
| `patra-identity-app` | `RegisterUserCommand` 加 `clientIp`，`RegisterUserHandler` 在事务里签发令牌、提交后发信；新增 `usecase/email/`：`ResendEmailVerificationCommand` / `Handler` / `EmailVerificationResent`，`VerifyEmailCommand` / `Handler` / `EmailVerified`，`PurgeExpiredEmailTokensCommand` / `Handler`；`BanUserHandler` 加作废令牌 |
| `patra-identity-infra` | `EmailTokenEntity` / `EmailTokenDao` / `EmailTokenJpaMapper` / `EmailTokenRepositoryAdapter`；`adapter/mail/`：`SmtpEmailDeliveryAdapter`、`ThymeleafMailRenderer`、`RenderedMail`；`adapter/throttle/EmailResendThrottleAdapter` + `redis/email-resend-throttle.lua`；`SessionStoreAdapter.revokeAllExcept`；`V1` 改写；`UserLoginRecordEntity` 注释；模板 `mail/html/**`、`mail/text/**`；依赖加 `spring-boot-starter-mail`、`org.thymeleaf:thymeleaf` |
| `patra-identity-session` | `RedisSessionStore.deleteAllExcept` + `redis/session-delete-others.lua`；README |
| `patra-identity-adapter` | `rest/auth/EmailVerificationController` 及请求 / 响应记录；`rest/me/MeEmailController` 及响应记录；`scheduler/config/XxlJobConfig`、`scheduler/job/EmailTokenCleanupScheduleJob`；`AuthController.register` 读请求 IP；依赖加 `libs.xxl.job` |
| `patra-identity-boot` | `IdentityProperties` 加 `mail` 段；`IdentityConfiguration` 装配 `EmailResendPolicy`、`MailLinkPolicy`、`EmailVerificationIssuer`、`ThymeleafMailRenderer`、`EmailDeliveryPort`（`SmtpEmailDeliveryAdapter.async`）；`application.yml` 加 `spring.mail.*`、`patra.identity.mail.*`、`xxl.job.executor.*`；集成测试配置加 `xxl.job.enabled: false`；README |
| `patra-gateway-boot` | `IDENTITY_PUBLIC_PATHS` 加 `/patra-identity/auth/email/verify`；`GatewayAuthenticationIT` 加两条 |
| `patra-tool-test` | `dev.patra.tool.test.mail.GreenMailSmtpInitializer`：JVM 内单例的 GreenMail SMTP（随机端口），把 `spring.mail.host` / `port` 写进环境；依赖加 `com.icegreen:greenmail` |
| `patra-infra` | `docker-compose.core.yaml` 加 `mailpit`；`docker-compose.apps.yaml` 的 identity 加 `mail.env`（`required: false`）；`secrets/mail.env.example`；README |

包位置按 `.claude/rules/tech/port-service.md`：端口 `domain/port/{repository|mail|throttle|session}/`，实现 `infra/adapter/{persistence|mail|throttle|session}/`，规则 `domain/policy/`，领域服务 `domain/service/`。

## 6. 表结构（`V1` 增量）

### 6.1 `idn_email_token`

一条签发出去的一次性令牌。带 `BaseJpaEntity` 的审计列、`set_updated_at()` 触发器和列注释，写法照现有五张表。

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键，雪花 ID |
| `user_id` | `BIGINT` | 非空；外键 `fk_idn_email_token_user` → `idn_user(id)` |
| `purpose` | `VARCHAR(32)` | 非空；`ck_idn_email_token_purpose`：`IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'EMAIL_CHANGE_CONFIRM', 'EMAIL_CHANGE_REVERT')` |
| `email` | `VARCHAR(254)` | 非空，发往的邮箱（规范化小写）；`ck_idn_email_token_email_lowercase`。`EMAIL_VERIFY` / `PASSWORD_RESET` / `EMAIL_CHANGE_REVERT` 是用户当时的邮箱，`EMAIL_CHANGE_CONFIRM` 是待确认的新邮箱（PAP-98） |
| `token_hash` | `CHAR(64)` | 非空；唯一 `uk_idn_email_token_hash`；`ck_idn_email_token_hash_format`：`token_hash ~ '^[0-9a-f]{64}$'` |
| `expires_at` | `timestamptz(6)` | 非空 |
| `consumed_at` | `timestamptz(6)` | 可空 |
| `revoked_at` | `timestamptz(6)` | 可空；`ck_idn_email_token_settled_once`：`consumed_at IS NULL OR revoked_at IS NULL`（消费和作废至多发生一个） |
| `requested_ip` | `VARCHAR(45)` | 可空，签发这条令牌的请求 IP 文本（IPv6 最长 45 个字符）；拿不到时为空 |

索引：

- `uk_idn_email_token_active`：`UNIQUE (user_id, purpose) WHERE consumed_at IS NULL AND revoked_at IS NULL AND purpose <> 'EMAIL_CHANGE_REVERT'`——「同一用户同一用途只留一个有效令牌」。过期但没消费、没作废的行在这个索引眼里仍是「有效」（过期是按时间判断的，进不了部分索引的谓词），所以签发前必须在同一事务里先作废旧的；这条索引是兜底，不是主要机制。改邮箱的撤销令牌（`EMAIL_CHANGE_REVERT`）不在约束内：连续改两次邮箱（X → Y → Z）要让发给 X、发给 Y 的两条「不是我」链接各自活满 7 天，否则知道密码的攻击者改两次就能让原主无法撤回（2026-10-21 定，第 20 节已拍板 2）。
- `idx_idn_email_token_user_id (user_id)`：按用户批量作废。
- `idx_idn_email_token_expires_at (expires_at)`：清理任务。

`requested_ip` 用文本列而不是 `inet`、也不复用审计列 `ip_address bytea`：审计列目前没有任何写入路径（五个 Mapper 都 `ignore`），`inet` 要给 Hibernate 配专门的类型映射，而这一列只用于审计查看和事后排查，文本够用。PAP-99 给登录记录加 IP 时用同样的文本列，两张表一致。

### 6.2 `idn_user_login_record`

`ck_idn_user_login_record_end_reason` 加 `'REVOKED'`：验证邮箱时撤销的其他会话记成它。PAP-99 的踢设备、PAP-98 改密码撤销的其他会话也用它。列注释同步。

## 7. 领域模型

### 7.1 聚合与值对象

| 名称 | 类别 | 内容和行为 |
|---|---|---|
| `EmailToken` | 聚合根 | ID、用户 ID、用途、发往邮箱、`tokenHash`、`expiresAt`、`consumedAt`、`revokedAt`、`requestedIp`、版本、审计时间。`issue(purpose, userId, email, secret, now, requestedIp)`：`expiresAt = now + purpose.validity()`，`tokenHash = secret.hash()`；`restore()` 是 Lombok 构建器（`@Builder(builderMethodName = "restore")`，12 个字段）；`isActive()`：没消费也没作废；`isUsableAt(now)`：`isActive()` 且 `now < expiresAt`；`markConsumed(now)` / `markRevoked(now)` 只在单测和 Mapper 里用——真正的消费与作废是仓储的条件更新（7.6），聚合不负责并发 |
| `EmailTokenPurpose` | 枚举 | `EMAIL_VERIFY`（24h）、`PASSWORD_RESET`（1h）、`EMAIL_CHANGE_CONFIRM`（1h）、`EMAIL_CHANGE_REVERT`（7d）；`validity()` 返回有效期。有效期是 release spec 定死的数，不进配置 |
| `EmailTokenSecret` | 值对象 | 令牌原文：32 字节 `SecureRandom` → base64url 无填充，恰好 43 个字符。`generate(SecureRandom)`；`parse(raw)` 只接受 43 个 `[A-Za-z0-9_-]`，否则空（格式不对的输入不查库）；`hash()`：原文 UTF-8 的 SHA-256 小写十六进制；`toString()` 只输出 `EmailTokenSecret[***]`。和 `SessionToken` 同一套做法，只是没有前缀 |
| `EmailResendPolicy` | 值对象 | `cooldown`（60 秒）、`perAccountHourly`（5）、`perAccountDaily`（10）、`perIpHourly`（30）、`hourWindow` = 1 小时、`dayWindow` = 24 小时；构造时校验正数；`nextResendAt(now) = now + cooldown` |
| `MailLinkPolicy` | 值对象 | `baseUrl`（绝对的 `http(s)` 地址，构造时去掉末尾 `/`，不合法启动失败）、`verifyEmailPath`、`securitySettingsPath`（都以 `/` 开头）。`verifyEmailUrl(EmailTokenSecret)` = `baseUrl + verifyEmailPath + "#token=" + secret.value()`；`securitySettingsUrl()`。所有邮件里的站内链接都从这里出，不读请求 |
| `SecurityNotice` | 记录（`port/mail/`） | 安全通知邮件共用的上下文：`occurredAt`（必填）、`device`（可空）、`ipAddress`（可空）、`ipLocation`（可空）、`notMeAdvice`（必填，这封邮件专属的「不是我怎么办」一句话）。`@Builder`。本 Issue 只定义和渲染，没有邮件用它 |
| `OutgoingMail` | 接口（`port/mail/`） | `EmailAddress to()`、`String template()`（模板名，如 `email-verification`）、`String subject()`、`Map<String, Object> model()`（模板变量）。新邮件类型实现它即可，适配器不认识具体类型 |
| `EmailVerificationMail` | 记录，实现 `OutgoingMail` | `to`、`displayName`、`verifyUrl`、`expiresAt`；`template()` 为 `email-verification`，`subject()` 为「验证你的 Patra 邮箱」，`model()` 给出 `displayName`、`verifyUrl`、`validHours`（24）；4 个字段用 `of()` |
| `EmailVerificationIssuer` | 领域服务（`domain/service/`） | `prepare(User user, String displayName, String requestedIp) → PreparedVerificationMail`：取用户当前邮箱（没有邮箱抛 `IllegalStateException`，调用方已保证不是 `DELETED`）→ `tokens.revokeActive(userId, EMAIL_VERIFY, now)` → 生成 `EmailTokenSecret` → `tokens.save(EmailToken.issue(...))` → 用 `MailLinkPolicy` 拼链接，返回令牌和 `EmailVerificationMail`。必须在调用方的事务里执行；令牌原文只在返回值里出现一次，不记日志。依赖 `EmailTokenRepository`、`MailLinkPolicy`、`Clock`、`SecureRandom` |
| `PreparedVerificationMail` | 记录 | `EmailToken token`、`EmailVerificationMail mail`。`toString()` 不输出邮件内容 |

`LoginEndReason` 加 `REVOKED`（「被本人的另一次操作撤销：验证邮箱、改密码、踢设备」）。`IdentityErrorTrait` 加 `EMAIL_ALREADY_VERIFIED`。`UserFieldViolations` 加字段名 `TOKEN = "token"`、原因码 `INVALID = "INVALID"`，以及 `tokenRequired()`、`tokenInvalid()`。

### 7.2 为什么令牌原文不过持久层

`EmailTokenSecret` 只在三个地方活着：签发时的返回值、邮件正文里的链接、消费请求的请求体。仓储的 `save` 收的是聚合（只有哈希），`consume` 收的是哈希。于是「库里不存明文」不靠纪律，靠类型：持久层的任何方法都拿不到 `EmailTokenSecret`。

### 7.3 为什么是条件更新而不是「读出来再改」

两个请求拿同一个令牌同时消费，「`SELECT` 再 `UPDATE`」会都成功。`UPDATE … WHERE token_hash = ? AND purpose = ? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > ? RETURNING …` 在 PostgreSQL 里是原子的：行锁让第二个 `UPDATE` 等第一个提交，再按新值重新判断 `WHERE`，于是只有一个拿到行。消费逻辑因此不在聚合里（聚合的 `markConsumed` 只给单测和 Mapper 用），这是有意的：并发正确性放在数据库这一层最便宜。

### 7.4 哪些操作作废令牌

| 时机 | 调用 | 归属 |
|---|---|---|
| 重发验证邮件 | `revokeActive(userId, EMAIL_VERIFY)` | 本 Issue |
| 封禁 | `revokeAllActive(userId)` | 本 Issue（`BanUserHandler`） |
| 改密码、重设密码、改邮箱生效、「不是我」 | `revokeAllActive` 或按用途 | PAP-98 |
| 注销申请、到期执行 | `revokeAllActive` | PAP-100 |

`revokeAllActive` **不动 `EMAIL_CHANGE_REVERT`**：原主旧邮箱里的「不是我」链接必须活过改密码、重设密码、再次改邮箱、封禁这些事件，否则攻击者抢到账号后随便做一件就能让它失效；只有注销（邮箱置空，撤回已无意义）由 PAP-100 显式 `revokeActive(userId, EMAIL_CHANGE_REVERT, now)` 清掉，以及 PAP-98 消费一条撤销令牌后作废该用户其余的撤销令牌。

解封不恢复令牌：用户重发即可。

### 7.5 哪些操作撤销会话

| 时机 | 调用 | 结束原因 | 归属 |
|---|---|---|---|
| 验证邮箱 | `revokeAllExcept(USER, userId, 当前会话)` | `REVOKED` | 本 Issue |
| 改密码（保留本机）、重设密码（全部） | `revokeAllExcept` / `revokeAll` | `REVOKED` | PAP-98 |
| 踢单个 / 其他全部 | `revoke` / `revokeAllExcept` | `REVOKED` | PAP-99 |
| 封禁、注销 | `revokeAll` | `BANNED` / `DELETION_REQUESTED` | 已有 / PAP-100 |

### 7.6 端口

| 端口 | 实现（infra） | 方法 |
|---|---|---|
| `EmailTokenRepository`（新） | `EmailTokenRepositoryAdapter` | `save(EmailToken)`（新行分配雪花 ID；撞 `uk_idn_email_token_active` 抛 `UserModifiedConcurrentlyException`——两个并发签发都先作废旧的，理论上进不来，这是兜底）；`findByTokenHash(String tokenHash) → Optional<EmailToken>`：不加锁按哈希查，验证流程用它先找到用户、锁用户行，再消费（第 8.3 节）；`consume(String tokenHash, EmailTokenPurpose, Instant now) → Optional<EmailToken>`：一条 `UPDATE … RETURNING`，适配器用 `JdbcClient` 执行、不经 JPA（绕开一级缓存，拿到的就是更新后的行），没命中返回空，不区分不存在 / 过期 / 已消费 / 已作废；`revokeActive(userId, purpose, now) → int`、`revokeAllActive(userId, now) → int`：JPQL 批量 `UPDATE`，只动 `consumed_at IS NULL AND revoked_at IS NULL` 的行，`revokeAllActive` 跳过 `EMAIL_CHANGE_REVERT`（第 7.4 节）；`deleteExpiredBefore(Instant cutoff, int batchSize) → int`：`DELETE … WHERE id IN (SELECT id … WHERE expires_at < ? LIMIT ?)`，返回删掉的行数 |
| `EmailDeliveryPort`（新，`port/mail/`） | `SmtpEmailDeliveryAdapter` | `send(OutgoingMail)`：立即返回；渲染和发送在适配器自己的执行器上做；任何失败只记日志，不抛给调用方 |
| `EmailResendThrottlePort`（新，`port/throttle/`） | `EmailResendThrottleAdapter` | `acquire(userId, Optional<String> ip, Instant now) → Optional<Duration>`：四个维度一起判，空表示放行且已计数，有值是要等多久；`markSent(userId, now)`：只计冷却和两个按账号的窗口，不判、不计 IP（注册的首封；每 IP 维度只管重发）。Redis 暂时不可用抛 `TemporarilyUnavailableException`（503） |
| `SessionStorePort`（扩展） | `SessionStoreAdapter` → `RedisSessionStore.deleteAllExcept` | `revokeAllExcept(accountType, userId, Optional<Long> keepSessionId) → List<Long>`：删掉该用户除指定一条外的全部会话，返回真的删掉了的会话 ID；`keepSessionId` 为空等于 `revokeAll`，但索引键保留（Redis 删空哈希时会自动删键） |
| `UserRepository` / `UserReadPort` / `UserLoginRecordRepository` | 不变 | — |

## 8. 接口与流程

### 8.1 注册 `POST /auth/register`（改）

请求、响应、状态码都不变。`RegisterUserCommand` 加 `clientIp`（`AuthController` 取 `request.getRemoteAddr()`）。处理器在原事务的最后一步加「签发令牌」，事务提交之后加「计数」和「发信」：

1. 校验、查占用、哈希：照旧。
2. 事务里：存用户 → 存资料 → 存凭据 → 建会话 → 读账号信息 → `emailVerificationIssuer.prepare(user, account.displayName(), clientIp)`。
3. 事务提交后：`emailResendThrottle.markSent(userId, now)`——只计冷却与两个按账号的窗口、不判也不计 IP（每 IP 维度只管重发；PAP-99 之前 IP 是门户地址，注册也计进去会让全站的注册和重发共用 30 次），失败只记 WARN（注册不能因为计数失败而失败；Redis 真挂了建会话那一步已经 503 了）→ `emailDelivery.send(prepared.mail())`。
4. 返回不变。

首封计入冷却和两个按账号的窗口：注册后立刻点「重发」会得到 429 + `Retry-After: 60`，而不是一秒内两封。

### 8.2 重发验证邮件 `POST /me/email/verification/resend`

无请求体。网关：`/me/**` 需登录。`ResendEmailVerificationHandler`，顺序：

1. `currentUserPort.require()`。
2. 不加锁读用户：不存在 401；`requireActive()`（`PENDING_DELETION` 409 带 `PENDING_DELETION`，`BANNED` / `DELETED` 401）；已验证 409 `EmailAlreadyVerifiedException`（`traits` 含 `EMAIL_ALREADY_VERIFIED`）。先判状态再限流：已验证的人乱点不消耗配额。
3. `emailResendThrottle.acquire(userId, clientIp, now)`：有值抛 `EmailResendLimitedException(等待时长)`（429，`Retry-After`）。放行即已计数——之后的步骤失败也不退还，宁可少发不多发。
4. 事务里：`users.findByIdForUpdate(userId)`（同一用户的两次并发重发串行，第二次在锁后重判）→ 再次 `requireActive()`、再次判已验证 → 读账号信息取昵称 → `emailVerificationIssuer.prepare(...)`（作废旧令牌、存新令牌）。
5. 事务提交后：`emailDelivery.send(prepared.mail())`。
6. 返回 `EmailVerificationResent(email, nextResendAt)`。

响应 `202 Accepted`：

```json
{ "email": "chen.yu@example.com", "nextResendAt": "2026-10-21T08:01:00Z" }
```

`nextResendAt` 只反映 60 秒冷却；小时 / 天 / IP 窗口满了由 429 的 `Retry-After` 说。202 而不是 200：邮件是异步发的，接口只保证「已签发并排队」。

### 8.3 验证邮箱 `POST /auth/email/verify`

请求体 `{ "token": "<43 个字符>" }`。网关：公开，但照样翻译令牌，所以带着会话来的请求有当前用户。`VerifyEmailHandler`：

1. `token` 为空 → 422 `token` / `REQUIRED`；`EmailTokenSecret.parse` 失败 → 422 `token` / `INVALID`，不查库。
2. 当前用户 = `currentUserPort.current()`（匿名为空）。
3. 事务里：
   1. `tokens.findByTokenHash(secret.hash())`（不加锁）：空，或 `purpose` 不是 `EMAIL_VERIFY` → 422 `token` / `INVALID`。
   2. `users.findByIdForUpdate(token.userId)`：不存在 → 422 `token` / `INVALID`。**先锁用户行再动令牌行**：重发（第 8.2 节第 4 步）和封禁（先 `save` 用户再 `revokeAllActive`）都是这个顺序，同一用户并发「验证 + 重发」「验证 + 封禁」才不会死锁（builder check 指出）。
   3. `tokens.consume(secret.hash(), EMAIL_VERIFY, now)`：空（这期间过期、被重发作废、被另一个请求抢先消费）→ 422 `token` / `INVALID`。
   4. 用户当前邮箱与令牌的 `email` 不同（期间改过邮箱）→ 422 `token` / `INVALID`。抛出让事务回滚、消费撤回也无妨：这条令牌对当前邮箱已经无效，再来还是 422。
   5. `user.markEmailVerified(now)`（幂等；`DELETED` 抛 409，但 `DELETED` 的邮箱为空、上一步已经 422）→ `users.save(user)`。
   6. `keep` = 当前用户存在且 `current.userId() == token.userId()` 时的 `current.sessionId()`，有则 `sessions.revokeAllExcept(USER, userId, keep)`，没有则 `sessions.revokeAll(USER, userId)`；被删会话的登录记录 `end(REVOKED, now)`。Redis 失败整个事务回滚、503，和封禁一致：令牌没被消费，用户重试。
4. 返回 `EmailVerified(email, emailVerifiedAt)`。

响应 `200`：

```json
{ "email": "chen.yu@example.com", "emailVerifiedAt": "2026-10-21T08:03:12Z" }
```

- 在未登录的浏览器（手机）里点链接：没有当前用户，该用户的全部会话被撤销——注册时那台电脑要重新登录。这正是决策 C 要的预劫持防线：验证的人证明了对邮箱的控制，验证前建立的会话一律不再信任。
- 以别人的身份登录着去消费这个令牌：令牌属于谁就验证谁、撤销谁的会话，请求者的会话不受影响。
- 「无效、过期、已用、已作废」一律同一个 422，不区分原因（和 PAP-93 公开身份卡的 404 同一个理由：不给任何能用来探测的信号）。门户文案「链接无效或已过期，请重新发送」。

### 8.4 过期令牌清理

`PurgeExpiredEmailTokensHandler`：`cutoff = now − 保留期`，循环 `tokens.deleteExpiredBefore(cutoff, 1000)` 直到一批不足 1000，每批一个事务，返回总数。保留期由 `EmailTokenCleanupScheduleJob`（`@XxlJob("emailTokenCleanupJob")`，无任务参数）从 `patra.identity.mail.token-retention`（30 天）读出、放进命令（app 层不依赖 boot 的配置类）；任务 `XxlJobHelper.handleSuccess("已删除过期令牌 N 行")`。调度由 mini 的 xxl-job-admin 负责：每天 04:30 一次（第 14.4 节）。

用 XXL-Job 而不是 Spring `@Scheduled`：和 catalog 一致；PAP-99（登录记录清理）、PAP-100（注销到期执行）还有两个任务，D6 验收要「把到期时间改到过去再触发」，XXL-Job 的手动触发和执行日志正好用上。代价是 identity 多一个执行器端口（6401）和 mini 上一次性的执行器注册。

### 8.5 封禁 `POST /admin/users/{userId}/ban`（改）

保存用户之后多一步 `tokens.revokeAllActive(userId, now)`，和撤销会话在同一个事务里。

## 9. 邮件发送链路

### 9.1 适配器与异步

`SmtpEmailDeliveryAdapter implements EmailDeliveryPort`，依赖 `JavaMailSender`（Boot 的 `MailSenderAutoConfiguration` 按 `spring.mail.*` 装配）、`ThymeleafMailRenderer`、一个执行器（见下）、发件人配置。

`send(mail)`：`executor.execute(() -> deliver(mail))` 后立即返回。`deliver`：渲染 → `MimeMessageHelper(message, true, "UTF-8")`：`setFrom(from)`、`setTo`、`setSubject`、`setText(text, html)`（`multipart/alternative`，纯文本在前）、加头 `Auto-Submitted: auto-generated`、`X-Auto-Response-Suppress: All` → `mailSender.send`。任何 `RuntimeException`（含 `MailException`）和 `MessagingException` 捕获后 `log.warn("邮件发送失败: template={}, userId 由调用方记", …, e)`，不重试、不抛。

执行器由适配器自己持有：`SmtpEmailDeliveryAdapter.async(mailSender, renderer, from, 4)` 建一个 `SimpleAsyncTaskExecutor`——虚拟线程、线程名前缀 `mail-`、并发上限 4（一次 SMTP 会话几百毫秒，上限只防突发把连接打满）、`taskTerminationTimeout` 10 秒（停机时等在途的几封发完）；适配器实现 `AutoCloseable`，容器销毁时关掉它。不把执行器声明成 Bean：容器里多一个 `Executor` 类型的 Bean 会让 Boot 的 `applicationTaskExecutor` 退让（CQRS 自动配置为此专门排在它之后）。也不复用 CQRS 的 `commandBusAsyncExecutor`：那是平台线程池、有队列容量，语义是命令总线的。

公开构造器接任何 `Executor`：传同步执行器就是同步发送，单测这样用。

Boot 见到 `JavaMailSender` 会自动注册邮件健康检查（`MailHealthIndicator`：每次 `/actuator/health` 连一次 SMTP，开了鉴权就是一次真实登录）。关掉它（`management.health.mail.enabled: false`）：容器 healthcheck 每 10 秒一次等于一天八千多次登录服务商，CD 又按健康结果决定是否回滚——Mailpit 没起或服务商抖动都不该让 identity 部署失败，这和「发信失败只记日志」是同一条原则（builder check 指出）。

### 9.2 渲染：Thymeleaf 双模板

`ThymeleafMailRenderer` 自己构造一个 `TemplateEngine`（不用 `spring-boot-starter-thymeleaf`：它会给 MVC 注册视图解析器，identity 没有页面；也不用把引擎做成 Bean：只有这一个使用方），两个 `ClassLoaderTemplateResolver`：

| 解析器 | `resolvablePatterns` | 前缀 / 后缀 | 模式 |
|---|---|---|---|
| HTML | `html/*` | `mail/` / `.html` | `HTML` |
| 文本 | `text/*` | `mail/` / `.txt` | `TEXT` |

`render(OutgoingMail) → RenderedMail(subject, text, html)`：模板名 `html/<template>` 与 `text/<template>` 各渲染一次；上下文 = `mail.model()` + 公共变量 `siteName`（Patra）、`securityUrl`（`MailLinkPolicy.securitySettingsUrl()`）；`model()` 里的 `SecurityNotice` 由渲染器换成展示用的 `SecurityNoticeView`（`occurredAt` 按配置时区 `Asia/Shanghai` 格式成 `yyyy-MM-dd HH:mm (UTC+8)`，空的设备 / 属地 / IP 显示「未知」）。模板里的用户输入（昵称）由 Thymeleaf 的 `th:text` / `[[…]]` 转义，HTML 注入测试覆盖。

文件布局（`patra-identity-infra/src/main/resources/mail/`）：

```
html/
  email-verification.html
  fragments/
    layout.html            # header(title) / footer()：站名、固定提示「Patra 不会在邮件里向你索要密码」「系统邮件请勿回复」
    security-notice.html   # notice(view)：时间、设备、IP 属地、不是我怎么办 + 安全设置链接
text/
  email-verification.txt
  fragments/
    layout.txt
    security-notice.txt
```

HTML 模板：内联样式、单列 600px、一个主按钮 + 一行可复制的完整链接（邮件客户端禁用按钮样式时仍可用）。文本模板：Thymeleaf TEXT 模式（`[(${…})]`、`[# th:if]`）。

### 9.3 本 Issue 的邮件

| 模板 | 主题 | 变量 | 正文要点 |
|---|---|---|---|
| `email-verification` | 验证你的 Patra 邮箱 | `displayName`、`verifyUrl`、`validHours` | 「你好，{昵称}」；按钮「验证邮箱」+ 完整链接；「链接 24 小时内有效、只能用一次」；「如果不是你注册的 Patra，忽略这封邮件即可，不要点链接」；页脚 |

它不是安全通知，不带 `SecurityNotice` 片段。

### 9.4 安全通知邮件框架（给 PAP-98 / 99 / 100）

一封安全通知 = 一个实现 `OutgoingMail` 的记录（字段里有一个 `SecurityNotice`，`model()` 把它放在键 `notice` 下）+ `html/<template>.html` 与 `text/<template>.txt` 各一份，正文里 `th:replace="~{html/fragments/security-notice :: notice(${notice})}"`。片段固定输出四项：发生时间、设备（PAP-99 前为「未知」）、IP 与属地（同上）、「不是我怎么办」= 该邮件的 `notMeAdvice` 一句话 + 固定一句「建议立即到安全设置修改密码并检查登录设备：{securityUrl}」。按 2.1.10：不放登录链接，从不索要密码。

| 后续邮件 | 归属 | `notMeAdvice` 示例 |
|---|---|---|
| 新设备登录 | PAP-99 | 「如果不是你，请立即修改密码并踢掉该设备」 |
| 密码已修改 / 已重设 | PAP-98 | 「如果不是你，你的邮箱可能已被盗，请先找回邮箱再重设密码」 |
| 改邮箱：新邮箱确认（带链接）、旧邮箱通知（带 7 天「不是我」链接）、生效后新旧各一封 | PAP-98 | 旧邮箱通知的「不是我」就是撤销链接 |
| 注销已提交 / 已取消 / 已完成 | PAP-100 | 「如果不是你，请在到期前登录取消」 |

带链接的邮件（重设密码、撤销改邮箱）在 `MailLinkPolicy` 上加各自的路径（`reset-password-path`、`revert-email-change-path`），链接格式同验证邮件。

### 9.5 链接

所有站内链接由 `MailLinkPolicy` 生成：`patra.identity.mail.links.base-url` + 路径 + `#token=<令牌>`。片段不随 HTTP 请求发出，邮件网关预取、门户访问日志、Referer 都拿不到令牌；门户落地页（PAP-102）在浏览器里读 `location.hash`，点按钮后把令牌放请求体 POST 到门户的 Route Handler，再转发到网关 `/patra-identity/auth/email/verify`（带上会话 Cookie 换出的令牌，这样本机会话才能被保留）。`base-url` 不取请求的 `Host` / `X-Forwarded-Host`：identity 永远不从请求里取主机名拼链接，集成测试用伪造的 `Host` 头证明（第 15.4 节）。

### 9.6 日志与隐私

- 签发：`log.info("验证邮件已签发: userId={}, tokenId={}, expiresAt={}")`，没有邮箱、没有令牌。
- 发出 / 失败：`template`、是否成功、异常摘要；收件人不进日志。
- `OutgoingMail` 的实现类 `toString()` 不输出链接和收件人。
- 调试邮件内容看 Mailpit，不看日志。

## 10. 重发限流

### 10.1 维度与键

| 维度 | 规则 | Redis 键 | 类型 |
|---|---|---|---|
| 冷却 | 两次发送间隔 ≥ 60 秒 | `idn:email-resend:cooldown:user:{userId}` | 字符串，值 = 上次发送的 epoch 毫秒，`PX` = 冷却时长 |
| 每账号每小时 | ≤ 5 | `idn:email-resend:hour:user:{userId}` | 有序集合：分数 = 发送时刻，成员 = `时刻:随机串` |
| 每账号每天 | ≤ 10 | `idn:email-resend:day:user:{userId}` | 同上 |
| 每 IP 每小时 | ≤ 30 | `idn:email-resend:hour:ip:{sha256(ip) 前 32 位十六进制}` | 同上；IP 未知时跳过这一维 |

用户 ID 可以进键名（不是个人信息）；IP 哈希后进键名，和登录限流哈希邮箱同一考虑。

### 10.2 脚本 `email-resend-throttle.lua`

`KEYS`：四个键（IP 未知时第四个传占位键并用参数标记跳过）。`ARGV`：`now`（毫秒）、冷却毫秒、小时窗口毫秒、天窗口毫秒、三个上限、`enforce`（1 判且计，0 只计）、`ipPresent`、成员随机后缀。

```
wait = 0
if enforce == 1:
  last = GET cooldown                          -- 没有则 0
  if last > 0 and now - last < cooldown: wait = max(wait, last + cooldown - now)
  for 每个窗口键 (hour, day, ip 若存在):
    ZREMRANGEBYSCORE key -inf (now - window)   -- 清窗口外
    if ZCARD key >= limit:
      oldest = ZRANGE key 0 0 WITHSCORES 的分数
      wait = max(wait, oldest + window - now)
  if wait > 0: return wait
SET cooldown now PX cooldown
ZADD hour now member; PEXPIRE hour window      -- 天、IP 同样
return 0
```

- 滑动窗口：「任意连续一小时 ≤ 5 次」，比按自然小时切的固定窗口严格，也不会在整点边界放两倍。
- 返回的是四个维度里**最长**的等待：`Retry-After` 要保证照着等完就能通过。
- 时间由 Java 按注入的 `Clock` 传入，脚本不调 `TIME`：固定时钟下窗口可以被拨过去，集成测试不用真等。键的 `PEXPIRE` 用真实时间，只是回收，不参与判断（测试跑不到一小时）。
- 返回值永远是数字，不返回 `false`（Lettuce 会解成 `[null]`）。

### 10.3 异常

`EmailResendLimitedException(Duration retryAfter)`：`QUOTA_EXCEEDED` + `HasRetryAfter`，429，`Retry-After` 向上取整到秒、`retryAfterSeconds` 同值，`detail`「发送过于频繁，请稍后再试」。不区分是哪个维度：`Retry-After` 已经告诉门户要等多久；要文案区分时门户按秒数大小选（≤ 60 秒是冷却，更大是配额）。

### 10.4 关于 IP

IP 来自 `HttpServletRequest.getRemoteAddr()`。网关的 `XForwardedRequestHeadersFilter` 会把它看到的客户端地址写进 `X-Forwarded-For`，identity 的 `forward-headers-strategy: framework` 把它解析成 `getRemoteAddr()`；门户是 BFF，所以今天这个地址是门户容器的地址。PAP-99 接通「门户 → 网关 → identity」的受信转发后，这里自动变成浏览器的真实地址，本 Issue 不用改代码。在那之前每 IP 维度等于「全站每小时 30 次重发」，开发期的人数下够用；`.env` 之类不需要为它加开关。

## 11. 网关路由规则

| 路径 | 规则 | 改动 |
|---|---|---|
| `/patra-identity/auth/email/verify` | 公开（照样翻译令牌） | 加进 `IDENTITY_PUBLIC_PATHS`；集成测试：匿名 POST 被转发且不带 `Authorization`，带会话的 POST 被转发且带断言 |
| `/patra-identity/me/email/verification/resend` | 需登录 | 现有默认规则已覆盖；集成测试：匿名 401 且 WireMock 没收到，带会话被转发且带断言 |

## 12. 错误契约

前缀 `IDN`。新增和改动：

| 场景 | 异常 | 特征 | 状态 | 错误码 | 额外信息 | detail |
|---|---|---|---|---|---|---|
| 令牌为空 / 格式不对 / 不存在 / 过期 / 已用 / 已作废 / 与用户当前邮箱不符 | `InvalidUserFieldsException` | `RULE_VIOLATION` | 422 | `IDN-0422` | `errors[]`：`token` / `REQUIRED` 或 `token` / `INVALID` | 请求参数不合法 |
| 重发过于频繁 | `EmailResendLimitedException`（新） | `QUOTA_EXCEEDED` | 429 | `IDN-0429` | `Retry-After`、`retryAfterSeconds` | 发送过于频繁，请稍后再试 |
| 邮箱已验证还重发 | `EmailAlreadyVerifiedException`（新） | `CONFLICT` + `IdentityErrorTrait.EMAIL_ALREADY_VERIFIED` | 409 | `IDN-0409` | `traits` 含 `EMAIL_ALREADY_VERIFIED` | 邮箱已经验证过了 |
| 注销中重发 | `UserPendingDeletionException`（已有） | `CONFLICT` + `PENDING_DELETION` | 409 | `IDN-0409` | `traits` 含 `PENDING_DELETION` | 账号正在注销中，取消注销后再操作 |
| 重发时没有当前用户、用户已封禁 / 已注销 | `AuthenticationRequiredException` | `UNAUTHORIZED` | 401 | `IDN-0401` | — | Authentication required |
| Redis 不可用（限流、撤销会话） | `TemporarilyUnavailableException` / `SessionStoreUnavailableException` | `DEP_UNAVAILABLE` | 503 | `IDN-0503` | — | 服务暂时不可用 |

验证接口的 422 而不是 404：`POST /auth/email/verify` 回 404 容易被当成「接口不存在」，而「请求体里的 `token` 不可用」正是 `errors[]` 这套字段错误的语义；原因码只有一个 `INVALID`，不区分过期和不存在。

## 13. 配置

按项目约定分两处：随环境变的（服务商坐标、凭据引用、发件人、门户地址）进 Nacos 的 `patra-infra/nacos/dev/patra-identity.yaml`，随代码变的默认值进 jar 的 `application.yml`，同一个键不两处写。

Nacos `patra-identity.yaml` 追加（密钥只写 `${变量:}` 引用，`NacosConfigFilesTest` 守）：

```yaml
spring:
  mail:
    # 默认指向 Mailpit（mini 的 core 栈，无鉴权、无 TLS）；mini 的真实服务商由 ~/.patra/secrets/mail.env 覆盖
    host: ${PATRA_MAIL_HOST:${PATRA_INFRA_HOST:127.0.0.1}}
    port: ${PATRA_MAIL_PORT:1025}
    username: ${PATRA_MAIL_USERNAME:}
    password: ${PATRA_MAIL_PASSWORD:}
    properties:
      "[mail.smtp.auth]": ${PATRA_MAIL_AUTH:false}
      "[mail.smtp.ssl.enable]": ${PATRA_MAIL_SSL:false}              # 465
      "[mail.smtp.starttls.enable]": ${PATRA_MAIL_STARTTLS:false}    # 587
      "[mail.smtp.starttls.required]": ${PATRA_MAIL_STARTTLS:false}
      "[mail.smtp.connectiontimeout]": 10000
      "[mail.smtp.timeout]": 15000
      "[mail.smtp.writetimeout]": 15000

patra:
  identity:
    mail:
      from: ${PATRA_MAIL_FROM:Patra <no-reply@patra.local>}                 # 发件人，随服务商定；mail.env 覆盖
      links:
        base-url: ${PATRA_PORTAL_BASE_URL:http://linqibins-mac-mini:4000}   # 邮件里站内链接的门户地址
```

jar 的 `application.yml` 加：

```yaml
management:
  health:
    mail:
      # 不拿 SMTP 连通性当健康：容器 healthcheck 每 10 秒一次会变成一天八千多次登录服务商，CD 也不该因此回滚
      enabled: false

patra:
  identity:
    mail:
      zone: Asia/Shanghai                                     # 安全通知里时间的显示时区
      token-retention: 30d                                    # 过期令牌保留多久后删行
      links:
        verify-email-path: /verify-email
        security-settings-path: /settings/security
      resend:
        cooldown: 60s
        per-account-hourly: 5
        per-account-daily: 10
        per-ip-hourly: 30

xxl:
  job:
    executor:
      ip: ${PATRA_ADVERTISE_IP:}
      logpath: ${XXL_JOB_EXECUTOR_LOG_PATH:${PATRA_LOG_DIR:logs}/xxl-job/patra-identity}
      logretentiondays: ${XXL_JOB_LOG_RETENTION_DAYS:30}
```

- `IdentityProperties` 加 `Mail(from, zone, tokenRetention, Links(baseUrl, verifyEmailPath, securitySettingsPath), Resend(cooldown, perAccountHourly, perAccountDaily, perIpHourly))`，都给默认值。`IdentityConfiguration` 把 `Resend` 变成 `EmailResendPolicy`（非正数启动失败）、`Links` 变成 `MailLinkPolicy`（不是绝对 `http(s)` 地址、路径不以 `/` 开头启动失败）、装配 `EmailVerificationIssuer`（`SecureRandom` 一个实例）、`ThymeleafMailRenderer`（时区）、`EmailDeliveryPort`（`SmtpEmailDeliveryAdapter.async`，发件人在这里解析一次，不合法启动失败）。
- `xxl.job.admin.addresses` 已在 Nacos `patra-common.yaml`，identity 读得到；执行器端口按约定 = 6400 + 1 = 6401。
- 本机 IDE 跑 identity：Nacos 里有了这些键之后不设任何变量就发到 mini 的 Mailpit、链接指向 mini 的门户；想让链接指向本机门户设 `PATRA_PORTAL_BASE_URL=http://localhost:4000`；想走真实服务商就在本机也放一份 `mail.env`。
- 这些键合入 main 时由 CD 自动推到 Nacos；合入前在本机验收要先手动推一次（`NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh --check`，确认只多这些键再去掉 `--check` 推送；mini 上跑着的旧 identity 不认识这些键，不受影响），或者在 IDE 里直接设 `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` 环境变量绕过。没有 `spring.mail.host` 时 Boot 不建 `JavaMailSender`，identity 启动失败并点名缺的 Bean。
- 集成测试：`application-test.yml` 加 `xxl.job.enabled: false`；`spring.mail.host` / `port` 由 `GreenMailSmtpInitializer` 写入。

## 14. 部署与运行环境

### 14.1 开发环境：Mailpit

`docker-compose.core.yaml` 加一个服务（core 栈常驻，和 PG、Redis、Nacos 一起起）：

```yaml
  mailpit:
    image: axllent/mailpit:v1.31.6
    container_name: patra-mailpit
    restart: unless-stopped
    ports:
      - "1025:1025"   # SMTP：identity 默认往这里发
      - "8025:8025"   # Web UI：http://linqibins-mac-mini:8025
    environment:
      TZ: Asia/Shanghai
      MP_DATABASE: /data/mailpit.db
      MP_MAX_MESSAGES: 500
    volumes:
      - ${HOME}/.patra/docker/mailpit:/data
    healthcheck:
      test: [ "CMD", "/mailpit", "readyz" ]
      interval: 10s
      timeout: 5s
      retries: 10
```

本机 IDE 里的 identity 经 `PATRA_INFRA_HOST`（mini 的 tailscale IP）:1025 投递；mini 上的 identity 容器在没有 `mail.env` 时经 `linqibins-mac-mini:1025`（`extra_hosts`）投递到同一个 Mailpit。Mailpit 只在 tailnet 内可达，和其他基础设施一样不设密码。

### 14.2 密钥：`mail.env`

`patra-infra/docker/secrets/mail.env.example`：

```bash
# 真实邮件服务商（Resend）的 SMTP 凭据。只放 mini（本机要走真实服务商时再放一份）。
# Resend 的 SMTP：主机 smtp.resend.com，用户名固定 resend，密码是控制台签发的 API key；
# 465 走 SSL（PATRA_MAIL_SSL=true、PATRA_MAIL_STARTTLS=false），587 走 STARTTLS（反过来）。
# 发件域名要先在 Resend 控制台验证（SPF / DKIM），PATRA_MAIL_FROM 用该域名下的地址。
PATRA_MAIL_HOST=smtp.resend.com
PATRA_MAIL_PORT=465
PATRA_MAIL_USERNAME=resend
PATRA_MAIL_PASSWORD=
PATRA_MAIL_AUTH=true
PATRA_MAIL_SSL=true
PATRA_MAIL_STARTTLS=false
PATRA_MAIL_FROM=Patra <no-reply@example.com>
```

- `docker-compose.apps.yaml` 的 identity 加 `- path: ${HOME}/.patra/secrets/mail.env`、`required: false`：没有它 mini 的 identity 也能起，只是邮件进 Mailpit；放好后按 README「改了密钥文件之后」重建 identity 容器。不做成 `required: true`：服务商选型可能晚于合并，不让它卡住部署。
- 本机进程由 `patra-tool-launch` 读 `~/.patra/secrets/*.env`，放了 `mail.env` 就自动生效。
- CD 不用改：注入方式就是 `env_file`。
- README「密钥」表加一行；「服务访问 URL」加 Mailpit。

### 14.3 mini 的真实服务商

2026-10-21 在 mini 上实测（`curl smtps://` / `curl --ssl-reqd smtp://`，完成 TLS 握手并收到 `220` / `250`）：

| 端点 | 结果 |
|---|---|
| `smtp.gmail.com:465`、`:587` | 通（TLS 1.3） |
| `smtp.resend.com:465`、`:587` | 通 |
| `smtpdm.aliyun.com:465`（阿里云邮件推送） | 通 |
| `smtp.qcloudmail.com:465`（腾讯云 SES） | 通 |
| `smtp-relay.brevo.com:587`、`smtp.sendgrid.net:587`、`smtp.postmarkapp.com:587`、`smtp.mailgun.org:587`、`smtp.qq.com:465`、`smtp.163.com:465` | 端口通（TCP） |
| 各家 HTTPS API（Resend、Brevo、Postmark、SendGrid、阿里云、腾讯云、Mailgun） | 通 |

结论：mini 直连各家 SMTP 都没问题，决策 D 的 SMTP 路线成立，不需要改走 HTTPS API。配置是通用的 SMTP，换服务商只改 `mail.env`。

服务商 2026-10-21 定为 **Resend**：`smtp.resend.com`，465（SSL）或 587（STARTTLS），用户名固定 `resend`，密码是 API key；发件域名先在 Resend 控制台验证（加 SPF / DKIM 记录），发件人用该域名下的地址；免费额度按控制台当时的说明为准（目前每月 3,000 封、每天 100 封、一个自定义域名），对本项目绰绰有余。代码里没有任何 Resend 专属的东西。

### 14.4 mini 上的一次性步骤（随后端 PR 部署时做）

1. 合入 main 的 CD 会先把 `patra-infra/nacos/dev/` 推到 Nacos 再部署，`spring.mail.*` 等新键随之生效，不用手动推。`git pull` 后 `compose-all.sh up core`，起 Mailpit。
2. 在 Resend 控制台验证发件域名、签发 API key；放 `~/.patra/secrets/mail.env`（第 14.2 节的 Resend 值），按 README「改了密钥文件之后」重建 identity 容器。
3. 重建 `patra_identity` 库（identity README 第 8 节的两条 SQL），Flyway 跑新 `V1`。
4. xxl-job-admin（`http://linqibins-mac-mini:7070/xxl-job-admin`）：新建执行器 `patra-identity`（自动注册），新建任务 `emailTokenCleanupJob`，Cron `0 30 4 * * ?`，路由策略第一个，阻塞策略单机串行。可以用 `~/.claude` 记忆里的 API 做法。
5. 真实邮箱注册一次，收到验证邮件、落地页验证成功（D3）。

## 15. 测试策略

时间全部走注入的 `Clock`；限流脚本和令牌有效期都按它判，所以固定时钟能把窗口拨过去。邮件桩用 GreenMail（`patra-tool-test` 的 `GreenMailSmtpInitializer`，JVM 内一个实例、随机端口，`@ContextConfiguration(initializers = …)` 挂上即可，和 Redis 容器同列，不增加 Spring 上下文数）。

### 15.1 单元测试（不起 Spring）

- `EmailTokenSecret`：43 个字符、字符集、`parse` 拒绝 42 / 44 个字符、`=` 填充、`+` / `/`；`hash()` 是 64 位小写十六进制且可复现；两次 `generate` 不同；`toString` 不含原文。
- `EmailTokenPurpose`：四个有效期。
- `EmailToken`：`issue` 的过期时间；`isUsableAt` 在过期当刻为假；`markConsumed` / `markRevoked` 互斥。
- `EmailResendPolicy` / `MailLinkPolicy`：非正数、非法地址启动失败；末尾 `/` 被去掉；链接形如 `…/verify-email#token=…`。
- `EmailVerificationIssuer`：先作废后保存；保存的聚合只有哈希；返回的邮件链接含原文；`DELETED` 用户（无邮箱）抛 `IllegalStateException`。
- `RegisterUserHandler`：令牌在事务里签发（`TransactionOperations` 的桩里记录顺序）；发信和计数在事务之后；计数抛异常不影响返回；发信端口收到的邮件收件人、链接正确。
- `ResendEmailVerificationHandler`：顺序（状态 → 限流 → 加锁 → 签发 → 发信）；已验证 409 且不调限流；注销中 409；封禁 401；限流 429 带等待时长且不进事务；锁后发现已验证 409 且不发信。
- `VerifyEmailHandler`：空 / 格式错不查库；消费失败 422；邮箱不符 422；同一用户带会话时保留该会话、其余撤销且记录标 `REVOKED`；匿名时全部撤销；别人的会话不受影响；`markEmailVerified` 幂等。
- `PurgeExpiredEmailTokensHandler`：分批直到不足一批，每批一个事务。
- `BanUserHandler`：封禁作废全部令牌。
- `ThymeleafMailRenderer`：验证邮件两部分都含链接与昵称；昵称 `<b>x</b>` 在 HTML 里被转义、在文本里原样；没有残留的 `th:` 属性；`SecurityNotice` 片段（用测试资源里的一个模板）：四项都在，空字段渲染「未知」，时间按 `Asia/Shanghai` 格式化。
- `SmtpEmailDeliveryAdapter`：同步执行器下 `send` 产出一封 `multipart/alternative`（用 `JavaMailSenderImpl` + 内存 `Session` 或 `mock(JavaMailSender)` 捕获 `MimeMessage`）；两个自动邮件头；`mailSender.send` 抛异常时 `send` 不抛、记 WARN（`OutputCaptureExtension`）。
- `IdentityConfiguration`：`resend.*` 非正数、`links.base-url` 不是 `http(s)` 启动失败。
- `GreenMailSmtpInitializer`：写入 `spring.mail.host` / `port`，两次初始化同一个实例。

### 15.2 集成测试

infra（PG 容器）：

- `EmailTokenRepositoryAdapterIT`（`@DataJpaTest`）：`consume` 第一次返回行且 `consumed_at` 已写、第二次为空；过期为空；作废后为空；用途不符为空；`revokeActive` 只动有效行并返回数量；`revokeAllActive`；同一用户同一用途插第二条有效行撞 `uk_idn_email_token_active`；`deleteExpiredBefore` 按批删、不删未过期的。
- `IdentitySchemaConstraintsIT`：`purpose` 检查、哈希格式与唯一、消费 / 作废互斥、`REVOKED` 可写入、邮箱小写检查。
- `EmailResendThrottleAdapterIT`（Redis 容器，时间直接传）：第一次放行；60 秒内第二次返回约 60 秒；拨过 60 秒放行；一小时内第 6 次返回到最早一次满一小时的剩余；一天内第 11 次；同 IP 不同用户第 31 次；多个维度同时超时返回最长的；`markSent` 不判只计；IP 为空时第四维跳过；键的 TTL 存在。
- `SmtpEmailDeliveryAdapterIT`（GreenMail）：真实 `JavaMailSender` + 异步执行器发一封，`waitForIncomingEmail` 后断言 `From`、`To`、主题（中文正确解码）、`multipart/alternative` 两部分、两个头。
- `RedisSessionStoreIT`（identity-session）：`deleteAllExcept` 保留指定会话、返回其余 ID、索引里只剩一条；指定不存在的会话时等于全删。

adapter 切片（`@WebMvcTest`）：

- `EmailVerificationControllerIT`：200 的字段；422 的 `errors[]`（`REQUIRED`、`INVALID`）；空请求体 400。
- `MeEmailControllerIT`：202 的字段；429 的 `Retry-After` 与 `retryAfterSeconds`；409 的 `traits` 含 `EMAIL_ALREADY_VERIFIED`；409 含 `PENDING_DELETION`；401。

boot 端到端（PG + Redis 容器 + GreenMail，`RANDOM_PORT`，`MutableClock`，断言用测试时钟签）：

- `EmailVerificationFlowIT`：
  1. 注册 → GreenMail 收到一封：`multipart/alternative`，文本与 HTML 都含 `#token=`，链接以配置的 `base-url` 开头——请求带 `Host: evil.example` 头也一样（AC 1）；响应体和日志里没有令牌原文；`idn_email_token` 里 `token_hash` 等于原文的 SHA-256、没有任何列含原文（AC 2）。
  2. 再登录一次得到第二个会话；带第一个会话的断言 POST 验证 → 200；`/auth/me` 的 `emailVerified` 为真；第二个会话的键消失、记录 `REVOKED`，第一个会话还在（AC 4）；再 POST 同一令牌 → 422（AC 2）。
  3. 新用户：立刻重发 → 429 且 `Retry-After` 为 60（注册的首封计入冷却）；拨 61 秒 → 202、旧令牌 422、新令牌 200；再拨，循环到一小时内第 6 次 → 429 且 `Retry-After` 等于到最早一次满一小时的秒数；拨过去后继续到一天内第 11 次 → 429（AC 3）。
  4. 另一个用户在未登录状态（不带断言）消费令牌 → 200，该用户的全部会话消失；请求者（第三个用户）的会话不受影响。
  5. 已验证用户重发 → 409 `EMAIL_ALREADY_VERIFIED`；SQL 把用户改成 `PENDING_DELETION` 后重发 → 409 `PENDING_DELETION`。
  6. 用 SQL 把 30 个不同 IP 的计数写满不现实，IP 维度在 infra IT 里测；这里只测一次「同 IP」的放行。

网关（`GatewayAuthenticationIT`）：第 11 节的两组用例。

### 15.3 回归

`./gradlew spotlessApply`，`./gradlew check`，`./gradlew dumpModuleGraph`（依赖变了），identity 四个模块（infra、adapter、boot、session）、gateway、tool-test 的 `integrationTest`。本机 Docker 在线。

### 15.4 库重建

本机：README 第 8 节的两条 SQL，启动 identity，`flyway_schema_history` 一行、六张表。mini：随后端 PR 部署执行（第 14.4 节）。

### 15.5 AC 对应

| AC | 证据 |
|---|---|
| 注册后收到验证邮件，含文本与 HTML，链接域名来自配置而非 Host | `EmailVerificationFlowIT` 第 1 步；本机 Mailpit 截图；mini 真实邮箱（部署后） |
| 令牌一次性、过期失败、重发后旧令牌失败、库里不存明文 | `EmailTokenRepositoryAdapterIT`、`EmailVerificationFlowIT` 第 1 ~ 3 步 |
| 四个维度分别 429 并带 `Retry-After`（固定时钟 + Redis 容器） | `EmailResendThrottleAdapterIT`（四维度）、`EmailVerificationFlowIT` 第 3 步 |
| 验证写 `email_verified_at` 并撤销其他会话、当前保留 | `EmailVerificationFlowIT` 第 2、4 步、`VerifyEmailHandlerTest` |
| mini 出站 SMTP 实测通过，凭据不在仓库，`.example` 存在 | 第 14.3 节的握手实测；真实发信待服务商定下后在 mini 做；`secrets/mail.env.example` 入库、`.gitignore` 已覆盖 `secrets/*` |
| `./gradlew check` + `integrationTest` 全绿 | 第 15.3 节的命令输出 |

## 16. 实施时要实测的点

今天已经实测的：

| # | 结论 | 结果 |
|---|---|---|
| 1 | mini 到各家 SMTP 465 / 587 的 TLS 握手 | 成立，见第 14.3 节 |
| 2 | Boot 4.0.8 BOM 里有 `spring-boot-starter-mail`、`thymeleaf` 3.1.5、Jakarta Mail 2.1.5 / Angus 2.0.5 | 成立（查 BOM pom） |
| 3 | GreenMail 最新版 2.1.14（2026-09）、Mailpit 最新版 v1.31.6 | 成立（Maven Central、GitHub Releases） |

实施时要实测、结果写回本节：

| # | 要实测的 | 不成立时的替代 | 实施结果 |
|---|---|---|---|
| 4 | `JdbcClient` 在 `@DataJpaTest` / 处理器事务里执行 `UPDATE … RETURNING`：与 JPA 同一个连接（`JpaTransactionManager` 暴露 JDBC 连接），能看到同事务里 `saveAndFlush` 的行，返回的是更新后的值 | 没有：这是设计选定的做法（不走 Spring Data 的 native `@Query`——结果会被一级缓存里的旧实例顶掉） | 成立：`EmailTokenRepositoryAdapterIT` 在 `@DataJpaTest` 事务里 `UPDATE … RETURNING` 看得到同事务 `saveAndFlush` 的行并返回新值；一级缓存里的旧实例不会刷新，测试按 SQL 断言 |
| 5 | Thymeleaf 两个 `ClassLoaderTemplateResolver` 按 `resolvablePatterns` 分流 HTML / TEXT 模式；TEXT 模式的片段 `[# th:fragment]` 与 `th:replace` | 退化为文本模板不用片段，公共部分放 Java 字符串 | 部分成立：HTML 模式片段正常；TEXT 模式不支持 `~{tpl :: fragment}` 片段选择器，文本片段改为整文件 `th:replace`，不放 Java 字符串 |
| 6 | `MailSenderAutoConfiguration` 在 `spring.mail.username` 为空串时装配出可用的 `JavaMailSender`（Mailpit 无鉴权） | 用 `PATRA_MAIL_USERNAME` 缺省时不写该键的写法，或自己装配 `JavaMailSenderImpl` | 成立：boot 集成测试镜像 Nacos 缺省 `username: ""` / `password: ""` + `mail.smtp.auth=false`，`EmailVerificationFlowIT` 经 GreenMail 正常收发 |
| 7 | GreenMail 2.1.14 与 Angus Mail 2.0.5 在同一类路径下收发正常 | 换 GreenMail 2.1.x 其他小版本 | 成立：`SmtpEmailDeliveryAdapterIT` 与 `EmailVerificationFlowIT` 收发正常（读正文要用 `part.getContent()`，`GreenMailUtil.getBody` 给的是 base64） |
| 8 | `SimpleAsyncTaskExecutor` 虚拟线程 + 并发上限 + `taskTerminationTimeout` 在 Boot 4 可用 | `Executors.newThreadPerTaskExecutor` + 信号量 | 成立：`SmtpEmailDeliveryAdapter.async` 用 `SimpleAsyncTaskExecutor` 虚拟线程 + `concurrencyLimit` + `taskTerminationTimeout` |
| 9 | `@WebMvcTest` 切片不加载 adapter 包里的 `XxlJobConfig`（catalog 的切片测试没有关它，推测如此）；boot 的 `@SpringBootTest` 要靠 `xxl.job.enabled: false` | 切片测试配置里也加 `xxl.job.enabled: false` | 成立：adapter 的 `@WebMvcTest` 切片没加载 `XxlJobConfig`；boot 的 `@SpringBootTest` 靠 `xxl.job.enabled: false` |
| 10 | 控制器方法参数 `HttpServletRequest` 在 `@WebMvcTest` + `RestTestClient` 下 `getRemoteAddr()` 有值（MockMvc 默认 `127.0.0.1`） | 命令里 IP 允许为空 | 成立：`AuthControllerIT` 里 `getRemoteAddr()` 是 `127.0.0.1`，命令里的 IP 非空 |
| 11 | `uk_idn_email_token_active` 部分唯一索引：`CREATE UNIQUE INDEX … WHERE …` 在 Flyway 脚本里与 `CREATE TABLE` 分开写 | — | 成立：`V1` 里 `CREATE UNIQUE INDEX … WHERE …` 与建表分开写，`IdentitySchemaConstraintsIT` 覆盖 |
| 12 | 拨动 `MutableClock` 后 `IdentityAssertionSigner` 要用同一个时钟签（PAP-93 第 14 节第 10 条） | — | 成立：`EmailVerificationFlowIT` 用 `new IdentityAssertionSigner(TestSigningKey.key(), clock)` 签断言，拨到 24 小时后请求照样通过验签 |
| 13 | `NacosConfigFilesTest` 对 `patra-identity.yaml` 的新键通过（`password` 键只写 `${PATRA_MAIL_PASSWORD:}`）、`JarConfigOverlapCheckerTest` 对 jar 与 Nacos 的键集通过 | — | 成立：`NacosConfigFilesTest`、`JarConfigOverlapCheckerTest`、`ApplicationConfigTest` 全绿 |

本机手工验收（2026-10-21，Task 16）：本机起 PostgreSQL 17（15432）、Redis 7（16379）、Mailpit v1.31.6 三个容器，identity 与 gateway 用 jar 起在本机，配置仍从 mini 的 Nacos 读（`PATRA_INFRA_HOST=127.0.0.1`、`PATRA_NACOS_ADDR` 指 mini、`SPRING_MAIL_HOST` / `PORT` 指本机 Mailpit、注册到独立的 Nacos 分组以免 mini 的网关路由过来）。Flyway 对空库只跑一份 `V1`，六张表齐。经本机网关：注册 201 → 立刻重发 429 `Retry-After: 60`、`retryAfterSeconds: 60` → Mailpit 收到一封 `Patra <no-reply@patra.local>` 发给注册邮箱的「验证你的 Patra 邮箱」，带 `Auto-Submitted: auto-generated` 与 `X-Auto-Response-Suppress: All`，文本与 HTML 两部分链接一致、形如 `http://localhost:4000/verify-email#token=…`（来自配置，不来自请求）→ 带会话 POST `/auth/email/verify` 200 → `/auth/me` 的 `emailVerified` 为 `true`、会话仍有效 → 匿名再 POST 同一令牌 422 `token` / `INVALID`（网关公开路径生效）→ 重发 409 `EMAIL_ALREADY_VERIFIED`。库里 `idn_email_token` 一行：`token_hash` 64 位、已消费、`requested_ip` 为 `127.0.0.1`，按令牌原文查不到；identity 与网关日志里都没有令牌原文。未推送 Nacos、未动 mini 的库。

## 17. README

`patra-api/patra-identity/README.md`：

1. 第 1 节模块表：domain 加令牌、邮件端口、限流；infra 加 SMTP 适配器、Thymeleaf 模板、令牌表、限流脚本；adapter 加两个控制器与 XXL-Job。
2. 第 2 节接口表：加 `POST /me/email/verification/resend`（需登录，202）、`POST /auth/email/verify`（公开，200）。
3. 第 3 节账号模型：加 `idn_email_token`；登录记录结束原因加 `REVOKED`。
4. 新增「邮箱验证与邮件」一节：令牌规则、链接格式与片段、重发限流、验证撤销会话、模板目录与新邮件怎么加、安全通知片段、配置项、Mailpit 与 `mail.env`、清理任务。
5. 第 7 节错误码表：第 12 节的新行。
6. 第 8 节本地运行：Mailpit 地址、`PATRA_PORTAL_BASE_URL`、库重建提示、XXL 执行器端口 6401 与 `PATRA_ADVERTISE_IP`。

`patra-identity-session/README.md`：操作表加 `deleteAllExcept`。

`patra-infra/docker/README.md`：「密钥」表加 `mail.env`；「服务访问 URL」加 Mailpit（SMTP 1025、UI 8025）；「首次部署」提到 `mail.env` 可选。

## 18. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 找回密码：`EmailTokenPurpose.PASSWORD_RESET`（1h）已在枚举和表里；签发走 `EmailTokenRepository.revokeActive` + `save`（照 `EmailVerificationIssuer` 的写法，或把它泛化成 `EmailTokenIssuer`）；消费走 `consume(hash, PASSWORD_RESET, now)`；重设成功 `revokeAllActive` + `sessions.revokeAll` + `markEmailVerified`；申请的限流复用 `email-resend-throttle.lua`（换键前缀与参数：同邮箱 60 秒、每天 5、同 IP 每小时 20）；改邮箱确认 / 撤销用 `EMAIL_CHANGE_CONFIRM`（`email` 列存新邮箱）/ `EMAIL_CHANGE_REVERT`（7d，`email` 列存要改回的旧邮箱）；撤销令牌不受「同用途只留一个」和 `revokeAllActive` 约束，X → Y → Z 时两条撤销令牌并存；消费一条撤销令牌：把邮箱改回它的 `email`、作废该用户其余的撤销令牌，再按 2.1.7 撤销全部会话、作废密码并发重设链接；改密码保留本机用 `revokeAllExcept`，结束原因 `REVOKED`；三封通知邮件按第 9.4 节加记录 + 模板，`MailLinkPolicy` 加 `reset-password-path`、`revert-email-change-path`；`403 REAUTH_REQUIRED` 仍按 PAP-93 | PAP-98 |
| 新设备邮件：实现 `OutgoingMail` 的记录 + 两个模板，`SecurityNotice` 的 `device` / `ipAddress` / `ipLocation` 由 Yauaa / ip2region 填；踢设备用 `revoke` / `revokeAllExcept`，结束原因 `REVOKED` 已在 `V1`；受信转发接通后 `getRemoteAddr()` 自动是真实 IP，令牌表的 `requested_ip` 与登录记录的 IP 用同样的文本列 | PAP-99 |
| 注销：发起与到期执行都 `revokeAllActive` + `revokeActive(userId, EMAIL_CHANGE_REVERT, now)`（注销后邮箱为空，撤回已无意义）；三封邮件按第 9.4 节；到期任务照 `EmailTokenCleanupScheduleJob` 的写法加 `@XxlJob`，执行器已注册 | PAP-100 |
| 未验证用户的主页 404、链接不展示：读 `emailVerified`，本 Issue 不动 | PAP-95 |
| 落地页 `/verify-email`：读 `location.hash` 里的 `token`，点按钮后 POST `{ token }` 到门户 Route Handler → 网关 `/patra-identity/auth/email/verify`，有会话 Cookie 就带会话令牌（保留本机会话）；200 显示已验证并刷新 `/auth/me`；422 `errors[0].code` 为 `INVALID` 显示「链接无效或已过期」并给已登录用户「重新发送」；顶部提示的「重发」调 `POST /patra-identity/me/email/verification/resend`，202 读 `nextResendAt` 倒计时，429 读 `retryAfterSeconds`，409 `EMAIL_ALREADY_VERIFIED` 刷新状态；安全设置页路径 `/settings/security`（邮件里的链接指向它） | PAP-102 |
| 门户的公开地址进 identity 配置 `PATRA_PORTAL_BASE_URL`（默认 `http://linqibins-mac-mini:4000`）；门户换域名时改它 | PAP-101 / 运维 |

## 19. 要同步改的文档和 Issue

本设计评审通过后一起改：

- release spec 2.1.6：补「注册的首封计入重发冷却与配额」「验证时保留当前会话、撤销其余」；2.1.7 一次性令牌行补「令牌原文 43 字符 base64url、哈希 SHA-256 十六进制、过期令牌保留 30 天由 XXL-Job 清理」；2.1.10 补「安全通知邮件共用 `SecurityNotice` 片段，设备 / 属地在 PAP-99 前显示未知」；2.1.7 一次性令牌行补「改邮箱撤销令牌可并存、不被批量作废」；决策 D 补「mini 实测各家 SMTP 均通，服务商为 Resend」「开发收集器是 Mailpit」「集成测试用 GreenMail」。
- Linear PAP-96：To-Do 的「`EmailDeliveryPort`」注明邮件对象是 `OutgoingMail`；AC 不变。
- Linear PAP-98 / PAP-99 / PAP-100 / PAP-102：第 18 节的契约各加一段。
- identity README、session README、infra README：第 17 节。

## 20. 设计中由我定的点与待拍板的点

**我定的（builder 可直接采用，用户看到不对就推翻）**：

1. 验证令牌不可用一律 422 `token` / `INVALID`（不是 404 / 410），不区分过期与不存在。
2. 注册的首封邮件计入冷却与两个按账号的窗口、不计每 IP 窗口（每 IP 维度只管重发；PAP-99 之前 IP 是门户地址）：注册后立刻重发得到 429。
3. 重发接口回 202 + `{ email, nextResendAt }`；验证接口回 200 + `{ email, emailVerifiedAt }`。
4. 已验证还重发回 409 + `EMAIL_ALREADY_VERIFIED`，不是 204 静默。
5. 匿名消费令牌时撤销该用户全部会话（没有可保留的）；登录成别人去消费时只动令牌所属用户。
6. 限流是滑动窗口（有序集合），不是固定窗口；`Retry-After` 取各维度里最长的；时间由 Java 传入脚本。
7. 令牌表的请求 IP 用 `VARCHAR(45)` 文本列，不用 `inet`、不用审计列 `ip_address`。
8. `OutgoingMail` 是接口（收件人、模板名、主题、变量），渲染器按模板名找文件，不按类型 `switch`；主题写在邮件记录里。
9. Thymeleaf 直接引 `org.thymeleaf:thymeleaf`，渲染器自建引擎，不用 starter。
10. 异步执行器是虚拟线程的 `SimpleAsyncTaskExecutor`，并发上限 4，停机等 10 秒，由适配器持有而不是 Bean；失败不重试。
11. 清理任务用 XXL-Job（identity 第一次接执行器，端口 6401），不用 `@Scheduled`。
12. Mailpit 放 core 栈常驻；`mail.env` 对 identity 容器是可选文件，缺它就发到 Mailpit。
13. 集成测试的 SMTP 桩用 GreenMail（JVM 内），初始化器放 `patra-tool-test`；不用 Mailpit 的 Testcontainers。
14. 封禁顺带作废该用户全部令牌（release spec 2.1.7 列了封禁，正好在本 Issue 的方法边上）。
15. 安全通知里的时间按 `Asia/Shanghai` 显示并标 `UTC+8`。
16. 邮件链接的门户路径（`/verify-email`、`/settings/security`）作为配置项给默认值，PAP-102 可改。
17. 关掉 Boot 自动注册的邮件健康检查（`management.health.mail.enabled: false`）：SMTP 连通性不进 `/actuator/health`，部署不因收集器或服务商抖动回滚。
18. 验证流程先按哈希无锁查到用户、锁用户行，再做条件消费：和重发、封禁同一个加锁顺序，避免死锁。
19. 随环境变的邮件配置（服务商坐标、凭据引用、发件人、门户地址）按项目约定进 Nacos `patra-identity.yaml`，不留在 jar。
20. 清理任务用 XXL-Job（第 8.4 节的理由：与 catalog 一致、PAP-99 / 100 还有两个任务、D6 要手动触发）；不想让 identity 接执行器时可以推翻，改 Spring `@Scheduled` 要另补手动触发入口。

**已拍板（2026-10-21）**：1 选 Resend；2 用户交我定，按建议选 (a)，已写进第 6.1、7.4、7.6、18 节；3、4 按默认。原文保留：

1. **真实服务商选哪家**。mini 到各家都通，配置通用，只差 `mail.env` 的内容。有自己的域名：推荐 Resend（免费额度够用、SMTP 465 / 587、域名做 SPF / DKIM）或阿里云邮件推送（国内投递稳、发 QQ / 163 不易进垃圾箱）；没有域名：Gmail 应用专用密码（`smtp.gmail.com:465`，发件人就是那个 Gmail 地址，免费账号每天 500 封）。决定后填进 release spec 决策 D 与第 14.3 节。
2. **邮件里链接的门户地址**默认 `http://linqibins-mac-mini:4000`（tailnet 内用 MagicDNS 名能点开）。如果用户和测试者更常用 `http://100.103.73.27:4000` 或别的入口，改默认值。
3. **发件人显示名与地址**：`Patra <no-reply@…>`，域名随服务商定。
4. **改邮箱的「不是我」令牌要不要豁免「同用户同用途只留一个」和「按用户全部作废」**（builder check 指出的漏洞，本 Issue 建索引、PAP-98 用）。按 release spec 2.1.7 的字面规则，知道密码的攻击者连续改两次邮箱（X → Y → Z），发给 X 的 7 天「不是我」链接会被第二次签发作废、也会被「改邮箱生效」的批量作废清掉，原主无法撤回。两条路：(a) 本 Issue 现在就把部分唯一索引的谓词加上 `AND purpose <> 'EMAIL_CHANGE_REVERT'`，`revokeAllActive` 不动 `EMAIL_CHANGE_REVERT`（只有注销和显式的 `revokeActive(userId, EMAIL_CHANGE_REVERT)` 清它），多个撤销链接可以并存、各自 7 天，PAP-98 消费时把邮箱改回该令牌 `email` 列的值并作废其余撤销令牌；(b) 本 Issue 照设计做，PAP-98 用产品规则堵（撤销窗口 7 天内禁止再次改邮箱）。建议 (a)：改动只是一个索引谓词和一条 JPQL 的 `where`，而 (b) 让改错新邮箱的正常用户也要等 7 天。
