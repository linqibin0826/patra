# identity 账号工程设计（PAP-63）

> **Issue**：[PAP-63](https://linear.app/papertrace/issue/PAP-63)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)
> **产品输入**：设计简报 `patra-portal/docs/design-briefs/v0.8/accounts.md` 附录 A
> **日期**：2026-10-05
> **状态**：已实现

## 1. 要解决的问题

v0.8 要让访客用邮箱注册并登录。账号、凭据、会话都归新服务 `patra-identity`（release spec 决策 A）。本 Issue 交付其中的账号部分：服务骨架、注册、登录时的凭据校验、登录失败限制、封禁与解封。登录成功后签发会话令牌属于 PAP-64，本 Issue 做到「凭据校验通过」为止。

设计过程中定下了三件影响别的 Issue 的事：

- 账号分「前台用户」和「后台账号」两类，分开建表；账号类型叫 `USER` 和 `STAFF`（第 4 节）。
- 封禁和解封是后台接口，放在 `/admin/` 下，不放 `/_internal/`（第 9 节）。
- linqibin-commons 的统一错误格式补三处：字段错误带原因码、4xx 不再按 ERROR 记日志、429 带剩余等待时间（第 10 节）。

**完成标准**：PAP-63 的 Acceptance Criteria。其中「封禁 / 解封接口位于 `/_internal/` 路径下」改为 `/admin/`，见第 17 节。

## 2. 范围

做：

- 新服务 `patra-identity` 的五个模块和构建接入。
- 前台用户的注册、登录校验、登录失败限制。
- 后台接口：封禁、解封。
- linqibin-commons 的三处改动，以及测试 starter 补一个 Redis 容器。
- 把 PAP-62 里的账号类型 `AccountType.PORTAL` 改名为 `USER`。
- 服务 README。

不做：

- 会话令牌、Redis 会话、登录记录、登出、「当前用户」接口。属于 PAP-64。
- 网关的路由和规则（PAP-65）；建库、compose、CD、Redis 密码（PAP-66）。
- 后台账号的表、登录和角色。本版只定名字。
- 邮箱验证、找回密码、修改密码、第三方登录、个人资料、验证码（release spec 边界 B、C、G）。
- 按 IP 限流。
- 接入安全 starter。本 Issue 没有需要「当前用户」的接口，PAP-64 加「当前用户」接口时再接。
- `patra-identity-api` 模块。它只放服务之间的 HTTP Interface 契约，本 Issue 没有这种契约，第一次有内容时再建。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 自建 `patra-identity`，独立库 `patra_identity`，用户 ID 是雪花 Long | release spec 决策 A |
| 会话是不透明令牌 + Redis 会话，网关查会话后把身份写进请求头 | release spec 决策 B、C |
| 账号分两类，分开建模；会话带账号类型，前台的会话进不了后台接口 | release spec 决策 D，第 4 节的调研 |
| 认证框架用 Spring Security；密码哈希只需要 `spring-security-crypto` | release spec 决策 I，PAP-62 工程设计第 16 节 |
| 本版内部接口和后台接口都信任内网，不做服务间的机器身份 | release spec 边界 F |
| 字段规则、失败语义、文案 | 设计简报 §4、附录 A |
| linqibin-commons 只为 Patra 服务，可以直接改 | PAP-62 工程设计第 3 节 |
| Long 型 ID 在 JSON 里输出为字符串 | starter-core 的 `JacksonAutoConfiguration` |

版本：Spring Boot 4.0.8、Spring Security 7.0.7（`spring-security-crypto` 7.0.7 里有 `Argon2PasswordEncoder`）、BouncyCastle 1.86、门户的 Zod 4.6.5。

## 4. 账号模型

### 4.1 前台用户和后台账号分开建表

设计时把「两类账号共用一套表、靠角色区分」重新评估了一次，调研了 50 多个系统，结论是维持决策 D。

| 做法 | 代表 | 共同点 |
|---|---|---|
| 一张表，用角色或标志区分 | GitLab、Discourse、Mastodon、WordPress、Saleor、Directus、Odoo、Liferay；Django、Laravel、JHipster 的默认做法；SpringBlade | 管理员只是被提升了权限的普通用户，比如社区、开发平台的成员 |
| 分开建表 | Magento、PrestaShop、Sylius、Shopify、Medusa、Broadleaf、Shopizer；Spree 从 5.2 起拆开；Strapi、Ghost、PocketBase；芋道、mall、litemall、newbee-mall | 前台是自己注册的外部用户，数量大；后台是少量员工，账号由管理员创建 |
| 在身份域这一层分开 | Microsoft Entra 的员工租户和客户租户、Okta 的两条产品线、Keycloak 官方示例的两个 realm | 注册方式、认证强度、规模、提权风险都不同 |

Patra 属于第二类。几条对设计有直接影响的发现：

- **分开的是账号数据，认证机制共用。** Sylius 两类用户继承同一个父类，Medusa 和 Broadleaf 微服务版是一个认证服务管两个用户池，芋道共用令牌表、按用户类型校验路径前缀。identity 也这样：密码哈希、会话、失败限制共用，账号分表，会话带账号类型。
- **合表的真实事故都出在公开接口能写到角色字段。** WordPress 的 Ultimate Member 插件（CVE-2023-3460）公开注册就能把自己写成管理员，已被在野利用。SpringBlade 只靠令牌里的角色字符串区分前后台，公开的提权漏洞多数出在这条边界上。分表从结构上去掉这条路；但根因是请求字段绑定没有白名单，分表后照样要注意。
- **只分表不够，令牌也要分清来源。** 微软 Storm-0558 事件里，两套体系的令牌验证共用了密钥元数据，消费者密钥签的令牌被企业邮箱接受。identity 的对应做法：会话里的账号类型由 identity 写入，请求方改不了；以后后台接口只认 `STAFF` 会话。
- **规范不管表结构。** OWASP ASVS 和 NIST SP 800-53 都没有规定要不要分表，硬要求是管理接口强认证、最小权限、留审计。
- **键要按账号类型分开命名。** 失败计数、会话这类按邮箱或用户建的 Redis 键带上账号类型，以后同一个邮箱的前台账号和后台账号互不影响。
- **账号类型和客户端类型是两回事。** BladeX 的 `user_type` 表示 web / app / 其他终端，不表示前台还是后台。Patra 已经把二者分成 `AccountType` 和 `ClientType`（PAP-62），保持不变。

主要出处：[Entra 租户配置](https://learn.microsoft.com/en-us/entra/external-id/tenant-configurations)、[Okta 多租户](https://developer.okta.com/docs/concepts/multi-tenancy/)、[Keycloak 管理指南](https://www.keycloak.org/docs/latest/server_admin/index.html)、[Magento 表结构](https://github.com/magento/magento2/blob/2.4-develop/app/code/Magento/User/etc/db_schema.xml)、[Sylius 安全配置](https://github.com/Sylius/Sylius-Standard/blob/2.0/config/packages/security.yaml)、[Shopify StaffMember](https://shopify.dev/docs/api/admin-graphql/latest/objects/StaffMember)、[Medusa actor](https://docs.medusajs.com/resources/commerce-modules/auth/auth-identity-and-actor-types)、[Spree 6.0 计划](https://github.com/spree/spree/blob/main/docs/plans/6.0-platform-auth.md)、[Strapi](https://docs.strapi.io/cms/features/users-permissions)、[Ghost](https://github.com/TryGhost/Ghost/blob/main/docs/codebase/authentication.md)、[芋道用户体系](https://doc.iocoder.cn/user-center/)、[GitLab 管理员模式](https://docs.gitlab.com/administration/settings/sign_in_restrictions/)、[Ultimate Member 漏洞](https://wpscan.com/blog/hacking-campaign-actively-exploiting-ultimate-member-plugin/)、[Storm-0558 调查](https://www.microsoft.com/en-us/msrc/blog/2023/09/results-of-major-technical-investigations-for-storm-0558-key-acquisition)、[OWASP ASVS 5.0 V8](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x17-V8-Authorization.md)、[SpringBlade](https://github.com/chillzhuang/SpringBlade)。

### 4.2 用户和凭据分开

主流有两派：应用框架（ASP.NET Core Identity、Django、Devise、Spring Security 默认表结构、Supabase Auth）把密码哈希作为用户表的一列，第三方登录另放一张表；身份产品（Keycloak、Ory Kratos）把用户和凭据分开，凭据表带类型字段，秘密数据存 JSON。

identity 介于两者之间：密码从用户表移出去，但不用「通用凭据表 + JSON」，而是每种凭据一张有明确列的表。现在只有密码表；以后加第三方登录，就加一张带 `(provider, subject)` 唯一约束的表。这样做的好处：

- 密码哈希只出现在注册和登录两条路径上。封禁、PAP-64 的「当前用户」、以后的后台用户列表都只加载用户，哈希不会跟着用户对象被误写进日志或响应。
- 改密码、找回密码只动凭据；加第三方登录不改用户表。
- 明确的列让数据库能做约束。Keycloak、Kratos 用 JSON 是为了支持插件扩展的凭据类型，identity 用不上。

代价：注册时一个事务建两个聚合；登录要查两次库。

### 4.3 命名

| | 前台 | 后台 |
|---|---|---|
| 中文 | 前台用户 | 后台账号 |
| 账号类型 | `USER`（header 值 `user`） | `STAFF`（以后加） |
| 表 | `idn_user`、`idn_user_password_credential` | `idn_staff`、`idn_staff_password_credential`、角色表（以后建） |
| 领域对象 | `User`、`UserPasswordCredential` | `Staff` 等（以后建） |
| 接口前缀 | `/auth/...` | `/admin/...`，登录是 `/admin/auth/...` |

- 不用 `portal`：它是门户这个 Web 前端的名字，以后 App、小程序用的是同一批前台账号。
- 不用 `admin` 命名后台账号：管理员只是后台的一种角色，以后还有运营等。Shopify 的 staff account、Saleor 和 Django 的 `is_staff` 都是这个叫法。
- `/admin/` 指后台这个应用，不是管理员角色，和 Shopify admin、Django admin site 的用法一致。

## 5. 模块

新增五个模块，Gradle 路径都在 `:patra-api:patra-identity` 下，包名根是 `dev.linqibin.patra.identity`。

| 模块 | 插件 | 主要依赖 | 内容 |
|---|---|---|---|
| `patra-identity-domain` | `hexagonal-domain` | `linqibin-commons-core`、`patra-common-security` | 聚合、值对象、领域服务、端口、领域异常 |
| `patra-identity-app` | `hexagonal-app` | domain、starter-core | 四个命令处理器 |
| `patra-identity-infra` | `hexagonal-infra` | domain、starter-jpa、`spring-boot-starter-data-redis`、`spring-security-crypto`、`bcprov-jdk18on` | 仓储实现、密码哈希、常见密码名单、失败限制、Flyway 脚本 |
| `patra-identity-adapter` | `hexagonal-adapter` | app、starter-web | 前台的 `AuthController`，后台的 `AdminUserController` |
| `patra-identity-boot` | `hexagonal-boot` | adapter、infra、starter-web、starter-observability、starter-openapi | 启动类、配置、README |

- `spring-security-crypto` 不依赖 Spring Security 的过滤器链和自动配置，加进来不会触发 Boot 的默认登录规则，和 PAP-62「Spring Security 只经安全 starter 进入 classpath」不冲突。
- 构建接入：`settings.gradle.kts` 加五条 `includeAt` 和 `mapParent(":patra-api:patra-identity", ...)`。
- 模块图：`dumpModuleGraph` 加 `identity` 单元（`patra-api/patra-identity` 下的模块都归它），重新生成 `module-graph.json`。CD 按 `services.json` 的服务名部署，identity 的条目由 PAP-66 加；两者在同一个后端 PR 里。CI 全量运行的单元列表是 `patra-infra/cd/detect-changes.sh` 里写死的 `ALL_UNITS`，已加入 identity，CI 也预拉 Redis 测试镜像；CD 只在 main 上运行，`services.json` 的条目由 PAP-66 在同一个后端 PR 里加。

## 6. 领域模型

### 6.1 聚合与值对象

| 名称 | 类别 | 内容和行为 |
|---|---|---|
| `User` | 聚合根 | ID、邮箱、状态、封禁时间、版本、审计时间。`register(EmailAddress)` 新建正常状态的用户；`ban(Instant)`、`unban()` 见第 9 节 |
| `UserStatus` | 枚举 | `ACTIVE`、`BANNED` |
| `UserPasswordCredential` | 聚合根 | ID、用户 ID、`PasswordHash`、版本、审计时间。`create(userId, hash)` |
| `EmailAddress` | 值对象 | 构造时规范化并校验（第 7.3 节），只保存小写形式 |
| `PlainPassword` | 值对象 | 用户输入的明文密码，构造时只拒绝空值和不合法的 Unicode。`toString()` 输出 `***` |
| `PasswordHash` | 值对象 | Argon2 的编码串。`toString()` 输出 `***` |
| `PasswordPolicy` | 领域服务 | 注册时的密码规则：长度、常见名单 |
| `LoginThrottlePolicy` | 值对象 | 失败次数上限、计数窗口、锁定时长 |

时间都来自注入的 `Clock`。

### 6.2 端口

| 端口 | 实现（infra） | 方法要点 |
|---|---|---|
| `UserRepository` | `UserRepositoryAdapter` | 按邮箱查、按 ID 查、保存。保存时撞上邮箱唯一约束，转成 `EmailAlreadyRegisteredException` |
| `UserPasswordCredentialRepository` | `UserPasswordCredentialRepositoryAdapter` | 按用户 ID 查、保存 |
| `PasswordHashingPort` | `PasswordHashingAdapter` | 哈希；校验；拿假哈希做一次校验（第 8.3 节） |
| `CommonPasswordPort` | `CommonPasswordAdapter` | 判断规范化后的密码是否在名单里 |
| `LoginThrottlePort` | `LoginThrottleAdapter` | 开始一次尝试；按成功、失败、取消结算这次尝试（第 8.2 节）。参数是账号类型和 `EmailAddress` |

包位置按 `.claude/rules/tech/port-service.md`：端口在 `domain/port/{repository|hashing|password|throttle}/`，实现在 `infra/adapter/{persistence|hashing|password|throttle}/`。

### 6.3 表

库 `patra_identity`，Flyway 脚本 `V1__init_identity_schema.sql`。两张表都带 `BaseJpaEntity` 的标准审计列（`record_remarks`、`version`、`ip_address`、`created_*`、`updated_*`），写法照 object-storage 的初始脚本，包括 `set_updated_at()` 触发器和列注释。

`idn_user`：

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `email` | `VARCHAR(254)` | 非空；唯一约束 `uk_idn_user_email`；检查约束 `email = lower(email)` |
| `status` | `VARCHAR(16)` | 非空；检查约束取值为 `ACTIVE`、`BANNED` |
| `banned_at` | `timestamptz(6)` | 可空；检查约束：状态为 `BANNED` 时非空，否则为空 |

`idn_user_password_credential`：

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键 |
| `user_id` | `BIGINT` | 非空；唯一约束；外键指向 `idn_user(id)` |
| `password_hash` | `VARCHAR(255)` | 非空。Argon2 编码串约 100 个字符 |

雪花 ID 由仓储在第一次保存时分配，沿用现有做法。

## 7. 注册

### 7.1 接口

`POST /auth/register`，请求体 `{ "email": "…", "password": "…" }`。确认密码只在前端校验，不传给后端。

成功返回 `201`，本 Issue 的响应体是 `{ "userId": "…", "email": "…" }`。PAP-64 在成功响应里加上会话令牌。

### 7.2 处理顺序

1. 两个字段各自校验，错误合并后一次返回 422。
2. 查邮箱是否已注册，已注册返回 409。
3. 哈希密码。哈希期间不持有数据库连接：哈希放在事务外，并关闭 OSIV（`spring.jpa.open-in-view: false`），否则请求第一次查库拿到的连接会一直占到请求结束，而哈希可能排队几秒；只有两次保存在事务里。
4. 同一个事务里先存用户、拿到 ID，再存凭据。并发注册同一个邮箱时，后到的撞上唯一约束，同样返回 409，响应里不带数据库的报错信息。

先校验字段再查重：字段不合法时不查库，也不做哈希。

### 7.3 字段规则

每条字段错误都带一个原因码，前端按原因码选文案。

| 字段 | 规则 | 原因码 |
|---|---|---|
| 邮箱 | 先去掉首尾空格，为空 | `REQUIRED` |
| | 超过 254 个字符 | `TOO_LONG` |
| | 格式不对 | `INVALID_FORMAT` |
| | 通过后转成小写（`Locale.ROOT`）保存 | — |
| 密码 | 不去空格；为空 | `REQUIRED` |
| | 含孤立的 UTF-16 代理项，不是合法的 Unicode 文本 | `INVALID_CHARACTER` |
| | 少于 8 个码点 | `TOO_SHORT` |
| | 多于 64 个码点 | `TOO_LONG` |
| | 在常见密码名单里 | `TOO_COMMON` |

- **邮箱格式**：和 Zod 4.6.5 默认的 `z.email()` 用同一条正则，抄自 `zod/v4/core/regexes.js` 的 `email`。它只认 ASCII，规则接近 Gmail。前后端同一条规则，不会出现前端放行、后端拒绝。
- **密码长度**：按码点数，`String.codePointCount`。一个汉字、一个表情都算 1 位，和前端 `[...password].length` 一致。长度按原始输入算，不按 NFKC 之后算。
- **孤立代理项**：浏览器正常输入产生不了，只会出现在手工构造的请求里。拒绝它是为了让「同一个密码」只有一种字节表示。登录时 `PlainPassword` 同样拒绝。

### 7.4 常见密码名单

- 来源：Django `CommonPasswordValidator` 自带的名单，2 万条，由 Royce Williams 整理，文件 `django/contrib/auth/common-passwords.txt.gz`。Django 是 BSD 许可，文件旁边放一份它的许可声明，并在 README 里记下取自哪个 Django 版本和文件的 SHA-256。
- 比较方式：对密码做 NFKC、转小写、去掉首尾空白（`strip()`）后查表。Django 也是转小写再比，所以 `Password1` 和 ` password1 ` 都会被拒。
- 加载：启动时把整份名单读进一个 `Set`，条目做同样的规范化，不按长度过滤。长度按原始输入算，比较前却要先规范化、去空白，两边的长度可能不一样。比如 ` 123456 ` 原始长度是 8，规范化后正是名单里的 `123456`；如果把名单里 8 位以下的条目删掉，它就能注册成功。Django 也是整份加载。

### 7.5 密码哈希

- 算法：Argon2id，参数取 OWASP Password Storage Cheat Sheet 的最低配置 `m=19456 KiB（19 MiB）、t=2、p=1`，盐 16 字节，输出 32 字节。用 `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)`，编码串自带参数，以后调参不影响旧哈希的校验。
- 哈希前对密码做 NFKC。登录校验时同样处理，同一个密码在全角、半角或不同输入法下得到同样的结果。
- 没有 BCrypt 的 72 字节限制：Argon2 对输入长度没有限制，密码经 NFKC 变长也全部参与计算。
- 并发上限：同时进行的哈希计算最多 4 个，超出的排队，等 3 秒仍拿不到就抛 `TemporarilyUnavailableException`，返回 503。每次 Argon2 要占 19 MiB 内存，不设上限时几百个并发登录就能把内存打满。两个数都可以配置：`patra.identity.password-hashing.max-concurrent`、`wait-timeout`。

## 8. 登录校验与失败限制

### 8.1 接口与处理顺序

`POST /auth/login`，请求体 `{ "email": "…", "password": "…" }`。本 Issue 成功时返回 `200` 和 `{ "userId": "…", "email": "…" }`，PAP-64 改为返回会话令牌。

1. 校验字段：邮箱用 `EmailAddress` 的规则；密码只拒绝空值和孤立代理项，不查长度，也不查常见名单（简报 §3.2：密码规则以后可能调整，旧密码不该被拦住）。不合法返回 422。
2. 开始一次尝试（第 8.2 节）。处于锁定期，或者同一账号的失败次数加上正在校验的次数已经到了上限，直接返回 429，不查库、不做哈希。
3. 按邮箱查用户，再按用户 ID 查凭据。
4. 查不到用户或凭据：拿假哈希做一次校验（第 8.3 节），按失败结算，返回 401。
5. 密码不对：按失败结算，返回 401；这次失败让次数达到上限时，上锁并返回 429。
6. 密码正确：按成功结算，失败计数清零。账号被封禁返回 403，否则成功。
7. 第 3 到 6 步中途出错（哈希排队超时、数据库或 Redis 异常）：按取消结算，这次不算失败，原来的错误照常返回。

由此，401 分不出邮箱不存在还是密码错；403 只在邮箱和密码都对时出现；429 对没注册过的邮箱也一样。

登录只读数据库，处理器不开写事务。PAP-64 加登录记录时再改。

### 8.2 失败限制

| 项 | 设计 |
|---|---|
| 计数单位 | 账号类型 + 规范化后的邮箱，不管邮箱有没有注册 |
| Redis 键 | 每个计数单位三个键：失败计数 `idn:login-failures:{账号类型}:{哈希}`、正在校验的尝试 `idn:login-inflight:{账号类型}:{哈希}`、锁 `idn:login-lock:{账号类型}:{哈希}`。哈希是邮箱的 SHA-256 十六进制，键里不放明文邮箱 |
| 阈值 | 计数窗口 15 分钟（从窗口内第一次失败算起），失败 5 次就锁定 15 分钟。中间成功一次就清零。三个数可配置：`patra.identity.login-throttle.max-failures`、`window`、`lock-duration`。在途登记的过期时间也可配置：`in-flight-ttl`，默认 30 秒。 |
| 锁定期间 | 一律返回 429，不论密码对不对、邮箱有没有注册；这期间的尝试不计数，也不延长锁定 |
| Redis 不可用 | 返回 503，不在没有限制的情况下放行 |

**失败和正在校验的尝试分开记。** 只有确认的失败才计数、才会上锁；正在校验的尝试单独登记，只用来限制并发。每次尝试分「开始」和「结算」两步，各用一段 Lua 脚本原子完成。

开始，在校验密码之前：

1. 锁存在：返回剩余时间，这次返回 429。
2. 清掉已经过期的在途登记。失败次数加上在途次数已经到了上限：这次返回 429，`retryAfterSeconds` 为 1，不上锁。
3. 否则登记一条在途记录（随机 ID，30 秒后过期），放行。

结算，在校验之后必定执行一次：

- 先删掉这条在途登记。
- **成功**：删掉失败计数，不动锁。
- **失败**：处于锁定期就不计数，这次返回 429。否则失败计数加一，第一次失败时设窗口过期时间；加一后达到上限就上锁、删计数，这次返回 429，没达到返回 401。
- **取消**：只删在途登记。

这样能保证：

- **一个窗口里进入密码校验的尝试最多 5 次。** 放行的条件是「失败次数 + 在途次数」小于上限，失败结算时一个减一、一个加一，总数不会超过上限。并发再多，超出的也只会拿到 429。
- **只有确认的失败才会上锁。** 同时提交的几次正确密码、因 503 中断的尝试，都不会留下计数。
- **不用记录尝试属于哪个窗口。** 成功只是清零失败计数；在它之后才结算的失败，计入当时的窗口，不会凭旧的计数触发上锁。
- **崩溃不会一直占着名额。** 进程崩溃、没来得及结算的在途登记，30 秒后自动过期。

因为「失败次数 + 在途次数」到了上限而返回的 429，只会出现在脚本并发提交的时候。前端提交期间会禁用按钮，正常用户碰不到。

Redis 客户端用 `spring-boot-starter-data-redis`（Lettuce）。

### 8.3 耗时一致

- 查不到用户时，拿一个启动时生成的假哈希（参数和真实哈希相同）做一次校验，结果丢弃。这样「邮箱不存在」和「密码错」的耗时都是一次 Argon2，从响应时间上也区分不出来。
- 被封禁的账号同样先校验密码，再判断状态。
- 锁定时直接返回，不做哈希。锁按邮箱计、对没注册的邮箱一样，所以快速返回不会暴露邮箱是否存在。
- 假哈希的校验同样受第 7.5 节的并发上限约束。

### 8.4 已知代价

知道某个邮箱的人，故意连输 5 次错密码，就能让这个账号 15 分钟内登不上。本版接受：门户还没对公网开放，按 IP 限流、验证码都在 release spec 边界 G 之外。以后可以在网关上按 IP 限流，或者加验证码。

## 9. 封禁与解封

| 项 | 设计 |
|---|---|
| 接口 | `POST /admin/users/{userId}/ban`、`POST /admin/users/{userId}/unban`，没有请求体。写法同 GitLab 管理 API 的 `POST /users/:id/ban` |
| 成功 | `204` |
| 幂等 | 对已封禁的账号再封禁、对正常账号解封，都返回 204，数据不变 |
| 用户不存在 | 404 |
| 并发 | 靠 `version` 乐观锁，冲突时后到的返回 409 |
| Controller | 单独的 `AdminUserController`，和前台的 `AuthController` 分开；OpenAPI 分成 Auth、Admin 两个标签 |

领域行为：

- `User.ban(now)`：已经封禁时什么都不做，保留原来的封禁时间；否则状态改为 `BANNED`，记下封禁时间。
- `User.unban()`：已经正常时什么都不做；否则状态改回 `ACTIVE`，清掉封禁时间。

封禁的效果：被封禁的账号在邮箱和密码都对时返回 403（第 8.1 节）；解封后正常登录。失败限制和封禁互不影响。被封禁账号的邮箱不能再注册，返回 409。

本版不做：

- 不记封禁原因和操作人。本版没有后台账号，操作人无从记录；原因和操作人等后台上线时随审计一起加。
- 接口本身不做身份校验。`/admin/**` 由网关拒绝外部访问（PAP-65），只在内网直连时能调，和边界 F 一致。后台上线后加上「`STAFF` 会话 + 角色」的校验。

PAP-64 要在封禁时删掉这个用户的全部会话，并把对应的登录记录标成「因封禁结束」。封禁处理器留出这一步的位置。

## 10. 错误契约

### 10.1 identity 的错误码

前缀 `IDN`，后四位跟 HTTP 状态码走，由领域异常的语义特征映射得到。`detail` 是固定文案，不带邮箱和用户输入。

| 场景 | 异常 | 特征 | 状态 | 错误码 | 额外信息 | detail |
|---|---|---|---|---|---|---|
| 字段不合法 | `InvalidUserFieldsException` | `RULE_VIOLATION` | 422 | `IDN-0422` | `errors[]` | 请求参数不合法 |
| 邮箱已注册 | `EmailAlreadyRegisteredException` | `CONFLICT` | 409 | `IDN-0409` | — | 该邮箱已注册 |
| 邮箱或密码错误 | `InvalidCredentialsException` | `UNAUTHORIZED` | 401 | `IDN-0401` | — | 邮箱或密码错误 |
| 被暂时限制 | `LoginTemporarilyLockedException` | `QUOTA_EXCEEDED` | 429 | `IDN-0429` | `Retry-After`、`retryAfterSeconds` | 尝试次数过多，请稍后再试 |
| 账号已被封禁 | `UserBannedException` | `FORBIDDEN` | 403 | `IDN-0403` | — | 该账号已被封禁 |
| 用户不存在（后台） | `UserNotFoundException` | `NOT_FOUND` | 404 | `IDN-0404` | — | 用户不存在 |
| 哈希排队超时、Redis 不可用 | `TemporarilyUnavailableException` | `DEP_UNAVAILABLE` | 503 | `IDN-0503` | — | 服务暂时不可用 |

- 并发注册撞唯一约束：仓储转成 `EmailAlreadyRegisteredException`，和普通的「邮箱已注册」完全一样。
- Redis 不可用：`LoginThrottleAdapter` 捕获 Spring 的 `DataAccessResourceFailureException`（含 `RedisConnectionFailureException`）和 `QueryTimeoutException`，转成 `TemporarilyUnavailableException`。不转的话它们会落到 500，`detail` 里还会带出原始异常消息。其他 Redis 异常（比如脚本写错）是程序缺陷，照常返回 500。
- 简报附录 A.4 第 1 条：同一个接口里，每种失败的状态码都不同，前端只看状态码就能区分。

### 10.2 commons 改动 1：字段错误带原因码

- **commons-core**：新增 `FieldViolation(field, code, message)` 和接口 `HasFieldViolations`。领域异常实现这个接口，就能带上字段错误。
- **starter-web**：
  - `ValidationError` 增加 `code` 字段，变成 `(field, code, rejectedValue, message)`。
  - 统一错误格式里，只要异常实现了 `HasFieldViolations`，就输出 `errors[]`。这类错误的 `rejectedValue` 一律为空，不回显用户填的值。
  - Bean Validation 的错误也补上 `code`：取约束名转成大写下划线，比如 `NotBlank` 变成 `NOT_BLANK`。其他服务的 422 只是每项多一个字段，原有字段不变。
- identity 的字段名就是请求体的字段名：`email`、`password`。

```json
{
  "type": "…",
  "title": "IDN-0422",
  "status": 422,
  "detail": "请求参数不合法",
  "code": "IDN-0422",
  "traits": ["RULE_VIOLATION"],
  "path": "/auth/register",
  "timestamp": "…",
  "errors": [
    { "field": "password", "code": "TOO_COMMON", "rejectedValue": null, "message": "密码太常见" }
  ]
}
```

### 10.3 commons 改动 2：日志分级

- `GlobalRestExceptionHandler`：4xx 记 WARN，一行，包括错误码、状态、路径、异常类名和消息，不带堆栈；5xx 不变，记 ERROR 加堆栈。例外：错误解析引擎按类名关键字或原因链兜底分类出来的 4xx（解析策略为 `FALLBACK`、`CAUSE`，比如 `IllegalStateException` 被归为 422）可能是服务端缺陷，仍记 WARN，但保留堆栈。
- 参数校验失败（`MethodArgumentNotValidException`）单独处理。它的 `getMessage()` 带着每个字段的原始值（`rejected value [...]`），现有的掩码只认 `password=…` 和 `"password": "…"` 两种写法，拦不住。现状下这些原始值会进 ERROR 日志，也会原样出现在响应的 `detail` 里。改为：
  - 日志只记固定文案「参数校验失败」，加上每个错误的字段名和原因码；不记异常消息，不带堆栈。
  - 响应的 `detail` 用固定文案「请求参数不合法」，不再取异常消息。
- `LoggingCommandInterceptor` 的「命令失败」：`DomainException`（业务上的拒绝）记 WARN，其他异常记 ERROR。
- PAP-62 时为「4xx 不再按 ERROR 记日志」开过一个后台任务提示，由这里完成。

### 10.4 commons 改动 3：剩余等待时间

- **commons-core**：新增接口 `HasRetryAfter`，返回一个 `Duration`。
- **starter-web**：异常实现了它时，统一错误格式同时输出：
  - 响应头 `Retry-After`，单位秒，向上取整，最小为 1。
  - 响应体字段 `retryAfterSeconds`，值同上。
- 简报 §4.2：前端用秒数向上取整到分钟显示。

### 10.5 明文密码不出现在日志和响应里

1. `PlainPassword` 和请求对象的 `toString()` 都把密码遮掉。
2. 字段错误不回显用户填的值；Bean Validation 那条路径本来就按字段名掩码。
3. 所有 `detail` 都是固定文案。
4. 参数校验失败时，日志和 `detail` 都是固定文案，不带异常消息（第 10.3 节）。identity 自己不用 Bean Validation 校验这两个字段，但 commons 的这条路径要对所有服务安全。
5. CommandBus 的日志拦截器只记命令类名，不记内容（现状，确认过）。
6. 测试：
   - identity：集成测试走完注册、登录的全部失败路径后，断言日志输出和响应体里都找不到那个密码。
   - starter-web：用一个带 `@Valid`、含 `password` 字段的测试接口，走真实的 Bean Validation 失败路径，断言日志和响应里都没有那个值。

## 11. 服务骨架与配置

| 项 | 设计 |
|---|---|
| 启动类 | `PatraIdentityApplication`，`@SpringBootApplication(scanBasePackages = "dev.linqibin")`，没指定 profile 时默认 dev，和现有服务一样 |
| 端口 | 6400 |
| `application.yml` | 服务名 `patra-identity`；Nacos 注册块照抄现有服务；`server.forward-headers-strategy: framework`（同 catalog，让 OpenAPI 文档的地址指向网关）；Hikari、Flyway；错误码前缀 `IDN`；失败限制和哈希并发的配置项及默认值；actuator 暴露 `health,info,metrics` |
| `application-dev.yml` | 数据库 `jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_identity`，账号写法同其他服务的 dev 配置；Redis `redis://${PATRA_INFRA_HOST:127.0.0.1}:16379` |
| `application-container.yml` | 数据库和 Redis 地址都从环境变量读，变量名由 PAP-66 定稿 |
| OpenAPI | boot 依赖 starter-openapi，同 catalog |
| 可观测性 | boot 依赖 starter-observability |

在 PAP-66 建库之前，本地还连不上 mini 跑 identity；本 Issue 的测试全部用 Testcontainers，不受影响。

## 12. 账号类型改名

PAP-62 已经在本分支提交、还没推送，改动范围小：

- `patra-common-security`：`AccountType.PORTAL("portal")` 改为 `USER("user")`，请求头 `X-Patra-Account-Type` 的值随之变成 `user`。`STAFF` 等后台上线时再加。
- 安全 starter：测试和 `TestIdentity` 的默认值跟着改。
- 文档：PAP-62 工程设计和安全 starter README 里的「门户用户」改为「前台用户」。

这一步放在实现计划的最前面，后面的代码直接用新名字。

## 13. 测试策略

单元测试，不起 Spring：

- `EmailAddress`：规范化；`REQUIRED`、`TOO_LONG`、`INVALID_FORMAT` 的边界；一组和 Zod 默认邮箱正则对照的用例（接受和拒绝的都有）。
- `PlainPassword`：空值、孤立代理项、`toString()` 遮掉。
- `PasswordPolicy`：7、8、64、65 个码点的边界，含汉字和表情；常见名单命中（含大小写、首尾空白、全角）；原始长度够 8 位、规范化后变短的输入也能命中，比如 ` 123456 `。
- `User`：注册后的状态；封禁、解封各自的幂等。
- 四个处理器（端口用 mock）：
  - 注册：两个字段的错误一次报全；字段不合法时不查库、不哈希；已注册返回 409。
  - 登录：第 8.1 节的七步顺序；查不到用户时调用了假哈希校验；403 只在密码正确后出现；锁定时不查库、不哈希；达到上限的那次返回 429；中途出错按取消结算，原错误照常抛出。
  - 封禁、解封：用户不存在返回 404。
- commons：`HasFieldViolations` 输出 `errors[]`；Bean Validation 错误的 `code`；`HasRetryAfter` 输出响应头和字段；4xx 记 WARN 不带堆栈、5xx 记 ERROR 带堆栈；参数校验失败走真实的 Bean Validation 路径，日志和 `detail` 里都没有字段原始值；命令拦截器的日志级别。

集成测试：

- 仓储和 Flyway（PG17 容器）：保存和查询；邮箱唯一约束转成 `EmailAlreadyRegisteredException`；三个检查约束生效。
- 失败限制（Redis 容器）：
  - 阈值、窗口过期、锁过期。
  - 并发 20 个错误尝试：只有 5 次被放行进入校验，之后上锁。
  - 并发 6 个正确尝试：不上锁，至多有一次拿到 429。
  - 5 次取消之后，下一次正确尝试照常成功。
  - 先放行一次尝试，另一次成功结算之后它再按失败结算：失败计数是 1，不上锁。
  - 锁定期间的失败结算不计数；没结算的在途登记过期后，名额自动释放。
- 密码哈希：编码串以 `$argon2id$v=19$m=19456,t=2,p=1$` 开头；NFKC 前后等价的两个密码互相能校验通过；并发上限和排队超时。
- 两个 Controller 的切片测试，逐条核对第 10 节：
  - 422 的 `errors[]` 形状和原因码。
  - 「邮箱不存在」和「密码错」的响应体除 `timestamp` 外完全相同。
  - 429 带 `Retry-After` 和 `retryAfterSeconds`。
  - 401、403、404、409、204。
- 端到端（`@SpringBootTest`，PG 和 Redis 容器）：注册 → 登录 → 连续失败到锁定 → 封禁后 403 → 解封后成功；日志和响应里找不到明文密码；Redis 连不上时登录返回 503（测试把 Redis 指向没人监听的端口，不停共享的容器）。
- 测试 starter 新增 `RedisContainerInitializer`，镜像用和 mini 一致的 `redis:7.0.15`，补上 `ContainerType.REDIS` 一直没有的实现。PAP-64、PAP-65 也会用到。

回归：

- linqibin-commons 改动之后，现有各服务的测试全部通过。
- `./gradlew check integrationTest` 通过。`check` 不包含集成测试：`integrationTest` 测试套件只设了 `shouldRunAfter(test)`，没有挂到 `check` 上，所以两个任务都要写上。本 Issue 的端到端用例放在 boot 模块的 `integrationTest` 源集里，不用 `e2eTest`。
- `./gradlew dumpModuleGraph` 后模块图与构建一致；CD 的 `detect-changes.test.sh` 通过。

## 14. 实测结果

以下结论原本来自阅读源码或文档，实现时用测试逐个确认，结果如下（2026-10-06）。

| # | 结论 | 结果 | 对应测试 |
|---|---|---|---|
| 1 | `Argon2PasswordEncoder(16, 32, 1, 19456, 2)` 输出 Argon2id 编码串，单次哈希在开发机和 mini 上都在几十毫秒量级 | 开发机成立：编码串前缀是 `$argon2id$v=19$m=19456,t=2,p=1$`，单次约 20 毫秒（MacBook）。mini 上的耗时待 PAP-66 部署 identity 后补测 | `PasswordHashingAdapterTest`（mini 上的手测见第 16 节） |
| 2 | 后端照抄的正则和 Zod 4.6.5 默认的 `z.email()` 对同一组用例结论相同 | 成立：28 条用例的结论和 Zod 4.6.5 实际运行的结果一致 | `EmailAddress` 的单元测试 |
| 3 | Redis 连不上和命令超时时，Spring Data Redis 抛的是第 10.1 节列出的两类异常，适配器能把它们转成 `TemporarilyUnavailableException` | 连不上时成立：适配器转成 `TemporarilyUnavailableException`，登录返回 503 `IDN-0503`，注册不受影响。命令超时没有单独构造场景，仍按源码判断 | `LoginThrottleAdapterIT` 的 `should_translate_connection_failure`，加上 `RedisUnavailableIT`（Redis 指向没人监听的端口） |
| 4 | 「开始」和「结算」两段 Lua 脚本在 `redis:7.0.15` 上按第 8.2 节工作，脚本里用 `TIME` 取时间 | 成立：10 个交错场景全部通过（并发 20 个只放行 5 个、其余 1 秒的 429，正确密码不上锁，取消释放名额，迟到的失败只计 1 次，锁定期的失败不计，在途登记和计数窗口按时过期） | 失败限制的集成测试 `LoginThrottleAdapterIT` |
| 5 | `ValidationError` 的 `rejectedValue` 为空时在 JSON 里输出为 `null`，不影响前端解析 | 成立：`errors[]` 每项都有 `rejectedValue` 这个键，值为 `null` | Controller 切片测试 `AuthControllerIT` |

## 15. README

放在 `patra-api/patra-identity/` 下，写这几件事：

1. 服务做什么，不做什么（会话在 PAP-64）。
2. 模块和包结构。
3. 接口一览：前台两个、后台两个，以及网关上的访问规则。
4. 账号模型：前台用户和后台账号分表的理由，见本设计第 4 节。
5. 密码：规则、Argon2id 参数、常见名单的来源、版本和 SHA-256。
6. 登录失败限制：阈值、Redis 键、配置项。
7. 错误码表。
8. 本地运行和测试：dev 配置依赖 mini 上的库，测试用 Testcontainers。

## 16. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 注册和登录的成功响应加上会话令牌；登录处理器返回已校验的用户，供建会话使用 | PAP-64 |
| 封禁处理器在留出的位置删掉该用户的全部会话，并结束对应的登录记录 | PAP-64 |
| 会话相关的 Redis 键带账号类型，比如 `idn:session:user:…` | PAP-64 |
| 建议路径：`POST /auth/logout`、`GET /auth/me` | PAP-64 |
| identity 接入安全 starter；dev 配置里内部令牌的给法 | PAP-64 |
| 会话契约如果放在 identity，建 `patra-identity-api` 模块 | PAP-64 |
| 路由 `/patra-identity/**`；`/auth/register`、`/auth/login`、`/auth/logout` 公开，`/auth/me` 需要登录 | PAP-65 |
| 拒绝外部访问 `/*/admin/**`，和 `/*/_internal/**`、actuator 一样处理：匿名和已登录得到同一个结果 | PAP-65 |
| 建库 `patra_identity`；compose 服务（端口 6400）；数据库和 Redis（带密码）的环境变量；`services.json` 加 identity；网关 OpenAPI 聚合加 identity；合并前 `services.json` 必须有 identity 条目（`ALL_UNITS` 已含 identity，没有条目时 main 上的 CD 会被 `deploy.sh` 以未知服务拒绝）；部署后在 mini 上测一次单次哈希耗时，写回第 14 节第 1 条 | PAP-66 |
| 前端校验：邮箱去掉首尾空格后用 Zod 默认 `z.email()` 加 `.max(254)`；密码长度按码点（`[...password].length`） | PAP-67、PAP-68 |
| 前端按原因码选文案：`REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`TOO_SHORT`、`TOO_COMMON`、`INVALID_CHARACTER`；不认识的原因码显示该字段的通用错误 | PAP-67、PAP-68 |
| 429 用 `retryAfterSeconds`；`userId` 是字符串 | PAP-67、PAP-68 |

## 17. 要同步改的文档和 Issue

本设计评审通过后一起改：

- release spec：
  - 决策 B、C、D 里的「门户用户」改为「前台用户」，账号类型写作 `USER`、`STAFF`。
  - 决策 D 和边界 F：封禁、解封是后台接口，放在 `/admin/` 下。
  - Done 判定 D6：外部经网关访问内部接口和后台接口（`/_internal/`、`/admin/`）都被拒绝。
  - Issue 表里 PAP-63 的「（内部接口）」改为「（后台接口）」。
- Linear：
  - PAP-63：验收标准里的 `/_internal/` 改为 `/admin/`；To-Do 里的「六个模块」改为五个，注明 `-api` 等有内容再建。
  - PAP-64、PAP-65：「门户用户」改为「前台用户」；PAP-65 的规则加上拒绝 `/admin/**`。
- PAP-62 工程设计和安全 starter README：随第 12 节的改名一起改。
