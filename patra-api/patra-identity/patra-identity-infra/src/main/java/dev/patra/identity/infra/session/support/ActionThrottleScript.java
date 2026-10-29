package dev.patra.identity.infra.session.support;

import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/// 通用的操作限流脚本：一个可选的冷却 + 任意个滑动窗口，一段 Lua 原子执行。四个限流适配器共用它，
/// 各自只负责算键和参数。
///
/// 时间由调用方传入、脚本不调 `TIME`，固定时钟下窗口能被拨过去；键的过期用真实时间，只是回收。
/// Redis 暂时不可用（由会话存储的 `TransientRedisFailures` 判定）转成 {@link TemporarilyUnavailableException}；
/// 脚本没有真正执行（返回 `null`）也按不可用处理：限流默认拒绝，不放行。
public final class ActionThrottleScript {

  /// 没有冷却时冷却键的占位，脚本不读不写它。
  public static final String NO_COOLDOWN_KEY = "-";

  private static final RedisScript<Long> SCRIPT =
      RedisScript.of(new ClassPathResource("redis/action-throttle.lua"), Long.class);

  private final StringRedisTemplate redis;

  /// 创建。
  ///
  /// @param redis Redis 模板
  public ActionThrottleScript(StringRedisTemplate redis) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
  }

  /// 一个滑动窗口：键、长度、上限。
  ///
  /// @param key 有序集合的键
  /// @param length 窗口长度
  /// @param limit 窗口内的上限
  public record Window(String key, Duration length, int limit) {

    /// 校验非空、正数。
    public Window {
      Objects.requireNonNull(key, "key 不能为 null");
      Objects.requireNonNull(length, "length 不能为 null");
      if (length.isZero() || length.isNegative() || limit < 1) {
        throw new IllegalArgumentException("窗口长度必须为正、上限至少为 1");
      }
    }
  }

  /// 执行一次：判（可选）并计数。
  ///
  /// @param cooldownKey 冷却键；没有冷却时传 {@link #NO_COOLDOWN_KEY}
  /// @param cooldown 冷却时长；`Duration.ZERO` 表示没有冷却
  /// @param windows 窗口，可以为空
  /// @param now 当前时间
  /// @param enforce 真则判且计，假则只计
  /// @return 放行为 0；被限时是还要等的毫秒（各维度里最长的）
  /// @throws TemporarilyUnavailableException Redis 暂时不可用或脚本没有真正执行
  public long run(
      String cooldownKey, Duration cooldown, List<Window> windows, Instant now, boolean enforce) {
    Objects.requireNonNull(cooldownKey, "cooldownKey 不能为 null");
    Objects.requireNonNull(cooldown, "cooldown 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    List<String> keys = new ArrayList<>();
    keys.add(cooldownKey);
    List<Object> args = new ArrayList<>();
    args.add(Long.toString(now.toEpochMilli()));
    args.add(Long.toString(cooldown.toMillis()));
    args.add(enforce ? "1" : "0");
    args.add(UUID.randomUUID().toString());
    for (Window window : windows) {
      keys.add(window.key());
      args.add(Long.toString(window.length().toMillis()));
      args.add(Integer.toString(window.limit()));
    }
    Long result;
    try {
      result = redis.execute(SCRIPT, keys, args.toArray());
    } catch (RuntimeException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new TemporarilyUnavailableException(e);
      }
      throw e;
    }
    if (result == null) {
      // 脚本没有真正执行（比如模板开了事务或流水线）：限流默认拒绝
      throw new TemporarilyUnavailableException();
    }
    return result;
  }

  /// 进键名的哈希：SHA-256 十六进制前 32 位。邮箱、IP 都这么处理，键名里不放明文。
  ///
  /// @param value 输入
  /// @return 32 位十六进制
  public static String keyHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 32);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }
}
