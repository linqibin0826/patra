# patra-identity

前台用户的账号服务：注册、登录、会话、登出、「当前用户」、用户名与公开身份卡、登录失败限制、封禁与解封。

工程设计：`docs/patra/specs/2026-10-05-identity-account-design.md`（账号）、`docs/patra/specs/2026-10-09-identity-session-design.md`（会话）、`docs/patra/specs/2026-10-20-identity-account-rebuild-design.md`（账号重建：用户名、昵称、邮箱验证状态、四态状态机、`V1` 最终形态）。

## 1. 模块

| 模块 | 内容 |
|---|---|
| `patra-identity-domain` | `User`、`UserProfile`、`FormerUsername`、`UserPasswordCredential`、`UserLoginRecord` 五个聚合，邮箱、用户名、昵称、密码、客户端信息值对象，用户名 / 昵称 / 改名 / 密码 / 失败限制规则、会话策略，端口，`SessionIssuer` 领域服务，领域异常 |
| `patra-identity-app` | 注册、登录、登出、改用户名、封禁、解封六个处理器，走 CommandBus；「当前用户」和公开身份卡走 `UserQueryService` |
| `patra-identity-infra` | JPA 持久化、Argon2id 哈希、常见密码名单、Redis 失败限制、会话存储适配器、Flyway 脚本（一份最终形态的 `V1`） |
| `patra-identity-adapter` | 前台的 `AuthController`、`MeController`、`PublicUserController`，后台的 `AdminUserController` |
| `patra-identity-boot` | 启动类、配置、安全 starter 接入 |
| `patra-identity-session` | 与网关共用的会话存储契约（令牌、Redis 键、Lua、`RedisSessionStore`），见它自己的 README |

## 2. 接口

| 路径 | 成功 | 网关规则（PAP-65 / PAP-93） |
|---|---|---|
| `POST /auth/register` | 201 `{ sessionToken, user }` | 公开 |
| `POST /auth/login` | 200 `{ sessionToken, user }` | 公开 |
| `POST /auth/logout` | 204，有当前用户就删会话，没有也 204 | 公开 |
| `GET /auth/me` | 200 `user`（下面的结构） | 需要登录 |
| `PATCH /me/username` | 200 `{ username, nextUsernameChangeAt }` | 需要登录（`/me/**` 整体） |
| `GET /users/{username}` | 200 `{ username, displayName, avatarUrl, audience }`；保留期内的旧名 301，`Location: /users/<新名>`，体 `{ username: <新名> }` | 公开（`/users/**` 整体，网关照样翻译令牌） |
| `POST /admin/users/{userId}/ban` | 204 | 拒绝外部访问 |
| `POST /admin/users/{userId}/unban` | 204 | 拒绝外部访问 |

注册请求体 `email`、`username`、`password`，登录请求体 `email`、`password`，两者都可带 `clientType`（不传按 `web`）和 `deviceId`（最长 128 个字符）。

登录、注册响应里的 `user` 与 `/auth/me` 同构：

```json
{
  "userId": "352303128713027974", "email": "chen.yu@example.com", "emailVerified": false,
  "username": "chen-yu", "displayName": "chen-yu", "avatarUrl": null,
  "status": "ACTIVE", "deletionDueAt": null, "nextUsernameChangeAt": null, "accountType": "user"
}
```

- `displayName` 永远非空：资料表里没设昵称就回退成当前用户名。`nextUsernameChangeAt` 是最近一次改名时间加冷却，没改过名为 `null`。`avatarUrl` 本版恒为 `null`（PAP-97 填）。
- `status` 只会出现 `ACTIVE`、`PENDING_DELETION`。登录在密码正确之后分流：`ACTIVE`、`PENDING_DELETION` 都建会话（后者 `deletionDueAt` 有值，门户据此进取消注销页）；`BANNED` 403；`DELETED` 与密码错误完全一样，按失败结算、401。`/auth/me` 对已封禁、已注销用户的会话返回 401。
- 公开身份卡：路径里的用户名先去空白、转小写，语法不合法直接 404。可见的条件是 `audience` 为 `PUBLIC`（`ACTIVE` 且邮箱已验证）或请求者就是本人（`SELF`，只要还能登录）；不可见一律 404，不区分原因。门户请求时带上会话令牌，本人才能看到自己。301 的 `Location` 是 identity 自己的相对路径，门户按体里的 `username` 做临时跳转（307，永久跳转会被浏览器缓存、原主改回旧名后成环），`fetch` 要用 `redirect: "manual"`；301 带 `Cache-Control: no-store`。
- `/admin/**` 本版没有身份校验，只能在内网直连调用；对 `DELETED` 用户封禁、解封都返回 409。

