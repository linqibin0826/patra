package dev.linqibin.patra.identity.session;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/// Redis 里的会话存储：identity 建和删，网关查和续期。
///
/// 键 1 `idn:session:<账号类型>:<令牌哈希>` 是一条会话；键 2 `idn:user-sessions:<账号类型>:<用户 ID>`
/// 是该用户全部会话的索引（会话 ID → 令牌哈希）。每个操作是一段 Lua，原子执行。
/// 会话键在脚本里由前缀拼出来，所以只支持单机 Redis。
///
/// Redis 暂时不可用时抛 {@link SessionStoreUnavailableException}；其他 Redis 异常是缺陷，原样抛出。
public final class RedisSessionStore {

  /// 续期间隔：距上次写入满这么久，查会话时才写回最后活跃时间和 TTL。
  public static final Duration RENEW_INTERVAL = Duration.ofSeconds(60);

  static final String SESSION_KEY_PREFIX = "idn:session:";
  static final String INDEX_KEY_PREFIX = "idn:user-sessions:";

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> CREATE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-create.lua"), List.class);

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> TOUCH_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-touch.lua"), List.class);

  private static final RedisScript<Long> DELETE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-delete.lua"), Long.class);

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> DELETE_ALL_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/session-delete-all.lua"), List.class);

  private final StringRedisTemplate redis;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /// 创建存储。
  ///
  /// @param redis Redis 模板
  /// @param clock 判断过期和续期用的时钟
  public RedisSessionStore(StringRedisTemplate redis, Clock clock) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
  }

  /// 建一条会话：生成令牌，写键 1 和键 2。
  ///
  /// @param session 要建的会话
  /// @return 新令牌和被挤掉的会话 ID
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public IssuedSession create(NewSession session) {
    Objects.requireNonNull(session, "session 不能为 null");
    SessionToken token = SessionToken.generate(session.accountType(), random);
    List<?> replaced =
        execute(
            () ->
                redis.execute(
                    CREATE_SCRIPT,
                    List.of(
                        indexKey(session.accountType(), session.userId()),
                        sessionKey(session.accountType(), token.hash())),
                    sessionKeyPrefix(session.accountType()),
                    token.hash(),
                    Long.toString(session.sessionId()),
                    Long.toString(session.userId()),
                    session.accountType().getCode(),
                    session.clientType().getCode(),
                    session.deviceId() == null ? "" : session.deviceId(),
                    Long.toString(session.createdAt().toEpochMilli()),
                    Long.toString(session.idleTimeout().toMillis()),
                    Long.toString(session.expiresAt().toEpochMilli()),
                    Integer.toString(session.maxSessionsPerUser())));
    return IssuedSession.of(token, toSessionIds(replaced));
  }

  /// 按令牌查会话，距上次写入满 {@link #RENEW_INTERVAL} 就续期。
  ///
  /// @param token 令牌
  /// @return 会话；不存在或已过绝对期时为空
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public Optional<StoredSession> findAndTouch(SessionToken token) {
    Objects.requireNonNull(token, "token 不能为 null");
    List<?> fields =
        execute(
            () ->
                redis.execute(
                    TOUCH_SCRIPT,
                    List.of(sessionKey(token.accountType(), token.hash())),
                    Long.toString(clock.millis()),
                    Long.toString(RENEW_INTERVAL.toMillis())));
    if (fields == null || fields.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toStoredSession(fields));
  }

  /// 按用户 ID 和会话 ID 删一条会话。登出用：identity 手里只有断言里的 `sub` 和 `sid`。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return 会话键真正删掉了返回 `true`；会话已经不在或不属于这个用户返回 `false`
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public boolean delete(AccountType accountType, long userId, long sessionId) {
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Long deleted =
        execute(
            () ->
                redis.execute(
                    DELETE_SCRIPT,
                    List.of(indexKey(accountType, userId)),
                    sessionKeyPrefix(accountType),
                    Long.toString(sessionId)));
    return deleted != null && deleted == 1;
  }

  /// 删掉一个用户的全部会话。封禁用。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 会话键真正删掉了的会话 ID，调用方据此结束登录记录
  /// @throws SessionStoreUnavailableException Redis 暂时不可用时
  public List<Long> deleteAll(AccountType accountType, long userId) {
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    List<?> deleted =
        execute(
            () ->
                redis.execute(
                    DELETE_ALL_SCRIPT,
                    List.of(indexKey(accountType, userId)),
                    sessionKeyPrefix(accountType)));
    return toSessionIds(deleted);
  }

  /// 某账号类型的会话键前缀。
  ///
  /// @param accountType 账号类型
  /// @return 前缀，以冒号结尾
  static String sessionKeyPrefix(AccountType accountType) {
    return SESSION_KEY_PREFIX + accountType.getCode() + ":";
  }

  /// 会话键。
  ///
  /// @param accountType 账号类型
  /// @param hash 令牌哈希
  /// @return 键
  static String sessionKey(AccountType accountType, String hash) {
    return sessionKeyPrefix(accountType) + hash;
  }

  /// 用户的会话索引键。
  ///
  /// @param accountType 账号类型
  /// @param userId 用户 ID
  /// @return 键
  static String indexKey(AccountType accountType, long userId) {
    return INDEX_KEY_PREFIX + accountType.getCode() + ":" + userId;
  }

  /// 执行 Redis 调用，把暂时失败转成 503 的异常。
  ///
  /// @param call 调用
  /// @param <T> 结果类型
  /// @return 结果
  private static <T> T execute(Supplier<T> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new SessionStoreUnavailableException(e);
      }
      throw e;
    }
  }

  /// 脚本返回的 ID 列表转成 Long。
  ///
  /// @param raw 脚本返回值，可能为 `null`
  /// @return ID 列表
  private static List<Long> toSessionIds(List<?> raw) {
    if (raw == null) {
      return List.of();
    }
    return raw.stream().map(value -> Long.parseLong(String.valueOf(value))).toList();
  }

  /// `HGETALL` 的扁平数组转成会话。
  ///
  /// @param fields 字段名和值交替的列表
  /// @return 会话
  private static StoredSession toStoredSession(List<?> fields) {
    Map<String, String> values = new HashMap<>();
    for (int i = 0; i + 1 < fields.size(); i += 2) {
      values.put(String.valueOf(fields.get(i)), String.valueOf(fields.get(i + 1)));
    }
    return StoredSession.builder()
        .userId(Long.parseLong(values.get("user_id")))
        .sessionId(Long.parseLong(values.get("session_id")))
        .accountType(
            AccountType.fromCode(values.get("account_type"))
                .orElseThrow(() -> new IllegalStateException("会话里的 account_type 不认识")))
        .clientType(
            ClientType.fromCode(values.get("client_type"))
                .orElseThrow(() -> new IllegalStateException("会话里的 client_type 不认识")))
        .deviceId(values.get("device_id"))
        .createdAt(Instant.ofEpochMilli(Long.parseLong(values.get("created_at"))))
        .lastActiveAt(Instant.ofEpochMilli(Long.parseLong(values.get("last_active_at"))))
        .expiresAt(Instant.ofEpochMilli(Long.parseLong(values.get("expires_at"))))
        .build();
  }
}
