package dev.patra.identity.infra.adapter.throttle;

import dev.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.patra.identity.domain.policy.EmailResendPolicy;
import dev.patra.identity.domain.port.throttle.EmailResendThrottlePort;
import dev.patra.identity.session.TransientRedisFailures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/// 重发验证邮件限流的 Redis 实现：冷却用字符串键，三个滑动窗口用有序集合，一段 Lua 原子执行。
///
/// 时间由调用方传入、脚本不调 `TIME`，固定时钟下窗口能被拨过去；键的过期用真实时间，只是回收。
/// 用户 ID 进键名（不是个人信息），IP 哈希后进键名。Redis 暂时不可用（连不上、超时、`LOADING` 等，
/// 由会话模块的 `TransientRedisFailures` 判定）转成 {@link TemporarilyUnavailableException}；
/// 其他 Redis 异常是程序缺陷，原样抛出。
@Component
public final class EmailResendThrottleAdapter implements EmailResendThrottlePort {

  private static final RedisScript<Long> SCRIPT =
      RedisScript.of(new ClassPathResource("redis/email-resend-throttle.lua"), Long.class);

  /// IP 未知时第四个键的占位，脚本按 `ipPresent = 0` 跳过它。
  static final String NO_IP_KEY = "idn:email-resend:hour:ip:-";

  private final StringRedisTemplate redis;
  private final EmailResendPolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 限流规则
  public EmailResendThrottleAdapter(StringRedisTemplate redis, EmailResendPolicy policy) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 申请发一封：判四个维度并计数。
  ///
  /// @param userId 用户 ID
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  @Override
  public Optional<Duration> acquire(long userId, String clientIp, Instant now) {
    long wait = run(userId, clientIp, now, true);
    return wait > 0 ? Optional.of(Duration.ofMillis(wait)) : Optional.empty();
  }

  /// 只计冷却和两个按账号的窗口，不判、不计 IP。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  @Override
  public void markSent(long userId, Instant now) {
    run(userId, null, now, false);
  }

  /// 执行脚本。
  ///
  /// @param userId 用户 ID
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param now 当前时间
  /// @param enforce 是否判限
  /// @return 还要等的毫秒，放行为 0
  private long run(long userId, String clientIp, Instant now, boolean enforce) {
    Objects.requireNonNull(now, "now 不能为 null");
    Long result =
        execute(
            () ->
                redis.execute(
                    SCRIPT,
                    keys(userId, clientIp),
                    Long.toString(now.toEpochMilli()),
                    Long.toString(policy.cooldown().toMillis()),
                    Long.toString(EmailResendPolicy.HOUR_WINDOW.toMillis()),
                    Long.toString(EmailResendPolicy.DAY_WINDOW.toMillis()),
                    Integer.toString(policy.perAccountHourly()),
                    Integer.toString(policy.perAccountDaily()),
                    Integer.toString(policy.perIpHourly()),
                    enforce ? "1" : "0",
                    clientIp == null ? "0" : "1",
                    UUID.randomUUID().toString()));
    if (result == null) {
      // 脚本没有真正执行（比如模板开了事务或流水线）：限流默认拒绝
      throw new TemporarilyUnavailableException();
    }
    return result;
  }

  /// 四个键：冷却、账号小时窗口、账号天窗口、IP 小时窗口。
  ///
  /// @param userId 用户 ID
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 键名
  static List<String> keys(long userId, String clientIp) {
    String ipKey =
        clientIp == null ? NO_IP_KEY : "idn:email-resend:hour:ip:" + sha256Hex(clientIp, 32);
    return List.of(
        "idn:email-resend:cooldown:user:" + userId,
        "idn:email-resend:hour:user:" + userId,
        "idn:email-resend:day:user:" + userId,
        ipKey);
  }

  /// SHA-256 的十六进制前缀。
  ///
  /// @param value 输入
  /// @param length 要几位十六进制
  /// @return 前缀
  private static String sha256Hex(String value, int length) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
          .substring(0, length);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 执行 Redis 调用，把暂时失败转成 503。
  ///
  /// @param call 调用
  /// @return 脚本返回值；脚本没有真正执行时为 `null`
  private static Long execute(Supplier<Long> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new TemporarilyUnavailableException(e);
      }
      throw e;
    }
  }
}
