# patra-identity-session

identity 与网关共用的会话存储契约：令牌格式、Redis 键、四段 Lua 和 `RedisSessionStore`。
只有这两个消费者；其他服务只认网关签的身份断言（`patra-spring-boot-starter-security`），不碰 Redis。

设计：`docs/patra/specs/2026-10-09-identity-session-design.md` 第 6、7 节。

## 令牌

`patra_user_` + 43 个 base64url 字符（32 字节 `SecureRandom`），总长 54。前缀只说明账号类型，
网关靠它决定查哪个键空间。`SessionToken.parse` 对格式不对的值返回空，调用方按匿名处理。
Redis 里只存 `SessionToken.hash()`：整个令牌 UTF-8 的 SHA-256，小写十六进制。令牌不进日志，
`toString()` 只有前缀。

## Redis 键

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `idn:session:user:<哈希>` | HASH | 一条会话 | `min(idle_timeout_ms, expires_at − now)`，续期时重算 |
| `idn:user-sessions:user:<userId>` | HASH | `session_id → <哈希>` | 不小于该用户最新会话的绝对有效期 |

会话字段（都是字符串）：`user_id`、`session_id`、`account_type`（`user`）、`client_type`（`web`）、
`device_id`（可选，没有就没这个字段）、`created_at`、`last_active_at`、`expires_at`（epoch 毫秒）、
`idle_timeout_ms`。

## 操作

| 方法 | 脚本 | 做什么 |
|---|---|---|
| `create(NewSession)` | `session-create.lua` | 清掉索引里指向不存在会话的条目；到上限按会话 ID 从小到大挤掉多出来的；写会话和索引。返回令牌和被挤掉的 ID |
| `findAndTouch(SessionToken)` | `session-touch.lua` | 读会话；过了绝对过期返回空并删键；距上次写入满 60 秒（`RENEW_INTERVAL`）才写回 `last_active_at` 和 TTL |
| `delete(accountType, userId, sessionId)` | `session-delete.lua` | 按索引找到哈希删会话；真的删掉了返回 `true` |
| `deleteAll(accountType, userId)` | `session-delete-all.lua` | 删该用户全部会话和索引；返回真的删掉了的会话 ID |

时间由 Java 按注入的 `Clock` 传进脚本，脚本不调 `TIME`。会话 ID 的大小按「字符串长度，再字典序」比，
雪花 ID 转成 Lua 的 double 会丢精度。脚本「没有结果」时返回空表而不是 `false`：Lettuce 把 `false`
解成 `[null]`，Java 侧拿到的不是空列表。

## 网关怎么用（PAP-65）

```java
@Bean
RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
  return new RedisSessionStore(redis, clock);
}
```

每个请求：取 `Authorization: Bearer` 的值 → `SessionToken.parse`，空按匿名 → `findAndTouch`，
空按匿名 → `StoredSession.toCurrentUser()` 建 `CurrentUserAuthentication`，再签断言。
网关不配任何有效期：续期只照会话里的字段做。

## 错误

Redis 连不上、超时、`LOADING` / `READONLY` / `BUSY` / `MASTERDOWN` 转成
`SessionStoreUnavailableException`（`DEP_UNAVAILABLE`，503）。判定在 `TransientRedisFailures`，
identity 的登录限流也用它。其他 Redis 异常（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，原样抛出。

## 限制

- 只支持单机 Redis：会话键在脚本里由前缀拼出来，Redis Cluster 要求所有键事先声明。
- 索引里过期会话留下的条目在该用户下一次登录时清理，封禁时整键删除。

## 测试

单测 `./gradlew :patra-api:patra-identity:patra-identity-session:test`；集成测试
`…:integrationTest` 用测试 starter 的 `RedisContainerInitializer`（`redis:7.0.15`），要本机 Docker 在线。