## 3. 账号模型

账号分前台用户（`USER`）和后台账号（`STAFF`，以后建）两类，分开建表；密码和用户也分开，各一张表。理由和调研见账号工程设计第 4 节。

| 表 | 内容 |
|---|---|
| `idn_user` | ID、邮箱（唯一、只能小写，`DELETED` 时为空）、`email_verified_at`、用户名（唯一）、最近改名时间、状态、封禁时间、注销到期时间、注销时间。用户名格式、状态与各时间列的配对都由检查约束守在表上 |
| `idn_user_password_credential` | 用户 ID（唯一）、Argon2id 编码串 |
| `idn_user_profile` | 用户 ID（唯一）、昵称（可空，空表示跟随用户名）。PAP-95 在这份骨架上加列 |
| `idn_user_former_username` | 用户 ID、旧用户名、替换时间、保留到期时间 |
| `idn_user_login_record` | 会话 ID、用户 ID、客户端信息、结束原因（第 9 节） |

### 状态机

| 方法 | `ACTIVE` | `BANNED` | `PENDING_DELETION` | `DELETED` |
|---|---|---|---|---|
| `ban(now)` | → `BANNED` | 不变 | → `BANNED`，清注销到期时间 | 409 |
| `unban()` | 不变 | → `ACTIVE` | 不变 | 409 |
| `requestDeletion(now, coolingPeriod)` | → `PENDING_DELETION` | 409 | 不变 | 409 |
| `cancelDeletion()` | 不变 | 409 | → `ACTIVE` | 409 |
| `completeDeletion(now)` | 409 | → `DELETED` | → `DELETED` | 不变（幂等） |
| `markEmailVerified(now)` | 记第一次 | 记第一次 | 记第一次 | 409 |
| `changeUsername(...)` | 见第 4 节 | 401 | 409（`traits` 含 `PENDING_DELETION`） | 401 |

表里的 409 是 `InvalidUserStateTransitionException`（「账号当前状态不允许此操作」）。进入 `DELETED` 时邮箱、邮箱验证时间、注销到期时间、封禁时间全部清空，用户名和 ID 留着，不能回退。`/me/**` 的写接口先 `requireActive()`：`PENDING_DELETION` 409，`BANNED`、`DELETED` 401。注销冷静期、到期执行是 PAP-100；到期时资料行置空不删行（读模型要求资料行与用户一对一存在，缺行是 500）。

### 用户名规则

输入去首尾空白、转小写后依次查，只报第一条：为空 `REQUIRED`；含 `[a-z0-9_-]` 之外的字符 `INVALID_CHARACTER`；少于 3 个字符 `TOO_SHORT`；多于 30 个 `TOO_LONG`；首尾不是字母数字，或 `_` / `-` 连续 `INVALID_FORMAT`；纯数字 `DIGITS_ONLY`；命中保留字 `RESERVED`。

保留字在 `domain/policy/ReservedUsernames`，三组：路由段、角色词、站名与数据源名。匹配前做同形归一（`0→o`、`1→i`、`3→e`、`5→s`）并去掉 `_` 和 `-`，整词比较：`adm1n`、`a_d_m_i_n` 都命中 `admin`，`admin2` 不命中。前端用同一条正则 `^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$`，保留字只在后端判。

### 昵称规则

本版只有规则和单测（`DisplayNamePolicy`），改昵称的接口是 PAP-95。规范化：NFC、连续空格类字符折叠成一个空格、再去首尾空白。校验依次：为空 `REQUIRED`；控制字符、行段分隔符、私用区、未分配码点、孤立代理项、格式字符（emoji 组合里的 ZWJ 和标签字符除外）、以组合用字符开头的字素簇 `INVALID_CHARACTER`；少于 2 个字素 `TOO_SHORT`；多于 30 个字素或超过 120 字节 `TOO_LONG`；网址、邮箱、手机号、微信号形态 `INVALID_FORMAT`；拒绝词 `RESERVED`（`domain/policy/RejectedDisplayNameWords`：中文词包含即命中、拉丁词整词命中，站名与数据源名复用保留字第三组）。字素按 `BreakIterator` 数，汉字、带变音符的字母、肤色 / ZWJ 组合的 emoji、国旗各计 1。

