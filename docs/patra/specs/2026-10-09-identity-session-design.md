# identity 会话工程设计（PAP-64）

> **Issue**：[PAP-64](https://linear.app/papertrace/issue/PAP-64)
> **版本**：[v0.8 Accounts](../release-specs/v0.8-accounts.md)
> **前置设计**：[identity 账号（PAP-63）](2026-10-05-identity-account-design.md)、[安全 starter（PAP-62 / PAP-70）](2026-10-05-security-starter-design.md)
> **日期**：2026-10-09
> **状态**：待评审

## 1. 要解决的问题

PAP-63 把登录做到「凭据校验通过」。本 Issue 把它做成真正的登录：校验通过后签发会话令牌、把会话写进 Redis、在 PostgreSQL 记一条登录记录；注册成功后同样建会话；提供登出和「当前用户」接口；封禁时清掉该用户的全部会话。网关（PAP-65）每个请求要按令牌查会话并续期，所以本 Issue 还要定下两边共用的存储契约，并把它做成一个两边都能引的模块。

设计过程中定下的、影响别的 Issue 的事：

- 会话存储契约放在独立的小模块 `patra-identity-session` 里，只给 identity 和网关引；下游服务仍然只认断言，不碰 Redis（第 5 节）。
- 会话令牌带账号类型前缀 `patra_user_`，是对决策 B「不带任何信息」的一处有意偏离（第 6.1 节）。
- 登录记录不存令牌的哈希；Linear 上的验收标准措辞随之调整（第 17 节）。

**完成标准**：PAP-64 的 Acceptance Criteria，其中「Redis 和数据库里都只有令牌的哈希」改为「数据库里不存令牌，Redis 里只存令牌的哈希」。

## 2. 范围

做：

- 会话模块：令牌的生成、解析、哈希；Redis 键与四段 Lua；`RedisSessionStore`；Redis 暂时不可用的统一判定。
- identity：登录记录聚合与表；登录、注册建会话；登出；封禁清会话；`GET /auth/me`；接入安全 starter；乐观锁冲突的固定文案；登录限流适配器改用统一的不可用判定。
- 文档：会话模块的 README（给 PAP-65 的契约）、identity README 的会话一节。

本版不做：

- 踢人下线、查看登录设备的接口；记录 User-Agent。它们是后台功能，等后台账号上线时随后台一起做；键 2 和登录记录已经为它们留好了数据。
- 会话因过期而结束时回写登录记录。Redis 过期不通知 identity，决策 I 已否决键空间通知。
- Redis Cluster。会话键在脚本里拼出来，只支持单机 Redis（第 6.5 节）。
- 后台账号（`STAFF`）的会话。模型按账号类型分键，到时加一行配置和一个前缀。
- 网关侧的查会话、续期、签断言（PAP-65）；建库、Redis 密码、密钥注入（PAP-66）；门户的 Cookie（PAP-67）。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 会话令牌是不透明随机串，各端用 `Authorization` 头携带；会话在 Redis，只存令牌哈希；30 天不活跃过期，网关每个请求续期；会话带账号类型、客户端类型、设备标识（可空）、创建时间、最后活跃时间、绝对过期时间；登录记录在 identity 的 PostgreSQL，不在请求路径上 | release spec 决策 B |
| 网关查会话后签 60 秒的身份断言，`sub` 是用户 ID、`sid` 是会话 ID；identity 的需登录接口从断言取当前用户，不自己查会话；登出在网关上是公开路由 | release spec 决策 C，安全 starter 设计第 6、17 节 |
| 账号类型 `USER` / `STAFF`，本版只有 `USER`；会话相关的 Redis 键带账号类型 | release spec 决策 D，PAP-63 设计第 16 节 |
| 门户只持有 HttpOnly Cookie，不做续期 | release spec 决策 E |
| Redis 密码和签名密钥不进仓库，公钥随下游配置给出 | release spec 决策 G |
| 网关是 servlet 应用，查会话是同步的 Redis 调用 | release spec 决策 H |
| 存储契约放独立模块、只给 identity 和网关引（三个备选里选 A） | 本设计的头脑风暴，2026-10-09 |
| Handler 之间不互相调用；读操作不经 CommandBus | `.claude/rules/tech/commandbus.md` |
| 时间来自注入的 `Clock`；Long 型 ID 在 JSON 里是字符串 | 测试约定、starter-core |

## 4. 业界参照

2026-10-08 查了约 20 个系统的公开文档和源码，做法分三类：

| 做法 | 代表 | 代价 |
|---|---|---|
| 签发方独占存储，网关每个请求调它的接口（introspection / whoami / forward-auth） | Curity Phantom Token、Keycloak、Ory Oathkeeper + Kratos、Authelia、Spring Security `opaqueToken()` | 每个请求多一跳，靠网关缓存压，撤销窗口等于缓存 TTL。决策 C 否决 |
| 网关自己持有会话、签断言给下游，认证方不碰存储 | Pomerium、oauth2-proxy、Cloudflare Access、Google IAP、AWS ALB、Netflix Passport | 认证方主动撤销做不好：oauth2-proxy 的 back-channel logout 至今未合并，Pomerium 靠刷新失败才删 |
| 共享库加共享 Redis，谁要谁读，契约就是那个 jar | Spring Session、Sa-Token、RuoYi-Cloud、Pig、JeecgBoot | 每个读会话的服务都要 Redis 凭据并耦合格式。决策 C 否决了下游各自查 Redis |

没有一家公开会话存储的键名格式，公开的只有下游断言的格式与验证方法。Patra 的做法是第三类的机制（网关直读 Redis，零跳数）加第二类的边界（下游只认断言）：契约是一个 jar，但只有两个消费者。

借来的细节：Spring Session 的 indexed 仓库用按用户的索引键指向该用户的全部会话（键 2）；RuoYi-Cloud 只在剩余有效期不足时才写回（续期节流）；Sa-Token 踢人时打标记而不是删键，区分「被踢」和「过期」，本版没有踢人，留给后台。

来源：[Curity phantom token](https://curity.io/resources/learn/phantom-token-pattern/)、[Oathkeeper cookie_session 加缓存的 PR](https://github.com/ory/oathkeeper/pull/1244)、[Pomerium 下游 JWT](https://www.pomerium.com/docs/capabilities/getting-users-identity)、[Spring Session indexed 键布局](https://docs.spring.io/spring-session/reference/api/java/org/springframework/session/data/redis/RedisIndexedSessionRepository.html)、[RuoYi-Cloud 网关 AuthFilter](https://github.com/yangzongzhuan/RuoYi-Cloud/blob/master/ruoyi-gateway/src/main/java/com/ruoyi/gateway/filter/AuthFilter.java)、[Sa-Token 踢人下线](https://github.com/dromara/Sa-Token/blob/v1.46.0/sa-token-doc/use/kick.md)。

## 5. 模块

新增一个模块，改五个：

| 模块 | 改动 |
|---|---|
| `patra-api/patra-identity/patra-identity-session`（新） | 纯库模块，插件同 `patra-common-security`（`linqibin.module-patra` + `linqibin.java-library`）。依赖 `patra-common-security`（`AccountType`、`ClientType`、`CurrentUser`）、`linqibin-commons-core`（`DomainException`）、`spring-boot-starter-data-redis`。内容：令牌、键、四段 Lua、`RedisSessionStore`、不可用判定。没有自动配置，消费方自己声明 Bean |
| `patra-identity-domain` | 聚合 `UserLoginRecord`、值对象、端口 `UserLoginRecordRepository` / `SessionStorePort` / `UserReadPort`、领域服务 `SessionIssuer`、异常 `UserModifiedConcurrentlyException` |
| `patra-identity-app` | `AuthenticateUser*` 改名 `LoginUser*` 并建会话；`RegisterUserHandler` 建会话；新增 `LogoutUserHandler`、`UserQueryService`；`BanUserHandler` 清会话 |
| `patra-identity-infra` | 依赖 `patra-identity-session`；`SessionStoreAdapter`、`UserLoginRecordRepositoryAdapter`、`UserReadAdapter`、Flyway `V2`；`LoginThrottleAdapter` 改用模块的不可用判定；`UserRepositoryAdapter` 转乐观锁异常 |
| `patra-identity-adapter` | 请求体加字段；响应改名；`/auth/logout`、`/auth/me` |
| `patra-identity-boot` | 依赖 `patra-spring-boot-starter-security`；配置项与 Bean；README |

依赖方向：`identity-infra → identity-session ← gateway-boot`（PAP-65）。domain 不依赖会话模块：`hexagonal-domain` 插件禁止框架依赖，而会话模块带着 Spring Data Redis。

构建接入：`settings.gradle.kts` 加一条 `includeAt`；`./gradlew dumpModuleGraph` 重新生成 `module-graph.json`，新模块在 `patra-api/patra-identity/` 下，归 identity 单元；CI 的 `ALL_UNITS` 已含 identity，不用改。

## 6. 令牌与存储

### 6.1 令牌

```
patra_user_<43 个 base64url 字符>
```

- 32 字节 `SecureRandom`，base64url 不带填充，43 个字符；总长 54。
- 前缀按账号类型：`USER` → `patra_user_`，`STAFF` 以后是 `patra_staff_`。映射只写在 `SessionToken` 一处。
- 解析严格：正则 `^patra_user_[A-Za-z0-9_-]{43}$`，不匹配的不哈希、不查 Redis，网关按匿名处理。
- 哈希：整个令牌字符串的 UTF-8 字节做 SHA-256，小写十六进制 64 个字符。不加盐、不用 HMAC：令牌有 256 位随机，拿到 Redis 快照也反推不出令牌；HMAC 要多分发一个密钥给两边。做法同登录限流键对邮箱的处理。
- `toString()` 输出 `SessionToken[patra_user_***]`。

前缀是对决策 B「不带任何信息」的有意偏离：键里带账号类型是 PAP-63 定下的约束，网关拿到令牌得知道去哪个键空间查，前缀是最直接的办法；顺带让泄露到日志或仓库里的令牌能被一眼认出、能被密钥扫描工具抓到（GitHub 的 `ghp_` 是同一思路）。前缀只说明「这是 Patra 的前台用户令牌」，不含用户信息。替代做法是去掉键里的账号类型、只查一个键空间，但那要推翻 PAP-63 的约束。

### 6.2 Redis 键

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `idn:session:user:<哈希>`（键 1） | HASH | 一条会话，字段见下 | `min(idle_timeout, expires_at − now)`，续期时重算 |
| `idn:user-sessions:user:<userId>`（键 2） | HASH | `session_id → <哈希>`，该用户的全部会话 | 不小于该用户最新一条会话的绝对有效期，每次登录刷新 |

键 1 的字段，值都是字符串：

| 字段 | 值 |
|---|---|
| `user_id` | 十进制 |
| `session_id` | 十进制，等于登录记录的 ID |
| `account_type` | `AccountType.code`，本版 `user` |
| `client_type` | `ClientType.code`，本版 `web` |
| `device_id` | 可选，没有就不写这个字段 |
| `created_at` | epoch 毫秒 |
| `last_active_at` | epoch 毫秒，最多落后真实活跃时间 60 秒 |
| `expires_at` | epoch 毫秒，绝对过期 |
| `idle_timeout_ms` | 毫秒，网关续期只照它做 |

三条查找路径：网关按令牌哈希查键 1；登出时 identity 手里是断言里的 `sub` 和 `sid`，用键 2 找到哈希删键 1；封禁按用户 ID 遍历键 2 删全部。会话 ID 用登录记录的雪花 ID，一个 ID 两处用；雪花 ID 按时间递增，键 2 里最小的字段就是最老的会话。

### 6.3 有效期策略

策略只在 identity 配置；网关不配任何有效期，续期照会话里的 `idle_timeout_ms` 和 `expires_at` 做。

```yaml
patra:
  identity:
    session:
      max-sessions-per-user: 10
      lifetime:
        user:
          web:
            idle: 30d
            absolute: 180d
```

- `lifetime` 按「账号类型 × 客户端类型」，键是两个枚举的 `code`，按字符串绑定、启动时解析：不认识的键、缺本服务要签发的那一行（本版 `user.web`）、`idle` 或 `absolute` 不是正数、`idle` 大于 `absolute`，都启动失败。
- 30 天不活跃是 Done 判定 D3 定的；180 天绝对过期兜住被偷的 Cookie 和换过的密码。
- `max-sessions-per-user` 对所有账号类型一个数，超过就挤掉最老的一条，被挤掉的登录记录标成 `REPLACED`。
- 续期间隔 60 秒是会话模块的常量 `RedisSessionStore.RENEW_INTERVAL`，不配置。
- `IdentityProperties` 加 `Session(int maxSessionsPerUser, Map<String, Map<String, Lifetime>> lifetime)`，`IdentityConfiguration` 把它变成领域的 `SessionLifetimePolicy`，和 `LoginThrottlePolicy` 的做法一样。

### 6.4 脚本

四段 Lua，各自原子，放在会话模块的 `redis/session-*.lua`。时间一律由 Java 侧按 `Clock` 传进 `ARGV`，脚本不调 `TIME`，测试能用固定时钟。

| 脚本 | KEYS | 做什么 | 返回 |
|---|---|---|---|
| `session-create` | 键 2、新会话的键 1 | 1. 遍历键 2，`EXISTS` 为 0 的悬空条目 `HDEL`；2. 条数达到上限时按会话 ID 从小到大挤掉多出来的：`DEL` 它们的键 1、`HDEL` 键 2；3. `HSET` 新会话全部字段，`PEXPIRE min(idle, expires_at − now)`；4. `HSET` 键 2，`PTTL` 小于 `expires_at − now` 时 `PEXPIRE` 到它 | 被挤掉的会话 ID 列表 |
| `session-touch` | 键 1 | `HGETALL`；空返回空；`now ≥ expires_at` 则 `DEL` 并返回空；`now − last_active_at ≥ 60000` 才 `HSET last_active_at` 并 `PEXPIRE min(idle, expires_at − now)` | 全部字段，扁平数组 |
| `session-delete` | 键 2 | `HGET` 会话 ID 得到哈希；`DEL` 键 1；`HDEL` 键 2 | 键 1 真正删掉了返回 1，否则 0 |
| `session-delete-all` | 键 2 | 遍历键 2，逐条 `DEL` 键 1；`DEL` 键 2 | 键 1 真正删掉了的会话 ID 列表 |

- 会话 ID 的大小比较按「先比字符串长度，再比字典序」：雪花 ID 现在 18 位、2031 年前后变 19 位，转成 Lua 的 double 会丢低位精度。
- 被挤掉的和要删的键 1 在脚本里由前缀和哈希拼出来，所以 `KEYS` 只声明已知的键。
- `HGETALL` 的结果经 `StringRedisTemplate` 回来是字符串列表，Java 侧两两成对装进 `StoredSession`。

### 6.5 限制

- 只支持单机 Redis：脚本在运行时拼键，Redis Cluster 要求所有键事先声明。dev 和 mini 都是单机。
- 键 2 比键 1 活得久，但不防 `maxmemory` 淘汰：默认 `noeviction`，不改。
- 键 2 的悬空条目只在该用户下一次登录时清理，封禁时整键删除；过期会话的条目最多占几十字节。

## 7. 会话模块的接口

包 `dev.linqibin.patra.identity.session`。

| 类型 | 内容 |
|---|---|
| `SessionToken` | 记录，只有 `value`。`generate(AccountType, SecureRandom)`；`parse(String) → Optional<SessionToken>`；`accountType()` 按前缀；`hash()` 十六进制；`toString()` 遮掩 |
| `NewSession` | `@Builder` 记录：`userId`、`sessionId`、`accountType`、`clientType`、`deviceId`（可空）、`idleTimeout`、`absoluteLifetime`、`maxSessionsPerUser` |
| `IssuedSession` | `of(SessionToken token, List<Long> replacedSessionIds)`，列表防御性拷贝 |
| `StoredSession` | `@Builder` 记录：`userId`、`sessionId`、`accountType`、`clientType`、`deviceId`（可空）、`createdAt`、`lastActiveAt`、`expiresAt`；`toCurrentUser()` 给网关建认证对象 |
| `RedisSessionStore` | 构造参数 `StringRedisTemplate`、`Clock`。`IssuedSession create(NewSession)`；`Optional<StoredSession> findAndTouch(SessionToken)`；`boolean delete(AccountType, long userId, long sessionId)`；`List<Long> deleteAll(AccountType, long userId)` |
| `SessionStoreUnavailableException` | `DomainException`，特征 `DEP_UNAVAILABLE`，文案「服务暂时不可用」，两边的错误引擎都映射成 503 |
| `TransientRedisFailures` | `static boolean isTransient(RuntimeException)`：连不上（`RedisConnectionFailureException` 等 `DataAccessResourceFailureException`）、超时（`QueryTimeoutException`）、Lettuce 的 `RedisLoadingException` / `RedisReadOnlyException` / `RedisBusyException`、消息以 `MASTERDOWN` 开头的命令错误。其余（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，不算暂时 |

`RedisSessionStore` 的每个方法都把暂时失败转成 `SessionStoreUnavailableException`，其余异常原样抛出。`create` 对 `maxSessionsPerUser < 1`、`idleTimeout > absoluteLifetime` 抛 `IllegalArgumentException`，这是调用方的缺陷。

消费方各自声明 Bean：identity 在 `IdentityConfiguration`，网关在自己的配置类；都用容器里的 `Clock`。

## 8. identity 的领域模型

### 8.1 聚合与值对象

| 名称 | 类别 | 内容和行为 |
|---|---|---|
| `UserLoginRecord` | 聚合根 | ID（= 会话 ID）、用户 ID、`ClientType`、`DeviceId`（可空）、绝对过期时间、结束时间、结束原因、版本、审计时间。`start(userId, LoginClient, expiresAt)` 新建未结束的记录；`end(LoginEndReason, now)` 已结束时什么都不做，保留第一次的时间和原因；`isEnded()` |
| `LoginEndReason` | 枚举 | `LOGOUT`、`BANNED`、`REPLACED` |
| `DeviceId` | 值对象 | 去首尾空格，最多 128 个字符；`validate(raw) → Optional<FieldViolation>`（`TOO_LONG`）；空白按没有 |
| `LoginClient` | 值对象 | `ClientType` + 可空 `DeviceId`。`validate(rawClientType, rawDeviceId) → List<FieldViolation>`：类型为空按 `web`，不认识给 `INVALID_FORMAT`；`of(...)` |
| `SessionLifetime` | 值对象 | `idle`、`absolute`，构造时校验正数且 `idle ≤ absolute` |
| `SessionLifetimePolicy` | 值对象 | `Map<ClientType, SessionLifetime>` + `maxSessionsPerUser`；`lifetimeFor(ClientType)` 缺行时抛 `IllegalStateException`（启动校验保证本版不会） |
| `NewUserSession` | 记录 | domain 自己的签发输入：用户 ID、会话 ID、`LoginClient`、`now`、`SessionLifetime`、上限 |
| `IssuedUserSession` | 记录 | `token`（字符串）、`replacedSessionIds` |

domain 不依赖会话模块的类型，所以 `NewUserSession` / `IssuedUserSession` 和模块的 `NewSession` / `IssuedSession` 是两套，由 infra 的适配器转换。多两个小记录，换 domain 纯净检查不用开口子。

### 8.2 端口与领域服务

| 端口 | 实现（infra） | 方法要点 |
|---|---|---|
| `UserLoginRecordRepository` | `UserLoginRecordRepositoryAdapter` | `save`（新记录分配雪花 ID）、`findById` |
| `SessionStorePort` | `SessionStoreAdapter`（转给 `RedisSessionStore`） | `issue(NewUserSession) → IssuedUserSession`；`revoke(AccountType, userId, sessionId) → boolean`；`revokeAll(AccountType, userId) → List<Long>`。`SessionStoreUnavailableException` 原样穿过 |
| `UserReadPort` | `UserReadAdapter` | `findAccount(userId) → Optional<UserAccountReadModel(userId, email, status)>` |

包位置按 `.claude/rules/tech/port-service.md`：端口在 `domain/port/{repository|session|read}/`，读模型在 `domain/model/read/`，实现在 `infra/adapter/{persistence|session|read}/`，查询服务在 `app/usecase/user/query/`。

领域服务 `SessionIssuer`（`domain/service/`），依赖上面前两个端口、`SessionLifetimePolicy`、`Clock`：

1. `now = clock.instant()`，按客户端类型取 `SessionLifetime`。
2. `records.save(UserLoginRecord.start(userId, client, now + absolute))`，拿到 ID。
3. `store.issue(...)`，拿到令牌和被挤掉的 ID。
4. 被挤掉的每个 ID：`findById` 有就 `end(REPLACED, now)` 再 `save`。
5. 返回 `IssuedUserSession`。

它不管事务，由调用它的处理器包事务。

### 8.3 表

Flyway `V2__add_user_login_record.sql`，表 `idn_user_login_record`，审计列和触发器照 `V1` 的写法：

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键，等于会话 ID |
| `user_id` | `BIGINT` | 非空；外键 `fk_idn_user_login_record_user` 指向 `idn_user(id)`；索引 `idx_idn_user_login_record_user_id` |
| `client_type` | `VARCHAR(16)` | 非空；检查约束取值 `WEB` |
| `device_id` | `VARCHAR(128)` | 可空 |
| `expires_at` | `timestamptz(6)` | 非空，绝对过期 |
| `ended_at` | `timestamptz(6)` | 可空 |
| `end_reason` | `VARCHAR(16)` | 可空；检查约束取值 `LOGOUT`、`BANNED`、`REPLACED`；检查约束 `(ended_at IS NULL) = (end_reason IS NULL)` |

登录时间就是审计列 `created_at`，不另加一列。枚举在表里存名字（同 `status` 列），在 Redis 和断言里用 `code`。不存令牌的哈希：没有读它的路径。

## 9. 流程与事务

| 流程 | 顺序 | 事务 |
|---|---|---|
| 登录 `LoginUserHandler` | PAP-63 的七步不变（事务外，哈希要排队）→ 校验通过后 `transactions.execute`：`sessionIssuer.issue(...)` → 返回令牌、用户 ID、邮箱 | Redis 写在事务里，失败回滚记录，返回 503 |
| 注册 `RegisterUserHandler` | 现有事务里存完用户和凭据 → `sessionIssuer.issue(...)` → 返回带令牌的结果 | 同上；Redis 挂了整个注册回滚，用户重试不会撞 409 |
| 登出 `LogoutUserHandler` | `currentUserPort.current()` 为空直接返回 → `store.revoke(USER, userId, sid)` → 返回 `true` 时 `transactions.execute`：`findById(sid)` 有就 `end(LOGOUT, now)` 并保存 | 只有 DB 一段事务。先删 Redis 是因为那才是登出的实质；DB 失败时用户已经登出，记录留空 |
| 封禁 `BanUserHandler` | 现有事务里 `user.ban`、保存 → `store.revokeAll(USER, userId)` → 返回的每个 ID `end(BANNED, now)` 并保存 | Redis 失败回滚封禁，返回 503，管理员重试 |

- Redis 放在事务里而不是提交后：提交后再写 Redis，失败时记录已经落库、会话却没建成，审计里多出一次没发生过的登录。放在事务里只剩一种坏情况：Redis 写成功、提交失败，会话存在但没记录。这种会话的主人确实通过了密码校验，安全上没事，只是审计少一行；登出时按 ID 找不到记录就记一条 INFO 放过。
- 登出时 `revoke` 返回 `false`（会话已经没了：过期、被封禁、被挤掉）不动记录：记录的结束原因以先发生的为准。
- 登录的 `recordSuccess` 在建会话之前，建会话失败不影响失败计数清零。
- `LoginUserCommand(email, password, clientType, deviceId)`、`LoginUserResult(sessionToken, userId, email)`；`RegisterUserCommand` 加同样两个字段、`RegisterUserResult` 加 `sessionToken`；`LogoutUserCommand` 没有字段（处理器用 `CurrentUserPort`）。四个字段的校验在处理器里一次报全：邮箱、密码照旧，客户端用 `LoginClient.validate`。
- `/auth/me` 走 `UserQueryService.currentAccount()`：`currentUserPort.require()` → `userReadPort.findAccount(userId)`；查不到或状态是 `BANNED` 抛 `AuthenticationRequiredException` 并记 WARN。

## 10. 接口

| 接口 | 请求 | 成功 |
|---|---|---|
| `POST /auth/register` | `{ "email", "password", "clientType"?, "deviceId"? }` | `201` + `AuthenticatedUserResponse` |
| `POST /auth/login` | 同上 | `200` + `AuthenticatedUserResponse` |
| `POST /auth/logout` | 无请求体；`Authorization` 可有可无 | `204` |
| `GET /auth/me` | 无 | `200` + `CurrentUserResponse` |

```json
AuthenticatedUserResponse  { "sessionToken": "patra_user_…", "userId": "…", "email": "…" }
CurrentUserResponse        { "userId": "…", "email": "…", "accountType": "user" }
```

- `clientType` 不传或为空按 `web`；不认识的值 422、原因码 `INVALID_FORMAT`。`deviceId` 可空，去首尾空格后最多 128 个字符，超长 422、原因码 `TOO_LONG`。本版门户两个都不传。
- `UserAccountResponse` 改名 `AuthenticatedUserResponse`；`accountType` 输出 `code`。
- 登出：有当前用户就删会话、结束记录；没有就什么都不做，照样 204。重复登出、不带令牌登出都走这条。
- `/auth/me`：没有当前用户、查不到用户、用户已封禁都是 401，对客户端都是「这个会话不再有效，清掉 Cookie」。封禁那一种只会出现在断言还没过期的 60 秒窗口里。
- 后台接口不变。OpenAPI 标签仍是 Auth、Admin。

## 11. 错误契约

在 PAP-63 第 10.1 节的表上加三行、改一行：

| 场景 | 异常 | 特征 | 状态 | 错误码 | detail |
|---|---|---|---|---|---|
| 需要登录但没有当前用户；会话指向的用户不存在或已封禁 | `AuthenticationRequiredException`（`patra-common-security`） | `UNAUTHORIZED` | 401 | `IDN-0401` | Authentication required |
| 封禁、解封撞上乐观锁 | `UserModifiedConcurrentlyException`（新） | `CONFLICT` | 409 | `IDN-0409` | 用户正被其他操作修改，请重试 |
| 会话存储暂时不可用 | `SessionStoreUnavailableException`（会话模块） | `DEP_UNAVAILABLE` | 503 | `IDN-0503` | 服务暂时不可用 |
| 哈希排队超时、登录限流的 Redis 不可用（改动） | `TemporarilyUnavailableException` | `DEP_UNAVAILABLE` | 503 | `IDN-0503` | 服务暂时不可用。`LoginThrottleAdapter` 的判定改用 `TransientRedisFailures.isTransient`，`LOADING` 等状态也归 503 |

- 乐观锁：`UserRepositoryAdapter.save` 捕获 `OptimisticLockingFailureException` 转成 `UserModifiedConcurrentlyException`；现在 409 的 `detail` 是 Hibernate 的异常消息，带实体类全名和 ID。
- 字段错误的原因码只用现有的 `INVALID_FORMAT`、`TOO_LONG`，前端不用新认。

## 12. 配置与接入

| 项 | 设计 |
|---|---|
| `application.yml` | 第 6.3 节的 `patra.identity.session.*` 默认值 |
| `application-dev.yml` | `patra.security.identity-assertion.public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}`，没有默认值；Redis 地址照旧 |
| `application-container.yml` | 同一个占位，变量由 PAP-66 注入 |
| 安全 starter | boot 加 `patra-spring-boot-starter-security`：过滤器链、`CurrentUserPort`、401 / 403 输出、JPA 审计人都由它接管；identity 自己不写安全配置 |
| Bean | `IdentityConfiguration`：`SessionLifetimePolicy`、`RedisSessionStore`（`StringRedisTemplate` + `Clock`）、`SessionIssuer` |
| 本地运行 | 先用 PAP-70 的 `./gradlew :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut=<路径>` 生成密钥对，标准输出的公钥设进 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`，私钥给本地网关（PAP-65）；步骤写进 identity 的 README |
| 集成测试 | 公钥由安全 starter 测试支持的 `TestIdentityAssertionEnvironmentPostProcessor` 注入；带身份的请求用 `TestIdentity.headers(...)` |

不往仓库放 dev 密钥对，哪怕只给 dev 用：决策 G 不允许。

## 13. 令牌不进日志

1. `SessionToken`、`LoginUserResult`、`RegisterUserResult`、`AuthenticatedUserResponse` 的 `toString()` 遮掩令牌；`LoginUserCommand`、`RegisterUserCommand` 照旧遮掩密码。
2. CommandBus 的日志拦截器只记命令类名（PAP-63 确认过），处理器里不打印结果。
3. 会话模块的日志只出现用户 ID 和会话 ID，不出现令牌和哈希。
4. 测试：集成测试走完注册、登录、登出后，断言日志输出和除登录 / 注册响应之外的响应体里都找不到那个令牌，写法照 PAP-63 的密码用例。

## 14. 测试策略

单元测试：

- 会话模块：`SessionToken` 的生成长度与字符集、解析对前缀 / 长度 / 字符的拒绝、同一令牌哈希稳定、`toString` 遮掩；`TransientRedisFailures` 对每类异常的分类；`NewSession` 的参数校验；`StoredSession.toCurrentUser()`。
- identity domain：`UserLoginRecord` 开始与结束（结束两次保留第一次）；`DeviceId`、`LoginClient` 的原因码；`SessionLifetime`、`SessionLifetimePolicy` 的校验；`SessionIssuer` 的五步顺序和被挤掉记录的收尾（端口用 mock）。
- identity app：登录在校验通过后才建会话、字段错误一次报全（含 `clientType`）；注册建会话；登出有 / 无当前用户各调了什么、`revoke` 返回 `false` 不动记录；封禁调 `revokeAll` 并逐条结束；`UserQueryService` 的三种 401。
- `IdentityConfiguration` 的启动校验：缺 `user.web`、不认识的键、`idle > absolute`。

集成测试：

- 会话模块（Redis 容器，用测试 starter 的 `RedisContainerInitializer`，固定时钟）：建会话后 `findAndTouch` 能查到且字段齐全；60 秒内不写回、满 60 秒写回且 TTL 重算；TTL 不超过绝对过期；过绝对期返回空并删键；超上限按最老挤掉并返回 ID；悬空条目被清；`delete` / `deleteAll` 的返回值；Redis 指向没人监听的端口时抛 `SessionStoreUnavailableException`。
- identity infra（PG 容器）：登录记录的保存与查询；`V2` 的检查约束；乐观锁冲突转成 `UserModifiedConcurrentlyException`。
- identity adapter 切片：`clientType` 不认识的 422 形状；登出 204 两种情况；`/auth/me` 的 200 与 401；409 的固定文案。
- identity 端到端（PG + Redis 容器）：注册和登录返回令牌，Redis 里只有哈希、键 2 有索引；登出后会话消失、记录标 `LOGOUT`，同一令牌再登出和不带令牌登出都 204；封禁后全部会话消失、记录标 `BANNED`，解封后能重新登录；第 11 次登录挤掉最老的一条、记录标 `REPLACED`；日志和响应里找不到令牌。
- `RedisUnavailableIT` 改写：Redis 连不上时注册和登录都 503，注册回滚后库里没有那个用户。它现在断言「注册不依赖 Redis」，本设计改变了这一点。

回归：`./gradlew check integrationTest`（`check` 不含集成测试）；`./gradlew dumpModuleGraph` 后模块图与构建一致；`detect-changes.test.sh` 通过。

## 15. 实施时要实测的点

结果写回本节。

1. Spring Data Redis 4.x 的 `LettuceExceptionConverter` 把 Lettuce 的 `RedisLoadingException` / `RedisReadOnlyException` / `RedisBusyException` 包成什么：预期是 `RedisSystemException`，原因是 Lettuce 异常本身；`MASTERDOWN` 没有专门的类，按消息前缀。
2. `StringRedisTemplate.execute(RedisScript<List>, …)` 对 Lua 返回的扁平数组和嵌套数组各给什么 Java 类型；`touch` 返回空时 Java 侧拿到的是 `null` 还是空列表。
3. 雪花 ID 当前的位数（预期 18），脚本里的长度优先比较用位数不同的 ID 测一次。
4. `transactions.execute` 里抛 `SessionStoreUnavailableException` 时记录被回滚；注册的回滚要连用户和凭据一起。
5. 安全 starter 接入 identity 后，PAP-63 的切片测试和端到端测试要不要补公钥配置才能启动。

## 16. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 依赖 `:patra-api:patra-identity:patra-identity-session`，用 `StringRedisTemplate` 和 `Clock` 声明 `RedisSessionStore`；每个请求：取 `Authorization: Bearer` → `SessionToken.parse`，解析失败按匿名 → `findAndTouch`，空按匿名 → `StoredSession.toCurrentUser()` 建 `CurrentUserAuthentication` | PAP-65 |
| 公开路由上照样翻译令牌：令牌有效就签断言，否则登出接口拿不到当前用户。路由规则只决定匿名能不能过 | PAP-65 |
| `SessionStoreUnavailableException` 输出 503（安全 starter 设计第 17 节要求包成 `AuthenticationServiceException`） | PAP-65 |
| 网关不配有效期，续期只照会话里的字段 | PAP-65 |
| 环境变量 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`（identity、网关）；Redis 密码；`generateIdentityAssertionKey` 的 runbook | PAP-66 |
| Cookie 里放 `sessionToken` 原文，`Max-Age` 30 天并在用户活动时刷新；收到 401 清 Cookie；注册、登录请求不传 `clientType` 和 `deviceId` | PAP-67 |
| 踢人下线、查看登录设备：键 2 和 `idn_user_login_record` 已有所需数据；踢人时考虑 Sa-Token 式的标记以区分「被踢」和「过期」 | 后台账号上线时 |

## 17. 要同步改的文档和 Issue

本设计评审通过后一起改：

- release spec 决策 B 补一条「补充（2026-10-09，PAP-64 设计）」：令牌带账号类型前缀 `patra_user_`，理由见本设计第 6.1 节；存储契约放 `patra-identity-session`。
- Linear PAP-64：验收标准「Redis 和数据库里都只有令牌的哈希」改为「数据库里不存令牌，Redis 里只存令牌的哈希」；To-Do 的 Tech Design 一项指向本文档。
- Linear PAP-65：描述里加第 16 节的前四条。
- identity README：会话一节（令牌、键、策略配置）、本地生成密钥的步骤、`/auth/logout` 与 `/auth/me`。
- 会话模块 README：第 6、7 节的契约，给 PAP-65 用。
- PAP-63 设计第 16 节交给 PAP-64 的八条，本设计逐条落实；不改原文。
