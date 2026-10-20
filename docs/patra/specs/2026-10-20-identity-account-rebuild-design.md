# identity 账号重建工程设计（PAP-93）

> **Issue**：[PAP-93](https://linear.app/papertrace/issue/PAP-93)（GitHub #259）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)
> **前置设计**：[identity 账号（PAP-63）](2026-10-05-identity-account-design.md)、[identity 会话（PAP-64）](2026-10-09-identity-session-design.md)、[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)
> **日期**：2026-10-20
> **状态**：已实施（2026-10-20，分支 `feat/v0.12-user-center-api`）；实测结果见第 14 节

## 1. 要解决的问题

v0.8 的账号只有邮箱、密码和 `ACTIVE / BANNED` 两个状态。v0.12 要让用户有一个可以被别人看见的身份，并且之后的收藏、关注、讨论、第三方登录都挂在这个身份上，不再改账号模型。本 Issue 是本版所有后端 Issue 的地基：把账号模型一次改成最终形态——用户名、昵称、邮箱验证状态、四态状态机、改名规则——并把 Flyway 脚本重写成一份最终形态的 `V1`。

本 Issue 交付之后，PAP-95（资料与公开主页）、PAP-96（邮件与邮箱验证）、PAP-98（账号安全）、PAP-100（注销）都只在这个模型上加东西，不再动 `idn_user` 的列和状态。

设计过程中定下的、影响别的 Issue 的事：

- 昵称存在资料表 `idn_user_profile`，不是 `idn_user` 的一列。本 Issue 建这张表的骨架（只有 `display_name`）和 `UserProfile` 聚合的骨架，PAP-95 在同一份 `V1` 上补齐其余列（第 6.3 节）。
- 登录、注册的成功响应改成 `{ sessionToken, user }`，`user` 和 `/auth/me` 是同一个结构；账号状态只出现在这两处，Redis 里的会话不加状态字段（第 8.2 节）。
- 409 带原因码的办法：唯一性冲突用 `errors[]`（字段 + 原因码），状态冲突用 `traits[]` 里多一个 identity 自己的特征（第 10 节）。PAP-98 的 `403 REAUTH_REQUIRED`、PAP-100 的「注销中拒绝写操作」都用这一套。
- 旧用户名的跳转是 `301` + JSON 体 `{ "username": "<新名>" }`，门户用 `redirect: "manual"` 取（第 8.5 节）。
- 公开身份卡对本人例外：本人请求自己的 `/users/{username}` 不受「邮箱未验证」「冷静期」的 404 约束，响应里用 `audience` 标明别人能不能看到；门户请求时要带上会话令牌（第 8.5 节）。
- 昵称没有默认值：`idn_user_profile.display_name` 可空，空表示「跟随用户名」，读出来时回退成当前用户名，所以改名后没设过昵称的人昵称自动跟着变（第 6.3、7.4 节）。

**完成标准**：PAP-93 的 Acceptance Criteria，逐条对应见第 13 节。

## 2. 范围

做：

- `V1__init_identity_schema.sql` 重写为最终形态，`V2` 删除：`idn_user` 加用户名、邮箱验证时间、四态状态与时间列、改名时间；新增 `idn_user_profile`（骨架）、`idn_user_former_username`；`idn_user_login_record` 并入。
- `User` 聚合重写：注册带用户名、邮箱可空（只在 `DELETED`）、`email_verified_at`、四态状态机的全部迁移方法、改名；`UserProfile` 聚合骨架；`FormerUsername` 聚合。
- `Username` / `UsernamePolicy`、`DisplayName` / `DisplayNamePolicy`、`UsernameChangePolicy`。
- 注册：`username` 必填，昵称默认等于用户名，同事务建资料行；邮箱已注册、用户名已占用各自给原因码。
- 登录按四态分流；登录、注册、`/auth/me` 的响应结构。
- `PATCH /me/username`；`GET /users/{username}`（公开身份卡、旧名跳转、本 Issue 能判断的 404 规则、本人例外）。
- 网关：`/patra-identity/users/**` 公开，`/patra-identity/me/**` 需登录（后者是现有默认规则，补测试）。
- 预留实体（外部身份、实名、职业认证记录）的表结构与说明，不建表（第 12 节）。
- 测试工具：`patra-tool-test` 加一个可拨动的时钟 `MutableClock`。
- identity README。

不做（都已有归属）：

- 发邮件、邮箱验证的写入路径、重发限流（PAP-96）。本 Issue 只有 `email_verified_at` 这一列和 `User.markEmailVerified()`。
- 资料的其余字段、隐私设置、`/me/profile`、`/me/privacy`、公开主页按可见性开关的 404（PAP-95）。
- 头像（PAP-97）。`avatarUrl` 在本 Issue 永远是 `null`，这正是「用默认头像」的最终含义。
- 注销的发起、取消、到期任务、匿名化、邮件（PAP-100）。本 Issue 交付聚合上的三个迁移方法和表上的约束，PAP-100 只写处理器和任务。
- 再认证、改密码、找回密码、改邮箱（PAP-98）；登录设备（PAP-99）。
- 后台接口的变化：封禁、解封的路径和行为不变，只是对 `DELETED` 用户返回 409。
- 旧用户名的清理任务：保留期按时间判断，过期的行留作审计，不需要清理。
- `@` 提及：本版没有内容，决策 L 的「存用户 ID」是给 interaction 的约束。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 登录标识只有邮箱；用户名不用于登录 | release spec 2.1.1、决策 B |
| 用户名：注册必填、全站唯一、3 ~ 30 个字符、`[a-z0-9_-]`、首尾字母或数字、不连续符号、不纯数字、不分大小写统一存小写、保留字做同形归一 | release spec 2.1.1 |
| 改用户名：两次修改间隔 ≥ 30 天；旧名保留 90 天，期间只有原主能改回、`/u/旧名` 301 到新名；90 天后释放 | 决策 L |
| 昵称：注册时默认等于用户名；2 ~ 30 个字素且 ≤ 120 字节；存 NFC；拒绝控制 / 零宽 / 双向字符与换行；拒绝网址、邮箱、手机号、微信号形态；拒绝保留字和官方 / 职业身份词；允许重复 | release spec 2.1.1 |
| 四态：`ACTIVE ⇄ BANNED`（后台）、`ACTIVE → PENDING_DELETION → ACTIVE / DELETED`、`BANNED → DELETED`（后台）；冷静期 15 天；`DELETED` 行保留、用户名永不释放、邮箱立即释放、`user_id` 永不复用 | 决策 H、2.1.9 |
| 登录分流：`BANNED` 403；`PENDING_DELETION` 可登录，会话与 `/auth/me` 带状态；`DELETED` 按密码错误处理 | PAP-93 To-Do、PAP-100 |
| 注册即登录，未验证邮箱的账号主页对别人不可见 | 决策 C、2.1.4、2.1.6 |
| 资料是账号旁边的独立聚合，与 `idn_user` 一对一；用户名、状态在账号表，昵称、头像在资料表 | 决策 A |
| `V1`、`V2` 合并重写为最终形态，不写 `ALTER`；本机与 mini 的 `patra_identity` 库直接重建，开发数据可丢弃 | 决策 J |
| 密码规则保持 8 ~ 64 个码点，不改；与 NIST SP 800-63B-4 的偏离由 PAP-98 的工程 spec 记录 | 决策 K |
| 手机号本版连可选存储也不做；实名是独立实体，只在工程 spec 里预留表结构 | 边界 A |
| 第三方登录不建表，只定规矩：外部身份按 `(provider, subject)` 定位，永不按邮箱自动合并 | 边界 C、决策 B |
| 会话模型、令牌、Redis 键、`/auth/me` 从断言取当前用户 | PAP-64 设计 |
| 网关：`/patra-identity/**` 默认需登录，公开名单例外；公开路由上照样翻译令牌 | PAP-65 设计 |
| 字段错误带原因码、4xx 记 WARN、429 带 `Retry-After`；`DomainException` 可带多个特征，错误引擎只认标准特征 | PAP-63 设计第 10 节、`patra-tool-core` |
| 时间来自注入的 `Clock`；Long 型 ID 在 JSON 里是字符串 | 测试约定、`patra-tool-web` |

版本：Java 25、Spring Boot 4.0.8、PostgreSQL 17、Hibernate 7、Lombok、MapStruct。

## 4. 业界参照

决策层面的调研在 release spec 的「调研依据」里，这里只记工程层面照抄或对照的细节：

| 事 | 参照 | 本设计 |
|---|---|---|
| 用户名字符集与边界 | GitHub：字母、数字、`-`，不能以 `-` 开头结尾、不能连续；Discourse `UsernameValidator`：3 ~ 20、`[A-Za-z0-9._-]`、首字母数字、末尾不能是 `.` `-` `_`、不连续特殊字符；Mastodon：`[a-z0-9_]+`、30 | 取并集里最严的一组：`[a-z0-9_-]`、3 ~ 30、首尾字母数字、`_` 和 `-` 都不能连续、不纯数字 |
| 保留字 | Discourse 站点设置 `reserved_usernames` 支持通配；Bluesky PDS 的 `RESERVED_SUBDOMAINS` 清单（路由段 + 角色词 + 品牌词） | 清单写在代码里，三组：路由段、角色词、站名与数据源名；匹配前做同形归一并去掉分隔符，整词匹配 |
| 改名 | GitHub：旧名立即释放，旧链接失效；Discourse：`username_change_period`（天）；Mastodon：不能改 | 决策 L 已定：30 天冷却 + 90 天保留，用一张「旧名」表承载多条未到期的旧名 |
| 昵称长度 | Bluesky `app.bsky.actor.profile.displayName`：`maxGraphemes: 64`、`maxLength: 640`（字素 + 字节双上限） | 同样的双上限：30 个字素、120 字节 |
| 字素计数 | JDK 20 起 `BreakIterator.getCharacterInstance()` 按 UAX #29 扩展字素簇切分；JDK 21 起 `Character.isEmoji*` / `isExtendedPictographic` | 直接用 JDK，不引第三方库；今天在 JDK 25 实测，见第 14 节 |
| 注销与匿名化 | Discourse `UserAnonymizer`、Mastodon 的 `suspended → deleted` | 行保留、邮箱置空、用户名永不释放（决策 H） |

来源：[GitHub 用户名规则](https://docs.github.com/en/account-and-profile/setting-up-and-managing-your-personal-account-on-github/managing-user-account-settings/changing-your-github-username)、[Discourse username_validator](https://github.com/discourse/discourse/blob/main/app/models/username_validator.rb)、[Mastodon account 模型](https://github.com/mastodon/mastodon/blob/main/app/models/account.rb)、[Bluesky handle 规范](https://atproto.com/specs/handle)、[atproto actor profile lexicon](https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/actor/profile.json)、[Discourse user_anonymizer](https://github.com/discourse/discourse/blob/main/app/services/user_anonymizer.rb)。

## 5. 模块与改动面

没有新模块。改六个，另加一个测试工具：

| 模块 | 改动 |
|---|---|
| `patra-identity-domain` | `User` 重写；新聚合 `UserProfile`、`FormerUsername`；值对象 `Username`、`DisplayName`；规则 `UsernamePolicy`、`DisplayNamePolicy`、`UsernameChangePolicy`；保留字与拒绝词常量；读模型 `UserAccountReadModel` 扩展、新增 `PublicUserReadModel`、`PublicUserLookup`；端口 `UserRepository` 扩展、新增 `UserProfileRepository`、`FormerUsernameRepository`、`UserReadPort` 扩展；异常：新增 `UserIdentifierTakenException`、`UsernameChangeCooldownException`、`UserPendingDeletionException`、`InvalidUserStateTransitionException`、`IdentityErrorTrait`，删除 `EmailAlreadyRegisteredException` |
| `patra-identity-app` | `RegisterUserCommand` 加 `username`（变 `@Builder`）；`RegisterUserHandler`、`LoginUserHandler` 改造；结果对象统一成 `AuthenticatedUser`；新增 `ChangeUsernameCommand` / `Handler`；`UserQueryService` 扩展（当前用户、公开身份卡） |
| `patra-identity-infra` | `V1` 重写、删 `V2`；`UserEntity` 加列；新增 `UserProfileEntity`、`FormerUsernameEntity` 及 Dao / Mapper / RepositoryAdapter；`UserRepositoryAdapter` 翻译两个唯一约束；`UserReadAdapter` 扩展 |
| `patra-identity-adapter` | `RegisterRequest` 加 `username`；响应改为 `AuthenticatedUserResponse { sessionToken, user }` + `CurrentUserResponse`；新增 `MeController`（`/me`）、`PublicUserController`（`/users`）及请求 / 响应记录 |
| `patra-identity-boot` | `IdentityProperties` 加 `username` 段；`IdentityConfiguration` 装配三个规则对象；`application.yml` 默认值；README |
| `patra-gateway-boot` | `GatewaySecurityConfiguration.IDENTITY_PUBLIC_PATHS` 加 `/patra-identity/users/**`；集成测试加两条路径规则 |
| `patra-tool-test`（新增一个类） | `dev.patra.tool.test.clock.MutableClock`：从固定时刻开始、可往前拨的 `Clock`，给 identity 的集成测试当 `@Primary` Bean（`LaunchAutoConfiguration` 的 `Clock` 是 `@ConditionalOnMissingBean`，测试配置里声明一个就替换） |

包位置按 `.claude/rules/tech/port-service.md`：端口 `domain/port/{repository|read}/`，实现 `infra/adapter/{persistence|read}/`，查询服务 `app/usecase/user/query/`，规则 `domain/policy/`，读模型 `domain/model/read/`。

## 6. 表结构（`V1` 最终形态）

库 `patra_identity`，脚本只有 `V1__init_identity_schema.sql`。五张表都带 `BaseJpaEntity` 的审计列（`record_remarks`、`version`、`ip_address`、`created_*`、`updated_*`）、`set_updated_at()` 触发器和列注释，写法照现在的 `V1`。

**本版内 `V1` 的维护方式**：决策 J 禁止 `ALTER` 迁移，所以 PAP-95（资料列、关注领域表）、PAP-96（令牌表）、PAP-99（登录记录加列）、PAP-100（登录记录结束原因）都在这同一份 `V1` 上原地改写，各自重建库。本 Issue 只写自己用到的列和取值，不预埋后续 Issue 的列。

### 6.1 `idn_user`

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键，雪花 ID，永不复用 |
| `email` | `VARCHAR(254)` | **可空**，只在 `DELETED` 为空；唯一 `uk_idn_user_email`（PostgreSQL 的唯一约束对多个 `NULL` 不冲突）；`ck_idn_user_email_lowercase`：`email IS NULL OR email = lower(email)`；`ck_idn_user_email_deleted`：`(status = 'DELETED') = (email IS NULL)` |
| `email_verified_at` | `timestamptz(6)` | 可空；`ck_idn_user_email_verified_at`：`email IS NOT NULL OR email_verified_at IS NULL` |
| `username` | `VARCHAR(30)` | 非空；唯一 `uk_idn_user_username`；`ck_idn_user_username_format`：`username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$' AND char_length(username) BETWEEN 3 AND 30 AND username !~ '^[0-9]+$'` |
| `username_changed_at` | `timestamptz(6)` | 可空，最近一次改名时间；从未改过为空 |
| `status` | `VARCHAR(16)` | 非空；`ck_idn_user_status`：`IN ('ACTIVE', 'BANNED', 'PENDING_DELETION', 'DELETED')` |
| `banned_at` | `timestamptz(6)` | `ck_idn_user_banned_at`：`(status = 'BANNED') = (banned_at IS NOT NULL)` |
| `deletion_due_at` | `timestamptz(6)` | `ck_idn_user_deletion_due_at`：`(status = 'PENDING_DELETION') = (deletion_due_at IS NOT NULL)` |
| `deleted_at` | `timestamptz(6)` | `ck_idn_user_deleted_at`：`(status = 'DELETED') = (deleted_at IS NOT NULL)` |

- 格式检查只守语法，保留字只在代码里判（清单会变，不该进库）。
- 四条「状态 ⇔ 时间列」的检查合起来保证：任何状态下恰好对应的那一列非空，其余为空；`DELETED` 时邮箱和验证时间都为空。
- 不加 `deletion_requested_at`：发起时间等于 `deletion_due_at` 减冷静期，PAP-100 需要发「到期前 3 天」提醒和记录已提醒时再加它自己的列。

### 6.2 `idn_user_password_credential`

不变。

### 6.3 `idn_user_profile`（骨架）

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 非空；唯一 `uk_idn_user_profile_user`；外键 `fk_idn_user_profile_user` → `idn_user(id)` |
| `display_name` | `VARCHAR(120)` | **可空**，空表示跟随用户名；`ck_idn_user_profile_display_name`：`display_name IS NULL OR (octet_length(display_name) BETWEEN 2 AND 120 AND display_name = btrim(display_name))` |

`VARCHAR(n)` 按字符数限制，120 字节不会超过 120 个字符，所以列长够用；字节上限靠 `octet_length` 守。字素数数据库数不了，由代码守。可空的理由见 7.4「没有默认昵称这个值」。PAP-95 在这张表上加简介、链接、身份大类、职称、机构、科室、三个隐私开关，并建关注领域表。

### 6.4 `idn_user_former_username`

一个用户改名后留下的旧名。一条旧名在 `reserved_until` 之前只有原主能改回、别人不能注册或改成它、`/users/旧名` 跳到新名；到期后行保留作审计，不再起任何作用。

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 非空；外键 `fk_idn_user_former_username_user` → `idn_user(id)` |
| `username` | `VARCHAR(30)` | 非空；`ck_idn_user_former_username_format` 同 `idn_user` 的格式检查 |
| `replaced_at` | `timestamptz(6)` | 非空，改名时间 |
| `reserved_until` | `timestamptz(6)` | 非空；`ck_idn_user_former_username_period`：`reserved_until > replaced_at` |

索引：`idx_idn_user_former_username_username (username, reserved_until)`、`idx_idn_user_former_username_user_id (user_id)`。不加唯一约束：同一个旧名可以在不同时期属于不同的人（A 释放后 B 注册再改名）；「同一时刻至多一条未到期」由注册和改名都查保留来保证，查询时按 `replaced_at` 倒序取第一条兜底。

### 6.5 `idn_user_login_record`

从 `V2` 原样并入，列、约束、索引、注释都不变（`end_reason` 仍是 `LOGOUT`、`BANNED`、`REPLACED`；`REVOKED` 由 PAP-99、`DELETION_REQUESTED` 由 PAP-100 在同一份 `V1` 上加）。

## 7. 领域模型

### 7.1 聚合与值对象

| 名称 | 类别 | 内容和行为 |
|---|---|---|
| `User` | 聚合根 | ID、邮箱（可空）、邮箱验证时间、用户名、改名时间、状态、封禁时间、注销到期时间、注销完成时间、版本、审计时间。`register(EmailAddress, Username)`；`restore()` 是 Lombok 生成的构建器（`@Builder(builderMethodName = "restore")` 放在私有构造器上，12 个字段不再写位置参数）；`getEmail()` 返回 `Optional<EmailAddress>`；状态迁移方法见 7.2；`changeUsername(Username, Instant, UsernameChangePolicy)` 见 7.5；`markEmailVerified(Instant)` 幂等，保留第一次时间，`DELETED` 时抛 `InvalidUserStateTransitionException`；`requireActive()` 见 7.2 |
| `UserStatus` | 枚举 | `ACTIVE`、`BANNED`、`PENDING_DELETION`、`DELETED` |
| `Username` | 值对象 | 规范化后的用户名（小写）。紧凑构造器只接受已规范化且语法合法的值；`of(raw)` 去首尾空白、转小写后构造；`violation(raw)` 返回第一条语法错误（空、字符、长度、首尾与连续符号、纯数字），保留字不在这里判 |
| `UsernamePolicy` | 领域服务 | `validate(raw)`：先 `Username.violation(raw)`，再查保留字（7.3）。没有依赖，装配成 Bean 只为了和 `PasswordPolicy` 一致 |
| `DisplayName` | 值对象 | 规范化后的昵称（NFC、去首尾空白、内部空白折叠成一个 U+0020）。没有「默认值」工厂：昵称为空时由读模型回退成用户名（7.4） |
| `DisplayNamePolicy` | 领域服务 | `normalize(raw)` 与 `validate(normalized)`（7.4）。PAP-95 的改昵称用它 |
| `UsernameChangePolicy` | 值对象 | `cooldown`（30 天）、`reservationPeriod`（90 天），构造时校验正数 |
| `UserProfile` | 聚合根 | ID、用户 ID、昵称（可空）、版本、审计时间。`create(userId)`（昵称为空）；`Optional<DisplayName> getDisplayName()`；`rename(DisplayName)`（PAP-95 用，本 Issue 只有单测；「清空昵称」的方法由 PAP-95 加）|
| `FormerUsername` | 聚合根 | ID、用户 ID、旧名、改名时间、保留到期时间。`reserve(userId, Username, now, reservationPeriod)`；`isReservedAt(now)` |
| `IdentityErrorTrait` | 枚举（实现 `ErrorTrait`） | identity 自己的语义特征，本 Issue 只有 `PENDING_DELETION`；随标准特征一起放进异常，错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它（第 10 节） |

读模型（`domain/model/read/`）：

| 名称 | 内容 |
|---|---|
| `UserAccountReadModel` | `@Builder`：`userId`、`email`（可空）、`emailVerified`、`username`、`displayName`、`status`、`deletionDueAt`（可空）、`nextUsernameChangeAt`（可空；读适配器按 `UsernameChangePolicy` 从 `username_changed_at` 算出，没改过名为 `null`）。给 `/auth/me` 和登录、注册的响应 |
| `PublicUserReadModel` | `@Builder`：`userId`、`username`、`displayName`（已回退）、`status`、`emailVerified`。`audience()`：`ACTIVE` 且邮箱已验证是 `PUBLIC`，否则 `SELF`；`isVisibleTo(Optional<Long> requesterId)`：`audience() == PUBLIC`，或请求者就是本人 |
| `PublicAudience` | 枚举：`PUBLIC`（谁都能看）、`SELF`（只有本人能看）。PAP-95 的「仅登录用户」加 `SIGNED_IN` |
| `PublicUserLookup` | 密封接口：`Found(PublicUserReadModel)`、`Moved(Username current)` |

### 7.2 状态机

| 方法 | `ACTIVE` | `BANNED` | `PENDING_DELETION` | `DELETED` |
|---|---|---|---|---|
| `ban(now)` | → `BANNED`，记 `bannedAt` | 不变（保留原时间） | → `BANNED`，清 `deletionDueAt`（注销申请作废） | 抛 `InvalidUserStateTransitionException` |
| `unban()` | 不变 | → `ACTIVE`，清 `bannedAt` | 不变 | 抛 |
| `requestDeletion(now, coolingPeriod)` | → `PENDING_DELETION`，`deletionDueAt = now + coolingPeriod` | 抛（被封禁的账号不能自助注销） | 不变（保留原到期时间） | 抛 |
| `cancelDeletion()` | 不变 | 抛 | → `ACTIVE`，清 `deletionDueAt` | 抛 |
| `completeDeletion(now)` | 抛（必须先申请） | → `DELETED`（后台将来用） | → `DELETED` | 不变（幂等，PAP-100 的任务重跑要靠它） |
| `markEmailVerified(now)` | 记第一次 | 记第一次 | 记第一次 | 抛 |
| `changeUsername(...)` | 见 7.5 | 抛 `AuthenticationRequiredException` | 抛 `UserPendingDeletionException` | 抛 `AuthenticationRequiredException` |

- 进入 `DELETED`：`email`、`emailVerifiedAt`、`deletionDueAt`、`bannedAt` 全部清空，`deletedAt = now`。用户名和 ID 留着。凭据、资料、令牌、头像的删除是 PAP-100 处理器的事，不在聚合里。
- `DELETED` 不能回退：表里的四条检查和聚合的方法一起守。
- `requireActive()`：`/me/**` 写接口的状态守卫。`ACTIVE` 通过；`PENDING_DELETION` 抛 `UserPendingDeletionException`（409，`traits` 含 `PENDING_DELETION`，PAP-100 要的「除取消注销与登出外的写操作被拒」就是它）；`BANNED`、`DELETED` 抛 `AuthenticationRequiredException`（401，和 `/auth/me` 一致：这个会话不再有效）。
- `InvalidUserStateTransitionException(from, to)`：`CONFLICT`，409，文案「账号当前状态不允许此操作」。本版只有后台的封禁、解封对 `DELETED` 用户会碰到它。

### 7.3 用户名规则（`UsernamePolicy`）

输入先去首尾空白、按 `Locale.ROOT` 转小写，再依次查，只报第一条：

| 顺序 | 规则 | 原因码 |
|---|---|---|
| 1 | 为空 | `REQUIRED` |
| 2 | 含 `[a-z0-9_-]` 之外的字符（转小写后判，所以大写字母合法；中文、空格、`.` 都不合法） | `INVALID_CHARACTER` |
| 3 | 少于 3 个字符 | `TOO_SHORT` |
| 4 | 多于 30 个字符 | `TOO_LONG` |
| 5 | 首或尾不是字母数字；`_` 或 `-` 连续出现（含 `_-`、`-_`） | `INVALID_FORMAT` |
| 6 | 全是数字 | `DIGITS_ONLY` |
| 7 | 命中保留字 | `RESERVED` |

长度按字符数：字符集是 ASCII，字符数就是字节数，和数据库的 `char_length` 一致。

**保留字匹配**：对输入做「同形归一」——`0 → o`、`1 → i`、`3 → e`、`5 → s`——再去掉全部 `_` 和 `-`，与清单整词比较。清单里的条目在代码里以同样的折叠形式保存。例：`adm1n`、`ad-min`、`a_d_m_i_n` 都命中 `admin`；`0fficial` 命中 `official`；`ncb1` 命中 `ncbi`；`admin2`、`patra-dev` 不命中（整词）。四个映射是 release spec 定的，`l` 不归一到 `i`：`l` 是合法字母，归一会误伤 `hello`、`lily` 这类名字。

**清单**（`domain/policy/ReservedUsernames`，三组，我拟的初稿，用户可增删）：

- 路由段：`api`、`app`、`auth`、`login`、`logout`、`register`、`signup`、`signin`、`signout`、`settings`、`account`、`profile`、`me`、`u`、`user`、`users`、`papers`、`paper`、`journals`、`journal`、`search`、`missing`、`about`、`help`、`docs`、`terms`、`privacy`、`legal`、`contact`、`static`、`assets`、`public`、`admin`、`dashboard`、`console`、`internal`、`actuator`、`health`、`status`、`blog`、`news`、`feed`、`explore`、`notifications`、`messages`、`inbox`、`collections`、`favorites`、`bookmarks`、`following`、`followers`、`discussions`、`comments`、`tags`、`topics`、`verify`、`reset`、`password`、`email`。
- 角色词：`admin`、`administrator`、`staff`、`official`、`system`、`support`、`moderator`、`mod`、`root`、`owner`、`operator`、`security`、`team`、`bot`、`robot`、`anonymous`、`guest`、`nobody`、`everyone`、`null`、`undefined`、`deleted`、`deleted-user`（折叠后 `deleteduser`）、`unknown`、`test`。
- 站名与数据源名：`patra`、`papertrace`、`pubmed`、`ncbi`、`nlm`、`nih`、`pmc`、`europepmc`、`epmc`、`crossref`、`medline`、`mesh`、`doi`、`orcid`、`ror`、`openalex`、`scopus`、`wos`、`clarivate`、`elsevier`、`springer`、`wiley`、`nature`、`lancet`、`nejm`、`jama`、`bmj`、`who`、`cdc`、`fda`、`nmpa`、`nhc`、`cnki`、`wanfang`。

`deleted` / `deleted-user` 进清单是因为 2.1.9 说注销用户的内容以后显示「已注销用户」。

### 7.4 昵称规则（`DisplayNamePolicy`）

`normalize(raw)`：NFC → `String.strip()` 去首尾空白 → 把每一段连续的空格类字符（`Character.isSpaceChar`，含 U+3000 全角空格、U+00A0）折叠成一个 U+0020。规范化后的值才是存库和比较的值。

`validate(normalized)` 依次查，只报第一条：

| 顺序 | 规则 | 原因码 |
|---|---|---|
| 1 | 为空（`null` 或规范化后空串） | `REQUIRED` |
| 2 | 含拒绝字符：控制字符（Cc，含制表、换行、回车）、行 / 段分隔符（Zl、Zp）、私用区（Co）、未分配码点（Cn）、孤立代理项、格式字符（Cf：零宽空格 / 连接符 / 不连字、BOM、软连字、全部双向控制符）——Cf 只有一种例外：它是 emoji 组件（`Character.isEmojiComponent`，即 ZWJ 和标签字符）且所在字素簇含有扩展象形字（`Character.isExtendedPictographic`），家庭 emoji 👨‍👩‍👧、旗帜序列才能通过；另外，字素簇以组合用字符（Mn / Mc / Me）开头也拒绝：只由组合字符拼成的昵称渲染出来是空白，字素数却是 1 ~ 2（我定的，release spec 没写） | `INVALID_CHARACTER` |
| 3 | 少于 2 个字素 | `TOO_SHORT` |
| 4 | 多于 30 个字素，或 UTF-8 超过 120 字节 | `TOO_LONG` |
| 5 | 形态：网址（`(?i)(https?://|www\.)`，或 `.` 后紧跟常见顶级域 `com cn net org io me top xyz info site app dev co cc tv edu gov ac` 再接结尾、空格或 `/`）；邮箱（`[^\s@]+@[^\s@]+\.[^\s@]{2,}`）；手机号（去掉空格、`-`、`.`、`()`、`+` 后出现 7 个以上连续数字；在 NFKC 副本上判，全角数字也算）；微信号（含 `微信` / `weixin` / `wechat`，或 `wx` / `vx` 后接冒号） | `INVALID_FORMAT` |
| 6 | 拒绝词：折叠后（NFKC、小写、去掉空白与 `·`、同 7.3 的同形归一）——中文词**包含**即命中，拉丁词按**整词**命中（折叠后按非字母数字切词） | `RESERVED` |

字素用 `BreakIterator.getCharacterInstance(Locale.ROOT)` 数，汉字、带变音符的字母、肤色和 ZWJ 组合的 emoji、国旗各计 1；字节用 UTF-8。

**拒绝词**（`domain/policy/RejectedDisplayNameWords`，我拟的初稿，用户可增删）：

- 官方与角色：`官方`、`官网`、`官微`、`管理员`、`客服`、`系统`、`运营`、`审核`、`认证`；`official`、`admin`、`administrator`、`staff`、`system`、`support`、`moderator`、`bot`。
- 职业身份：`医生`、`医师`、`大夫`、`主任`、`主治`、`教授`、`副教授`、`专家`、`院士`、`博导`、`院长`、`护士长`、`药师`、`研究员`；`doctor`、`dr`、`physician`、`surgeon`、`prof`、`professor`、`md`、`expert`、`specialist`、`nurse`。
- 站名与数据源：复用 7.3 第三组。

路由段不进昵称的拒绝词：昵称不进 URL，叫「Settings」没有危害。

**没有默认昵称这个值**：注册时资料行的 `display_name` 为空，读模型把空回退成当前用户名，所以 release spec 说的「注册时默认等于用户名」在接口上成立，库里却不存拷贝。好处：用户名改了，没设过昵称的人昵称自动跟着变，不会留下一份过期的旧用户名拷贝，90 天后旧名被别人注册时也不会出现两个账号顶着同一个昵称；用户自己把昵称设成和用户名一样的值是有意为之，照存不动。GitHub、Mastodon、Bluesky 都是这个做法（昵称可空，空就显示用户名）。用户名也因此不必过昵称规则：`weixin123`、`1234567a` 这类合法用户名会被昵称的形态规则拒掉，但它们从不作为昵称存。

### 7.5 改名规则

`User.changeUsername(next, now, policy)`：

1. `requireActive()`。
2. `next` 等于当前用户名：抛 `InvalidUserFieldsException`，`username` / `UNCHANGED`（422）。大小写不同的输入在 `Username.of` 已折成小写，所以 `Alice → alice` 也是 `UNCHANGED`。
3. `usernameChangedAt` 非空且 `now < usernameChangedAt + cooldown`：抛 `UsernameChangeCooldownException(剩余时长)`（429）。改回旧名同样受冷却约束：release spec 说「两次修改间隔 ≥ 30 天」，改回也是一次修改。
4. 换名，`usernameChangedAt = now`，返回旧名。

保留与占用由处理器判（第 8.4 节）：`idn_user` 里有人用、或别人的旧名未到期，都是 409 `username` / `ALREADY_TAKEN`；自己的旧名未到期可以改回。

从未改过名的用户第一次改没有冷却：`usernameChangedAt` 为空就放行。

### 7.6 端口

| 端口 | 实现（infra） | 方法 |
|---|---|---|
| `UserRepository` | `UserRepositoryAdapter` | 现有的 `findById`、`findByIdForUpdate`、`findByEmail`、`existsByEmail`、`save`，加 `findByUsername(Username)`、`existsByUsername(Username)`。`save` 撞上 `uk_idn_user_email` 转成 `UserIdentifierTakenException(email)`，撞上 `uk_idn_user_username` 转成 `UserIdentifierTakenException(username)`；乐观锁照旧转 `UserModifiedConcurrentlyException` |
| `UserProfileRepository` | `UserProfileRepositoryAdapter` | `save`（新行分配雪花 ID）、`findByUserId` |
| `FormerUsernameRepository` | `FormerUsernameRepositoryAdapter` | `save`；`findReserved(Username, now)`：未到期的最近一条；`existsReserved(Username, now)`（注册用）；`existsReservedByOther(Username, userId, now)`（改名用） |
| `UserReadPort` | `UserReadAdapter` | `findAccount(userId) → Optional<UserAccountReadModel>`（用户 + 资料，两次查询拼起来；资料行缺失是数据不一致，抛 `IllegalStateException`；`displayName` 为空回退成 `username`；`nextUsernameChangeAt` 在这里用注入的 `UsernameChangePolicy` 算）；`findPublicIdentity(Username) → Optional<PublicUserReadModel>`、`findPublicIdentityByUserId(userId) → Optional<PublicUserReadModel>`（同样回退昵称；后者给旧名跳转取原主的当前身份卡） |

`existsByUsername` 对 `DELETED` 的行一样返回 `true`：用户名永不释放。`existsByEmail` 天然不会命中 `DELETED` 的行（邮箱为空）。

## 8. 接口与流程

### 8.1 注册 `POST /auth/register`

请求体 `{ "email", "username", "password", "clientType"?, "deviceId"? }`。确认密码仍只在前端校验。

处理顺序（`RegisterUserHandler`）：

1. 五个字段一起校验，错误一次报全（422）：邮箱、密码、客户端照旧；用户名用 `UsernamePolicy.validate`。
2. 查占用，两项一起查、一起报（409，`errors[]` 可能有两条）：`existsByEmail` → `email` / `ALREADY_REGISTERED`；`existsByUsername` 或 `existsReserved(username, now)` → `username` / `ALREADY_TAKEN`。别人的旧名在保留期内对注册者来说就是「已被使用」，不区分。
3. 哈希密码（事务外，照旧）。
4. 一个事务里：存用户（`User.register(email, username)`）→ 存资料（`UserProfile.create(userId)`，昵称为空）→ 存凭据 → 建会话。并发撞唯一约束时仓储转成同一个 409。
5. 返回 `AuthenticatedUser(token, account)`。

`PENDING_DELETION` 用户的邮箱和用户名都还在，注册同邮箱返回 409；`DELETED` 用户的邮箱已释放，可以注册，用户名仍被占。

响应 `201`：

```json
{
  "sessionToken": "patra_user_…",
  "user": {
    "userId": "352303128713027974",
    "email": "chen.yu@example.com",
    "emailVerified": false,
    "username": "chen-yu",
    "displayName": "chen-yu",
    "avatarUrl": null,
    "status": "ACTIVE",
    "deletionDueAt": null,
    "nextUsernameChangeAt": null,
    "accountType": "user"
  }
}
```

`user` 就是 `/auth/me` 的响应体（`CurrentUserResponse`），由 `UserAccountReadModel` 映射：`displayName` 是回退后的值，永远非空；`nextUsernameChangeAt = usernameChangedAt + cooldown`，没改过为 `null`；`avatarUrl` 本 Issue 恒为 `null`（PAP-97 填）。

### 8.2 登录 `POST /auth/login`

请求体不变。PAP-64 的七步只在「密码正确之后」分流：

| 状态 | 结果 |
|---|---|
| `ACTIVE` | 建会话，200 |
| `PENDING_DELETION` | 建会话，200，`user.status` 是 `PENDING_DELETION`、`deletionDueAt` 有值。门户据此进「取消注销」页（PAP-102） |
| `BANNED` | 403 `UserBannedException`（照旧，在成功结算之后） |
| `DELETED` | 和密码错误完全一样：按失败结算、401。`DELETED` 的行邮箱为空、凭据已删，正常情况下按邮箱根本查不到，走假哈希那条路；处理器仍显式判一次 `isDeleted()`，万一查到了也按失败处理，不记成功 |

事务内加行锁重读后同样复核：封禁 403、已注销 401。

**「会话带状态」的解读**：状态写在登录、注册响应的 `user` 里和 `/auth/me` 里，Redis 会话 HASH 不加字段。理由：状态在数据库里，会话里存一份就会过期（取消注销后会话里的标记就错了，除非再去改每条会话）；网关本版不按状态做任何事；`/auth/me` 读库比读 Redis 更准。PAP-100 的 To-Do 写着「会话标记 `pendingDeletion`」，按本设计应改成「登录响应与 `/auth/me` 带 `status`」（第 17 节）。

### 8.3 当前用户 `GET /auth/me`

`UserQueryService.currentAccount()`：`currentUserPort.require()` → `userReadPort.findAccount(userId)`。

| 情况 | 结果 |
|---|---|
| 没有当前用户、用户不存在、`BANNED`、`DELETED` | 401 `AuthenticationRequiredException`（记 WARN） |
| `ACTIVE`、`PENDING_DELETION` | 200 `CurrentUserResponse`（8.1 的 `user`） |

`status` 在响应里只会出现 `ACTIVE` 和 `PENDING_DELETION`，另两个值对应 401。

### 8.4 改用户名 `PATCH /me/username`

请求体 `{ "username": "new-name" }`。`ChangeUsernameHandler`，`@Transactional`：

1. `currentUserPort.require()`。
2. `UsernamePolicy.validate` → 422。
3. `users.findByIdForUpdate(userId)`，查不到抛 `AuthenticationRequiredException`。行锁让同一用户的两次并发改名串行。
4. `user.changeUsername(next, now, policy)`：状态守卫（401 / 409）、`UNCHANGED`（422）、冷却（429）。
5. `users.existsByUsername(next)` 或 `formerUsernames.existsReservedByOther(next, userId, now)` → 409 `username` / `ALREADY_TAKEN`。
6. `users.save(user)`（并发撞 `uk_idn_user_username` → 同一个 409）；`formerUsernames.save(FormerUsername.reserve(userId, 旧名, now, reservationPeriod))`。

响应 `200`：`{ "username": "new-name", "nextUsernameChangeAt": "2026-11-19T08:00:00Z" }`。

改回自己的旧名：旧名那条记录留着不动，当前名又变成旧名、再留一条；`/users/{名}` 先查 `idn_user`，所以改回之后旧名的记录自然不起作用。

不做再认证（2.1.7 列的敏感操作里没有改用户名），不撤销任何会话，不发邮件，不碰资料表：没设过昵称的人，昵称本来就是回退出来的用户名，改名后自动变成新名。

### 8.5 公开身份卡 `GET /users/{username}`

路径里的用户名先按 `Username.of` 规范化（去空白、转小写）；语法不合法直接 404，不查库。

`UserQueryService.publicIdentity(raw) → PublicUserLookup`，请求者 = `currentUserPort.current()`（公开路由上网关照样翻译令牌，登录用户带令牌来就有当前用户，没带就是匿名）：

1. `findPublicIdentity(username)` 查到 → `isVisibleTo(请求者)` 为真返回 `Found`，否则 404。可见的条件：`audience() == PUBLIC`（`ACTIVE` 且邮箱已验证），或请求者就是这个用户本人。
2. 查不到 → `formerUsernames.findReserved(username, now)` 有 → 按 `userId` 取原主的当前身份卡：`isVisibleTo(请求者)` 为真返回 `Moved(当前用户名)`，否则 404。
3. 都没有 → 404。

**本人例外**：release spec 2.1.4、2.1.6 说的都是主页「对别人」不存在、「对别人」不可见，2.1.5 还有「仅自己」这一档，所以本人看自己的主页永远能看到（只要还能登录，即 `ACTIVE` 或 `PENDING_DELETION`）。本人看到的响应里 `audience` 是 `SELF`，门户据此显示「目前只有你能看到」；别人也能看到时是 `PUBLIC`。本人用自己的旧名请求同样 301。门户请求这个接口时有会话令牌就要带上，否则本人也会 404。

Controller 把 `Found` 映射成 `200`，`Moved` 映射成 `301`：

```
HTTP/1.1 301 Moved Permanently
Location: /users/new-name
Content-Type: application/json

{ "username": "new-name" }
```

```json
{ "username": "chen-yu", "displayName": "陈宇", "avatarUrl": null, "audience": "PUBLIC" }
```

- 404 一律是 `UserNotFoundException`（`IDN-0404`，「用户不存在」），对非本人的访客，匿名和已登录一样；不可见的原因不区分，不给出任何「这个名字存在但你看不到」的信号。
- `301` 同时给 `Location`（HTTP 语义）和 JSON 体（门户要的新名）。`Location` 是 identity 自己的相对路径，经网关转发后对门户没有直接用处，门户按体里的 `username` 做 `permanentRedirect('/u/<新名>')`，并且请求时必须用 `redirect: "manual"`，否则 Node 的 `fetch` 会顺着 `Location` 去请求网关根路径（第 16 节）。网关配置 `redirects: DONT_FOLLOW` 已保证网关不替客户端跟随。
- 响应不含邮箱、ID、状态、任何设置。PAP-95 在这个结构上加简介、链接、专业背景、关注领域、加入时间，并把可见性开关加进 `audience()` / `isVisibleTo`（「仅登录用户」对应新值 `SIGNED_IN`）。

### 8.6 后台接口

`POST /admin/users/{userId}/ban`、`/unban` 路径与成功响应不变；封禁会清掉 `PENDING_DELETION` 的到期时间；对 `DELETED` 用户两者都返回 409。

## 9. 网关路由规则

`GatewaySecurityConfiguration`：

| 路径 | 规则 | 改动 |
|---|---|---|
| `/patra-identity/users/**` | 公开 | 加进 `IDENTITY_PUBLIC_PATHS`。用 `**` 而不是单段：PAP-97 的头像读取也在 `/users/{username}/avatar/…` 下，同样公开；`/users/` 下不会有需登录的东西，用户自己的东西都在 `/me/` |
| `/patra-identity/me/**` | 需登录 | 现有默认规则（`IDENTITY_PATHS` 整体 `authenticated()`）已覆盖，补集成测试 |
| 其余 | 不变 | — |

公开路由上网关照样翻译令牌：登录用户访问 `/users/{username}` 时下游拿得到断言，PAP-95 的「仅登录用户可见」靠这个。

## 10. 错误契约

前缀 `IDN`，状态码由语义特征映射。新增和改动：

| 场景 | 异常 | 特征 | 状态 | 错误码 | 额外信息 | detail |
|---|---|---|---|---|---|---|
| 字段不合法 | `InvalidUserFieldsException` | `RULE_VIOLATION` | 422 | `IDN-0422` | `errors[]` | 请求参数不合法 |
| 邮箱已注册 / 用户名已被使用（含并发撞唯一约束、别人的旧名未到期） | `UserIdentifierTakenException`（新，实现 `HasFieldViolations`，替代 `EmailAlreadyRegisteredException`） | `CONFLICT` | 409 | `IDN-0409` | `errors[]`：`email` / `ALREADY_REGISTERED`、`username` / `ALREADY_TAKEN`，可同时两条 | 邮箱或用户名已被使用 |
| 改名冷却期内 | `UsernameChangeCooldownException`（新，实现 `HasRetryAfter`） | `QUOTA_EXCEEDED` | 429 | `IDN-0429` | `Retry-After`、`retryAfterSeconds`（剩余时长向上取整到秒） | 用户名修改过于频繁，请稍后再试 |
| 注销中不能做写操作 | `UserPendingDeletionException`（新） | `CONFLICT` + `IdentityErrorTrait.PENDING_DELETION` | 409 | `IDN-0409` | `traits` 含 `PENDING_DELETION` | 账号正在注销中，取消注销后再操作 |
| 状态不允许的迁移（后台对 `DELETED` 用户封禁 / 解封等） | `InvalidUserStateTransitionException`（新） | `CONFLICT` | 409 | `IDN-0409` | — | 账号当前状态不允许此操作 |
| 公开身份卡不存在或不可见 | `UserNotFoundException`（现有，不再只用于后台） | `NOT_FOUND` | 404 | `IDN-0404` | — | 用户不存在 |
| 邮箱或密码错误（含 `DELETED`） | `InvalidCredentialsException` | `UNAUTHORIZED` | 401 | `IDN-0401` | — | 邮箱或密码错误 |
| 需要登录；会话指向的用户不存在、已封禁、已注销 | `AuthenticationRequiredException` | `UNAUTHORIZED` | 401 | `IDN-0401` | — | Authentication required |

其余（403 封禁、429 登录限流、409 乐观锁、503）不变。

**为什么 409 能带原因码**：

- 唯一性冲突是「某个字段的值不可用」，和 422 的字段错误是同一种信息，所以复用 `HasFieldViolations` 的 `errors[]`，前端按 `errors[].field` 和 `code` 选文案；只是状态码按语义是 409。
- 状态冲突不属于任何字段。`DomainException` 允许多个特征，`DefaultErrorResolutionEngine` 遍历特征时只认 `TRAIT_TO_CODE_MAP` 里的标准特征，identity 自己的 `IdentityErrorTrait` 被跳过、不影响状态码，而 `ProblemDetailBuilder` 把全部特征名写进 `traits[]`。于是 `traits: ["CONFLICT", "PENDING_DELETION"]` 就是「409 + 原因码」，不用给错误格式加字段。PAP-98 的 `403 REAUTH_REQUIRED` 用同一个办法：`FORBIDDEN` + `IdentityErrorTrait.REAUTH_REQUIRED`。
- 两个标准特征放在一起时状态码取决于 `Set.of` 的遍历顺序，所以一个异常只放一个标准特征。

**原因码一览**（字段名就是请求体字段名）：

| 字段 | 原因码 |
|---|---|
| `email` | `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`ALREADY_REGISTERED`（409） |
| `username` | `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`DIGITS_ONLY`、`RESERVED`、`UNCHANGED`、`ALREADY_TAKEN`（409） |
| `displayName` | `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`RESERVED`（本 Issue 只有规则和单测，PAP-95 的接口才会返回） |
| `password`、`clientType`、`deviceId` | 不变 |

409 示例：

```json
{
  "title": "IDN-0409", "status": 409, "detail": "邮箱或用户名已被使用",
  "code": "IDN-0409", "traits": ["CONFLICT"], "path": "/auth/register",
  "errors": [
    { "field": "email", "code": "ALREADY_REGISTERED", "rejectedValue": null, "message": "该邮箱已注册" },
    { "field": "username", "code": "ALREADY_TAKEN", "rejectedValue": null, "message": "该用户名已被使用" }
  ]
}
```

日志：用户名是公开信息，可以进日志；邮箱、密码、令牌照旧不进。

## 11. 配置

```yaml
patra:
  identity:
    username:
      change-cooldown: 30d       # 两次改名的最短间隔
      reservation-period: 90d    # 旧名保留多久
```

`IdentityProperties` 加 `Username(Duration changeCooldown, Duration reservationPeriod)`，`IdentityConfiguration` 把它变成 `UsernameChangePolicy`，非正数启动失败；同时声明 `UsernamePolicy`、`DisplayNamePolicy` 两个无依赖的 Bean。Nacos 里不用加键，mini 上验收 D7 时如果不想等 30 天，可以在 `patra-identity.yaml` 临时把冷却改短。

注销冷静期的配置项归 PAP-100：聚合方法接收 `Duration`，本 Issue 的单测直接传 15 天。

## 12. 预留但本版不建的实体

三张表按最终形态写在这里，到对应版本再进 `V1`。它们都只引用 `idn_user(id)`，加表时不动现有表和聚合。

### 12.1 外部身份 `idn_user_external_identity`（第三方登录，边界 C）

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 非空；外键 → `idn_user(id)`；`DELETED` 时整行删除（匿名化清单，PAP-100 的「以后的外部身份」） |
| `provider` | `VARCHAR(32)` | 非空；检查取值如 `ORCID`、`WECHAT`、`GITHUB`，到时定 |
| `subject` | `VARCHAR(255)` | 非空，提供方的稳定主体标识（ORCID iD、`unionid`、GitHub 数字 ID） |
| `email_at_provider` | `VARCHAR(254)` | 可空，只作参考，永不用于合并账号（决策 B） |
| `display_name_at_provider` | `VARCHAR(255)` | 可空 |
| `linked_at` | `timestamptz(6)` | 非空 |
| `last_login_at` | `timestamptz(6)` | 可空 |
| `raw_profile` | `jsonb` | 可空，提供方返回的资料快照 |

约束：`UNIQUE (provider, subject)`、`UNIQUE (user_id, provider)`。规矩（决策 B）：首次用外部身份登录时邮箱撞上已有账号，不合并，要求先用原方式登录再关联；OAuth 首次登录补填用户名和邮箱，邮箱仍由 Patra 自己验证；ORCID / Scopus 等标识符只经这张表写入，不手输。

### 12.2 实名 `idn_user_identity_verification`（边界 A）

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 非空；外键 → `idn_user(id)` |
| `method` | `VARCHAR(32)` | 非空；`PHONE_SMS`、`CTID`（国家网络身份认证）、`NATIONAL_ID` 等 |
| `status` | `VARCHAR(16)` | 非空；`PENDING`、`VERIFIED`、`FAILED`、`REVOKED` |
| `phone_encrypted` | `bytea` | 可空，应用层加密 |
| `phone_hash` | `CHAR(64)` | 可空，SHA-256（带全局盐），用于按号码查重 |
| `id_number_encrypted` / `id_number_hash` | `bytea` / `CHAR(64)` | 可空，同上 |
| `real_name_encrypted` | `bytea` | 可空 |
| `provider_reference` | `VARCHAR(255)` | 可空，认证服务商的流水号 |
| `verified_at` / `revoked_at` | `timestamptz(6)` | 可空；`(status = 'VERIFIED') = (verified_at IS NOT NULL)` |

约束：部分唯一索引 `(method, phone_hash) WHERE status = 'VERIFIED'`，一个号码同一时间只能实名一个账号；`(user_id, method)` 唯一。明文永不落库；密钥在 `~/.patra/secrets/`。`idn_user` 上不加手机号列（边界 A）。

### 12.3 职业认证记录 `idn_user_professional_certification`（边界 B）

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 非空；外键 → `idn_user(id)` |
| `type` | `VARCHAR(32)` | 非空；`PHYSICIAN_LICENSE`、`NURSE_LICENSE`、`PHARMACIST_LICENSE`、`RESEARCHER_AFFILIATION` 等 |
| `status` | `VARCHAR(16)` | 非空；`SUBMITTED`、`UNDER_REVIEW`、`APPROVED`、`REJECTED`、`EXPIRED`、`REVOKED` |
| `materials` | `jsonb` | 非空，材料的对象键列表（存 MinIO 私有桶） |
| `submitted_at` | `timestamptz(6)` | 非空 |
| `reviewed_at` / `reviewed_by` | `timestamptz(6)` / `BIGINT` | 可空；`reviewed_by` 指向以后的 `idn_staff(id)` |
| `review_note` | `TEXT` | 可空 |
| `valid_until` / `next_review_at` | `timestamptz(6)` | 可空，有效期与复核 |
| `badge_label` | `VARCHAR(50)` | 可空，通过后公开展示的标识文案 |

约束：`(status = 'APPROVED') → (reviewed_at IS NOT NULL AND valid_until IS NOT NULL)`；同一用户同一类型只允许一条 `APPROVED`（部分唯一索引）。资料里的身份大类、职称、机构、科室是自述，这张表里的 `APPROVED` 记录才让公开主页出现认证标识。

## 13. 测试策略

时间全部走注入的 `Clock`。单元测试直接传 `Instant`；集成测试用 `patra-tool-test` 新增的 `MutableClock` 当 `@Primary` Bean 往前拨。

### 13.1 单元测试（不起 Spring）

- `Username` / `UsernamePolicy`（对应 AC 1）：3 / 30 边界（2、3、30、31）；`Chen_Yu` 折成 `chen_yu`；首尾 `_` `-`；`a__b`、`a--b`、`a_-b`；`123456`；每一组保留字各取一个；同形归一 `adm1n`、`0fficial`、`ncb1`、`ad-min`、`a_d_m_i_n` 命中，`admin2`、`hello`（`l` 不归一）不命中；首尾空白被去掉；只报第一条。
- `DisplayName` / `DisplayNamePolicy`（对应 AC 2）：字素边界 1 / 2 / 30 / 31（分别用汉字、emoji、ZWJ 家庭、国旗、带肤色的 👍🏽 组成）；120 字节边界（40 个汉字通过、41 个被拒）；`e + U+0301` 规范化成 `é`；全角空格折叠；控制字符、U+200B、U+200D 在字母之间、U+202E、换行各自被拒；家庭 emoji 和 🏴󠁧󠁢󠁥󠁮󠁧󠁿 旗帜通过；网址（`patra.dev`、`https://x`、`www.x`）、邮箱、手机号（含 `138 1234 5678` 和全角数字）、微信号各一；`张医生`、`Dr. Wang`、`0fficial` 被拒，`Andrea Wang`（含 `dr` 但不是整词）、`医学生`（不含「医生」整词）通过；只由组合字符拼成的 `"́́"` 被拒。
- `User`（对应 AC 5）：7.2 表里每一格各一个用例，含 `DELETED` 对每个方法都抛、`completeDeletion` 幂等、`ban` 清注销到期；`changeUsername` 的四种结果（成功并记时间、`UNCHANGED`、冷却剩余时长、首次改名无冷却）；`markEmailVerified` 保留第一次。
- `FormerUsername`：`reserve` 的到期时间；`isReservedAt` 的边界（到期当刻不再保留）。
- `UserProfile`：`create` 后昵称为空、`rename`。
- `RegisterUserHandler`：五个字段的错误一次报全；邮箱和用户名同时被占时 409 带两条；用户名被别人的旧名保留时 409；成功时资料行昵称为空、三次保存和建会话都在事务里。
- `LoginUserHandler`：密码正确后 `PENDING_DELETION` 照常建会话且结果带状态；`DELETED` 按失败结算、不调 `recordSuccess`；封禁 403 不变。
- `ChangeUsernameHandler`：顺序（校验 → 加锁 → 聚合 → 占用 → 保存两处）；每种失败对应的异常；改回自己的旧名放行。
- `UserQueryService`：`currentAccount` 对 `DELETED` 也是 401、`PENDING_DELETION` 是 200 带到期时间；`publicIdentity` 的三条路径（找到、旧名跳转、404），以及「跳转目标不可见也是 404」、「未验证邮箱对别人 404、对本人 200 且 `audience` 是 `SELF`」、`PENDING_DELETION` 对别人 404 对本人 200、本人用旧名 301、语法不合法不查库。
- `IdentityConfiguration`：冷却或保留期非正数启动失败。
- `MutableClock`：拨动后 `instant()` 变化。

### 13.2 集成测试

infra（PG 容器）：

- `IdentitySchemaConstraintsIT` 重写：用户名格式（大写、`a--b`、`-ab`、纯数字、2 个字符、31 个字符）；四条状态 ⇔ 时间列；`DELETED` 必须邮箱为空、`ACTIVE` 不能邮箱为空；邮箱为空时验证时间必须为空；资料表字节上限、可空；旧名表 `reserved_until > replaced_at`；两张新表的外键；登录记录的约束照旧。
- `UserRepositoryAdapterIT`：两个唯一约束各转成带正确字段的 `UserIdentifierTakenException`；`findByUsername` / `existsByUsername` 对 `DELETED` 行；邮箱为空的行能保存和恢复。
- `FormerUsernameRepositoryAdapterIT`（固定时钟）：`findReserved` 到期前后；`existsReservedByOther` 排除本人；同名多条取最近一条。
- `UserReadAdapterIT`：`findAccount` 在昵称为空时回退成用户名、设了昵称时用昵称；`findPublicIdentity` 的字段与同样的回退。

adapter 切片（`@WebMvcTest`）：

- `AuthControllerIT` 改：注册请求多传 `username`；登录、注册响应的嵌套结构；`/auth/me` 的全部字段与 `null` 的输出。
- `MeControllerIT`（新）：`PATCH /me/username` 的 200、422（含 `UNCHANGED`）、409 的 `errors[]`、409 的 `traits` 含 `PENDING_DELETION`、429 的 `Retry-After`、401。
- `PublicUserControllerIT`（新）：200 的字段、301 的 `Location` 与 JSON 体、404；路径带大写被折成小写后查询。

boot 端到端（PG + Redis 容器，`RANDOM_PORT`，`MutableClock`）：

- `AccountFlowIT` 改：不带用户名注册 422 并带原因码；重复用户名 409 与重复邮箱 409 的 `errors[]` 各不相同、同时重复时两条都在（AC 3）；大小写不同的用户名算同一个。
- `UsernameChangeFlowIT`（新，AC 4）：注册 → 改名成功 → 立刻再改 429 且 `retryAfterSeconds` 约等于 30 天 → 拨 30 天后改回旧名成功 → 别人在 90 天内注册或改成该旧名 409 → `/users/旧名` 301 到新名 → 拨到 90 天后别人注册成功，`/users/旧名` 变成那个人的 200；期间 `/auth/me` 的 `nextUsernameChangeAt` 正确。
- `UserStatusFlowIT`（新，AC 6）：用 SQL 把用户改成四种状态（`DELETED` 时同时置空邮箱、删凭据），分别登录：`ACTIVE` 200、`PENDING_DELETION` 200 且 `user.status` / `deletionDueAt` 正确、`BANNED` 403、`DELETED` 401 且响应体与密码错误一致；`/auth/me` 对 `PENDING_DELETION` 200、对另两种 401；`PATCH /me/username` 对 `PENDING_DELETION` 409 且 `traits` 含 `PENDING_DELETION`；封禁 `DELETED` 用户 409。
- `PublicIdentityFlowIT`（新）：未验证邮箱时匿名 404、本人带断言 200 且 `audience` 为 `SELF` → SQL 写 `email_verified_at` 后匿名 200 且 `audience` 为 `PUBLIC` → 封禁后 404；改名后没设昵称的人 `displayName` 跟着变成新名。

网关（`GatewayAuthenticationIT`）：匿名访问 `/patra-identity/users/x` 被转发（WireMock 收到请求）；匿名访问 `/patra-identity/me/username` 401 且 WireMock 没收到；带令牌访问 `/patra-identity/me/username` 被转发并带断言；identity 回 301 时网关原样回 301、体和 `Location` 不变。

回归：`./gradlew spotlessApply`，`./gradlew check`，identity 与 gateway 的 `integrationTest`。`check` 不含集成测试，两个都要跑。本机 Docker 在线。

### 13.3 库重建（AC 7）

本机（Docker PG，端口 15432）和 mini 各执行一次：`DROP DATABASE patra_identity; CREATE DATABASE patra_identity;`，再启动 identity，Flyway 只跑 `V1`。`patra-infra/docker/postgres/init-scripts/02-create-databases.sql` 只在首次初始化时执行，不用改。不做 `pg_dump`：开发数据可丢弃。

## 14. 实施时要实测的点

今天已经实测的（JDK 25.0.1，zulu）：

| # | 结论 | 结果 |
|---|---|---|
| 1 | `BreakIterator.getCharacterInstance(Locale.ROOT)` 按扩展字素簇切分 | 成立：👨‍👩‍👧（5 个码点）计 1、🇨🇳 计 1、`e + U+0301` 计 1、👍🏽 计 1、`张三` 计 2；正则 `\X` 结果相同 |
| 2 | 零宽、双向控制符、BOM、软连字的 `Character.getType` 都是 `FORMAT`（16）；ZWJ U+200D 也是 `FORMAT`，所以「拒绝 Cf」必须给 emoji 组合留例外 | 成立；`Character.isEmojiComponent(0x200D)` 为真，标签字符 U+E0067 也是组件，👨 `isExtendedPictographic` 为真，肤色 U+1F3FD 是 `isEmojiModifier` |
| 3 | U+3000 全角空格 `isSpaceChar` 为真、`isWhitespace` 为真；U+00B7 `·` 是 `OTHER_PUNCTUATION`，不会被当空白折叠 | 成立 |

实施时实测的结果（2026-10-20，Testcontainers PostgreSQL 17 / Redis 7.0.15）：

| # | 结论 | 结果 |
|---|---|---|
| 4 | PostgreSQL 17 的 `CHECK (username ~ '^[a-z0-9]([a-z0-9]\|[_-][a-z0-9])*$' AND char_length(username) BETWEEN 3 AND 30 AND username !~ '^[0-9]+$')` | 成立：大写、连续分隔符、首尾分隔符、纯数字、2 个字符都被约束拒绝；31 个字符先撞 `VARCHAR(30)`（`value too long for type character varying(30)`），到不了 CHECK。`IdentitySchemaConstraintsIT` 每个用例只做一条失败插入：`@DataJpaTest` 的事务在第一条失败语句后被 PG 置为中止（`25P02`），同一事务里的第二条不再执行 |
| 5 | 错误引擎对 `{CONFLICT, PENDING_DELETION}` 映射成 409、`traits` 两个都输出 | 成立：`MeControllerIT` 拿到 `IDN-0409`、`traits: ["CONFLICT", "PENDING_DELETION"]` |
| 6 | `RestTestClient` 对 301 的处理 | 切片成立、真端口不成立：`@WebMvcTest` 下它绑定 MockMvc，`expectStatus().isEqualTo(301)` 能拿到 `Location` 和 JSON 体（`PublicUserControllerIT`）；`RANDOM_PORT` 下它底层是 Apache HttpClient，会自动跟随 301，还会按 `Retry-After` 自动重试 429（冷却是 30 天，等于挂死）。真端口的用例改用 `RestClient` + `JdkClientHttpRequestFactory`、`HttpClient.Redirect.NEVER`（`UsernameChangeFlowIT`、`PublicIdentityFlowIT`、`GatewayAuthenticationIT`）。网关 `redirects: DONT_FOLLOW` 下 301 的状态、`Location`、体原样透传：成立 |
| 7 | Lombok `@Builder(builderMethodName = "restore")` 与 `@Getter` / 手写 `getEmail()` 共存；MapStruct 对 `email` 的映射 | 成立：构建器放私有全参构造器上，类级 `@Getter` 加字段上的 `@Getter(AccessLevel.NONE)`、手写 `Optional<EmailAddress> getEmail()`，spotless / Error Prone / SpotBugs 都不报；`UserJpaMapper` 对 `email` 用 `expression`（`imports = EmailAddress.class`）显式映射，`toDomain` 走 `User.restore()` |
| 8 | `Duration` 配置 `30d` / `90d` 的绑定 | 成立：`DurationStyle` 认 `d` 后缀，`IdentityConfigurationTest` 覆盖默认值、覆盖值与非正数启动失败 |
| 9 | `@WebMvcTest` 切片对新 Controller | 成立：`@Import(Controller)` + `@MockitoBean`（`CommandBus` / `UserQueryService`），`MeControllerIT`、`PublicUserControllerIT` |
| 10 | MOCK 环境不经过安全过滤器链 | 成立，且多一条：identity 验断言 `exp` 用的是容器里的 `Clock`，拨动 `MutableClock` 后用真实时间签的 60 秒断言全部「过期」，测试要用同一个时钟签（`new IdentityAssertionSigner(TestSigningKey.key(), clock)`，`UsernameChangeFlowIT`） |

顺带踩到、后续 Issue 会再碰的：

- Spring Security 的 `StrictHttpFirewall` 对路径里的 `%20`、`%25` 等百分号编码直接 400（不是 404）：「语法不合法 404」的用例只能用未编码的非法名（`/users/ab`）；门户侧 `/u/[username]` 转发前要先按用户名规则过滤。
- google-java-format 会把源码里的反斜杠 u 转义改写成字面字符，U+2028 会被当成换行导致「unclosed string literal」；注释里出现反斜杠 u 也会被 javac 当成 unicode 转义报错。不可见字符用按码点构造的辅助方法，注释里不写反斜杠 u。
- `UserReadAdapterIT` 用 JDBC 改行后 JPA 一级缓存仍返回旧对象，查前要 `entityManager.clear()`。
- SpotBugs：字段只在非构造路径赋值报 UWF，返回内部集合报 MS_EXPOSE_REP；`FormerUsername` 用全参构造器，`ReservedUsernames.siteAndSourceNames()` 返回 `Set.copyOf`。
- `ChangeUsernameHandlerTest` 的 `findByIdForUpdate` 桩要用 `thenAnswer` 每次给新实例：处理器会改聚合，共享实例会把第一次的改名带进第二个用例。

库重建（13.3，AC 7）：本机 2026-10-20 用 README 第 8 节的两条 SQL 重建 `patra_identity` 后启动，Flyway 只应用 `V1`（`flyway_schema_history` 一行，五张表）；经本地网关走通注册（带 `username`）→ `/auth/me` → `PATCH /me/username`（200；立刻再改 429，`Retry-After: 2592000`）→ `GET /users/<旧名>`（本人 301；匿名未验证 404，验证后 301 + `Location` + 体）→ `GET /users/<新名>` 200。mini 的重建随后端 PR 部署执行（README 第 8 节的运行手册：先 `docker stop patra-identity`，重建后 `docker start`）。

## 15. README

`patra-api/patra-identity/README.md` 改这几节：

1. 第 2 节接口表：注册请求加 `username`；登录、注册响应改为 `{ sessionToken, user }`；`/auth/me` 的字段；新增 `PATCH /me/username`（需登录）、`GET /users/{username}`（公开，含 301）。
2. 第 3 节账号模型：五张表；用户名与昵称规则、保留字与拒绝词在哪个类；四态状态机和迁移表。
3. 第 6 节错误码表：第 10 节的新行与原因码。
4. 新增「用户名」一节：改名冷却、旧名保留、配置项。
5. 第 7 节本地运行：库重建的两条 SQL。

## 16. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| `idn_user_profile` 已有可空的 `display_name`（空 = 跟随用户名），在同一份 `V1` 上加其余列和关注领域表；`UserProfile` 聚合已有 `create` / `rename`，改昵称走 `DisplayNamePolicy`，`PUT /me/profile` 传空昵称就是清空（回到跟随），要加 `clearDisplayName()`；注册建资料行、`/auth/me` 读昵称都已在本 Issue 完成，PAP-95 的这两条 To-Do 作废；`GET /users/{username}` 在 `PublicUserReadModel` 上扩展字段，本人例外已实现，可见性开关加进 `audience()` / `isVisibleTo`，`PublicAudience` 加 `SIGNED_IN`；`UserNotFoundException` 继续作公开 404 | PAP-95 |
| `User.markEmailVerified(now)` 写 `email_verified_at`；注册成功后发验证邮件的钩子放在 `RegisterUserHandler` 事务提交之后 | PAP-96 |
| `403 REAUTH_REQUIRED` 用 `FORBIDDEN` + `IdentityErrorTrait.REAUTH_REQUIRED`；`/me/**` 的写处理器先 `findByIdForUpdate` 再 `requireActive()`；登录响应的 `user` 结构不变，`/auth/reauth` 换令牌后也返回它 | PAP-98 |
| `idn_user_login_record` 在 `V1` 上加列与 `REVOKED` | PAP-99 |
| 聚合的 `requestDeletion` / `cancelDeletion` / `completeDeletion` 已实现并有单测；`deletion_due_at` / `deleted_at` 的检查约束已在表上；写接口对 `PENDING_DELETION` 的拒绝复用 `requireActive()`（409 + `traits` 含 `PENDING_DELETION`）；到期执行时 `completeDeletion` 已经把邮箱置空，处理器再删凭据、资料、令牌、头像；`DELETED` 用户的旧名跳转自动变 404，不用处理；登录响应与 `/auth/me` 带 `status`、`deletionDueAt`，Redis 会话不加标记 | PAP-100 |
| 注册请求加 `username`；登录、注册响应读 `user`；`/auth/me` 新字段进 `CurrentUser`：`emailVerified`、`username`、`displayName`、`avatarUrl`（`null` 用默认头像）、`status`、`deletionDueAt`、`nextUsernameChangeAt`；409 看 `errors[].field` 区分邮箱和用户名；用户名原因码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`DIGITS_ONLY`、`RESERVED`、`ALREADY_TAKEN`；`/u/[username]` 请求 `GET /patra-identity/users/{username}` 时有会话令牌就带上（本人才能看到未验证时的主页），`fetch` 用 `redirect: "manual"`，301 时读体里的 `username` 做 `permanentRedirect('/u/<新名>')`，404 原样；`audience` 为 `SELF` 时显示「目前只有你能看到」 | PAP-101 |
| 账号页改用户名：`PATCH /me/username`，422（含 `UNCHANGED`）、409、429（`retryAfterSeconds` 换算成天显示）、`nextUsernameChangeAt` 做冷却提示；`status` 为 `PENDING_DELETION` 时进取消注销页 | PAP-102 |
| 前端用户名校验与后端同一条规则：`^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$`、3 ~ 30、非纯数字、输入先转小写；保留字只在后端判 | PAP-101 |

## 17. 要同步改的文档和 Issue

本设计评审通过后一起改：

- release spec 2.1.4：`/auth/me` 的字段补上 `deletionDueAt`、`nextUsernameChangeAt`；公开主页一节补「本人看自己的主页不受这些 404 约束」；2.1.1 加一句「登录、注册的成功响应里 `user` 与 `/auth/me` 同构」，昵称一栏补「库里不存默认值，空表示跟随用户名，改名后没设过昵称的人昵称跟着变」；决策 H 补「会话不带状态，状态只在响应里」。
- Linear PAP-93：To-Do 的「公开主页接口对保留期内的旧名返回指向新名的重定向信息」注明「301 + JSON 体」；AC 不变。
- Linear PAP-95：删掉「注册时同事务创建资料行」「`/auth/me` 的昵称从资料读」两条 To-Do（本 Issue 完成），`idn_user_profile` 改为「在骨架上加列」。
- Linear PAP-100：「会话标记 `pendingDeletion`」改为「登录响应与 `/auth/me` 带 `status`」；「409 带原因码」注明用 `traits` 的 `PENDING_DELETION`。
- Linear PAP-98：`403 REAUTH_REQUIRED` 注明用 `IdentityErrorTrait`。
- Linear PAP-101 / PAP-102：第 16 节的契约。
- identity README：第 15 节。

## 18. 设计中由我定的点与待拍板的点

**我定的（builder 可直接采用，用户看到不对就推翻）**：

1. 旧名用独立表 `idn_user_former_username` 而不是 `idn_user` 加 `previous_username` 列：30 天冷却 + 90 天保留意味着同一用户可同时有三条未到期的旧名。
2. `User.restore()` 用 Lombok 构建器，不写 12 个位置参数。
3. 保留字整词匹配、昵称拒绝词「中文包含 + 拉丁整词」；`l` 不做同形归一。
4. 昵称默认等于用户名时不过昵称规则。
5. `PATCH /me/username` 新名与当前相同返回 422 `UNCHANGED`，不当成功；改回旧名也受冷却约束。
6. `BANNED → DELETED`、`PENDING_DELETION → BANNED` 在聚合里允许（后台将来用），`PENDING_DELETION → BANNED` 作废注销申请。
7. 旧名表不加唯一约束、不清理。
8. 公开身份卡在本 Issue 只返回 `username`、`displayName`、`avatarUrl`；加入时间等随 PAP-95。
9. `MutableClock` 放进 `patra-tool-test`，identity-session 里已有的 `AdjustableClock` 不动。
10. 网关公开 `/patra-identity/users/**`（两段以上也公开）。
11. 公开身份卡对本人例外（依据 release spec 2.1.4 / 2.1.6 的「对别人」），响应加 `audience`（`PUBLIC` / `SELF`）告诉门户别人能不能看到；reviewer 指出的矛盾由此解决。
12. 昵称可空、空即跟随用户名，库里不存「默认等于用户名」的拷贝，改名不碰资料表；reviewer 提出的三个选项里选了这个，理由见 7.4。

**待拍板（改变做出来的东西）**：

1. 昵称存在 `idn_user_profile`（本 Issue 建骨架表）而不是 `idn_user` 加列；PAP-95 的两条 To-Do 因此提前完成。
2. 登录、注册响应改成 `{ sessionToken, user }`，`user` 与 `/auth/me` 同构；门户要改读法。
3. 「会话带状态」解读为「响应带状态」，Redis 会话不加字段；PAP-100 的 To-Do 随之改。
4. 409 的原因码方式：唯一性冲突走 `errors[]`，状态冲突走 `traits[]`；改名冷却用 429 + `Retry-After`。
5. `/auth/me` 比 release spec 2.1.4 多两个字段：`nextUsernameChangeAt`（账号页的冷却提示）、`deletionDueAt`（取消注销页）。
6. 旧名跳转契约：`301` + `Location` + JSON 体 `{ "username" }`，门户 `redirect: "manual"`。
7. 保留字与拒绝词清单的内容（第 7.3、7.4 节的初稿）。