资料表里不存默认昵称：空就跟随用户名，改名后没设过昵称的人昵称自动跟着变。

## 4. 用户名

- 改名 `PATCH /me/username`：校验新名 → 加行锁读用户 → 状态守卫、与当前相同 422 `UNCHANGED`、冷却期内 429 → 查占用（`idn_user` 里有人用，或别人的旧名未到期）409 → 保存，旧名写进 `idn_user_former_username`。不做再认证，不撤销会话，不碰资料表。
- 冷却：两次改名间隔不少于 30 天，改回旧名也算一次；从未改过名的第一次没有冷却。
- 保留：旧名保留 90 天，期间别人不能注册或改成它，原主可以改回；`GET /users/<旧名>` 301 到新名。到期自动失效，不用任务清理（查询按 `reserved_until` 过滤）。
- 配置：

```yaml
patra:
  identity:
    username:
      change-cooldown: 30d       # 两次改名的最短间隔
      reservation-period: 90d    # 旧名保留多久
```

非正数启动失败。mini 上验收时不想等 30 天，可以在 `patra-identity.yaml` 临时改短。

## 5. 密码

- 规则：8 到 64 个码点，不去空格，不能在常见密码名单里。登录只要求非空、不含孤立代理项；超过 1024 个码点的密码直接按密码错误处理，不做哈希。
- 哈希：Argon2id，`m=19456 KiB, t=2, p=1`，盐 16 字节、输出 32 字节；哈希和校验前都做 NFKC。开发机上单次约 20 毫秒（mini 上的耗时等 PAP-66 部署后实测）。
- 并发：同时进行的哈希计算最多 4 个，排队超过 3 秒返回 503。
- 常见密码名单：`infra/src/main/resources/password/common-passwords.txt.gz`，取自 Django 6.0 的 `django/contrib/auth/common-passwords.txt.gz`（Royce Williams 整理，19640 行），SHA-256 `3c1baed62596de36860824eb3f436d5932d37ca8b06e59df78f5a44ec175afe4`。许可证见同目录的 `common-passwords.LICENSE`（BSD 3-Clause）。比较时对密码做 NFKC、转小写、去掉首尾空白。

## 6. 登录失败限制

- 按「账号类型 + 邮箱」计数，没注册过的邮箱也一样。15 分钟内失败 5 次锁 15 分钟，成功一次清零。
- 只有确认的失败才计数；正在校验的尝试单独登记，「失败数 + 在途数」到上限时返回 429（1 秒），不上锁。
- Redis 键：`idn:login-failures:user:{邮箱 SHA-256}`、`idn:login-inflight:…`、`idn:login-lock:…`，脚本在 `infra/src/main/resources/redis/`。
- 配置：

```yaml
patra:
  identity:
    login-throttle:
      max-failures: 5
      window: 15m
      lock-duration: 15m
      in-flight-ttl: 30s
    password-hashing:
      max-concurrent: 4
      wait-timeout: 3s
```

## 7. 错误码

| 场景 | 状态 | 错误码 |
|---|---|---|
| 字段不合法（`errors[]` 带字段名和原因码） | 422 | `IDN-0422` |
| 邮箱已注册、用户名已被使用（`errors[]`：`email` / `ALREADY_REGISTERED`、`username` / `ALREADY_TAKEN`，注册时可同时两条；含别人的旧名未到期、并发撞唯一约束；文案「邮箱或用户名已被使用」） | 409 | `IDN-0409` |
| 账号注销中不能做写操作（`traits` 为 `CONFLICT` + `PENDING_DELETION`，文案「账号正在注销中，取消注销后再操作」） | 409 | `IDN-0409` |
| 状态不允许的迁移（后台对 `DELETED` 用户封禁 / 解封，文案「账号当前状态不允许此操作」） | 409 | `IDN-0409` |
| 封禁、解封撞上乐观锁（文案「用户正被其他操作修改，请重试」） | 409 | `IDN-0409` |
| 邮箱或密码错误（含已注销账号） | 401 | `IDN-0401` |
| 需要登录：`/auth/me`、`/me/**` 没有当前用户、用户不存在、已封禁、已注销（文案 `Authentication required`） | 401 | `IDN-0401` |
| 登录被暂时限制（`Retry-After`、`retryAfterSeconds`） | 429 | `IDN-0429` |
| 改名冷却期内（`Retry-After`、`retryAfterSeconds` 是剩余秒数，文案「用户名修改过于频繁，请稍后再试」） | 429 | `IDN-0429` |
| 账号已被封禁 | 403 | `IDN-0403` |
| 用户不存在：后台接口；公开身份卡不存在或对请求者不可见 | 404 | `IDN-0404` |
| Redis 不可用（登录限流、会话存储）、哈希排队超时 | 503 | `IDN-0503` |

请求本身格式不对时由 Spring 直接处理，返回默认格式的 ProblemDetail，没有 `code`：请求体不是 JSON 对象（`[]`、`"x"`、空请求体、坏 JSON）或字段类型不对返回 400；`Content-Type` 不是 JSON 返回 415；方法不对（比如 GET）返回 405；路径里的 `userId` 不是数字或溢出返回 400，负数和 0 按用户不存在返回 404 `IDN-0404`；`/users/{username}` 路径里带 `%20`、`%25` 等百分号编码时被 Spring Security 的请求防火墙拒绝，返回 400。

原因码：邮箱 `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`ALREADY_REGISTERED`（409）；用户名 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`DIGITS_ONLY`、`RESERVED`、`UNCHANGED`（改名）、`ALREADY_TAKEN`（409）；昵称 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`INVALID_FORMAT`、`RESERVED`（PAP-95 的接口才会返回）；密码 `REQUIRED`、`INVALID_CHARACTER`、`TOO_SHORT`、`TOO_LONG`、`TOO_COMMON`；客户端类型 `INVALID_FORMAT`；设备标识 `TOO_LONG`、`INVALID_FORMAT`（含控制字符）。

## 8. 本地运行和测试

- 配置从 mini 的 Nacos 读（`patra-common.yaml`、`patra-identity.yaml`，源文件在 `patra-infra/nacos/dev/`）：连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`），和 mini 上的容器共用同一个 Redis 0 号库。Redis 密码在 Nacos 里写成 `${REDIS_PASSWORD}`，本机由 Nacos starter 从 `~/.patra/secrets/redis.env` 读；生成和复制见 `patra-infra/docker/README.md`「密钥」。
- 库结构只有一份 `V1`（`infra/src/main/resources/db/migration/`）。库还是旧结构时启动会因 Flyway 校验失败起不来，推倒重建再启动；开发数据可丢弃，不做 `pg_dump`：

```bash
docker exec patra-postgres psql -U postgres -c 'DROP DATABASE IF EXISTS patra_identity WITH (FORCE);' -c 'CREATE DATABASE patra_identity;'
```

mini 上先 `docker stop patra-identity`，重建后 `docker start patra-identity`。

- 测试全部用 Testcontainers（PostgreSQL 17、Redis 7.0.15），本机要有 Docker：

```bash
./gradlew spotlessApply && ./gradlew check && ./gradlew :patra-api:patra-identity:patra-identity-infra:integrationTest :patra-api:patra-identity:patra-identity-adapter:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest
```

`check` 不包含集成测试，按模块各自跑。

identity 从网关签的身份断言取当前用户，要配网关的公钥：写在 Nacos 公共配置 `patra-common.yaml` 里，和网关共用一份，换密钥时改这一处。集成测试不读这些：安全 starter 的测试支持会自动注入测试公钥。

容器和本机读同一份 Nacos 配置，只差 `PATRA_INFRA_HOST` 等本地变量；容器里的 `REDIS_PASSWORD` 来自 compose 加载的 `~/.patra/secrets/redis.env`。

## 9. 会话

登录、注册成功后签发不透明令牌，会话写进 Redis，登录记录写进 `idn_user_login_record`（ID 就是会话 ID）。
令牌、键和脚本见 `patra-identity-session/README.md`。

策略只在这里配，网关不配：

```yaml
patra:
  identity:
    session:
      max-sessions-per-user: 10      # 超过挤掉最老的，记录标 REPLACED
      lifetime:
        user:
          web:
            idle: 30d                # 不活跃过期；网关每个请求续期，60 秒内只读不写
            absolute: 180d           # 绝对过期
```

缺 `user.web` 这一行、键不认识、`idle` 长于 `absolute`，应用启动失败。

登出删会话并把记录标成 `LOGOUT`；封禁删该用户全部会话并把记录标成 `BANNED`。会话过期不回写记录：
记录里 `ended_at` 为空而 Redis 里没有会话，就是过期了。

登录建会话前在事务里对用户行加锁重读、复核封禁（`UserRepository.findByIdForUpdate`），封禁与登录并发时
要么登录返回 403，要么封禁把这条新会话一起删掉；被封用户拿不到有效会话。
