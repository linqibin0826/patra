package dev.linqibin.patra.identity.infra.adapter.throttle;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import dev.linqibin.patra.identity.session.TransientRedisFailures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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

/// 登录失败限制的 Redis 实现。
///
/// 失败次数、在途登记、锁分三个键，键名带账号类型和邮箱的 SHA-256，不放明文邮箱。
/// 「开始」和「结算」各是一段 Lua 脚本，原子执行。Redis 暂时不可用（连不上、超时、`LOADING` 等，
/// 由会话模块的 `TransientRedisFailures` 判定）转成 {@link TemporarilyUnavailableException}；
/// 其他 Redis 异常（比如脚本写错）是程序缺陷，原样抛出。
@Component
public final class LoginThrottleAdapter implements LoginThrottlePort {

  private static final RedisScript<Long> BEGIN_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/login-throttle-begin.lua"), Long.class);
  private static final RedisScript<Long> SETTLE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/login-throttle-settle.lua"), Long.class);

  /// 「失败数 + 在途数」到上限时告诉调用方的等待时间。
  private static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(1);

  private final StringRedisTemplate redis;
  private final LoginThrottlePolicy policy;

  /// 创建适配器。
  ///
  /// @param redis Redis 模板
  /// @param policy 失败限制的参数
  public LoginThrottleAdapter(StringRedisTemplate redis, LoginThrottlePolicy policy) {
    this.redis = Objects.requireNonNull(redis, "redis 不能为 null");
    this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
  }

  /// 开始一次尝试。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @return 被放行的尝试
  @Override
  public LoginAttempt begin(AccountType accountType, EmailAddress email) {
    String ticketId = UUID.randomUUID().toString();
    Long result =
        execute(
            () ->
                redis.execute(
                    BEGIN_SCRIPT,
                    keys(accountType, email),
                    String.valueOf(policy.maxFailures()),
                    ticketId,
                    String.valueOf(policy.inFlightTtl().toMillis())));
    if (result == null) {
      // 脚本没有真正执行（比如模板开了事务或流水线）：失败限制默认拒绝，不放行
      throw new TemporarilyUnavailableException();
    }
    if (result > 0) {
      throw new LoginTemporarilyLockedException(Duration.ofMillis(result));
    }
    if (result < 0) {
      throw new LoginTemporarilyLockedException(BUSY_RETRY_AFTER);
    }
    return LoginAttempt.of(accountType, email, ticketId);
  }

  /// 按成功结算。
  ///
  /// @param attempt 尝试
  @Override
  public void recordSuccess(LoginAttempt attempt) {
    settle(attempt, "SUCCESS");
  }

  /// 按失败结算。
  ///
  /// @param attempt 尝试
  /// @return 处于锁定期时返回剩余时间
  @Override
  public Optional<Duration> recordFailure(LoginAttempt attempt) {
    long lockMillis = settle(attempt, "FAILURE");
    return lockMillis > 0 ? Optional.of(Duration.ofMillis(lockMillis)) : Optional.empty();
  }

  /// 按取消结算。
  ///
  /// @param attempt 尝试
  @Override
  public void cancel(LoginAttempt attempt) {
    settle(attempt, "CANCEL");
  }

  /// 执行结算脚本。
  ///
  /// @param attempt 尝试
  /// @param outcome 结果
  /// @return 锁的剩余毫秒，没有锁时为 0
  private long settle(LoginAttempt attempt, String outcome) {
    Long result =
        execute(
            () ->
                redis.execute(
                    SETTLE_SCRIPT,
                    keys(attempt.accountType(), attempt.email()),
                    attempt.ticketId(),
                    outcome,
                    String.valueOf(policy.maxFailures()),
                    String.valueOf(policy.window().toMillis()),
                    String.valueOf(policy.lockDuration().toMillis())));
    return result == null ? 0 : result;
  }

  /// 三个键：失败计数、在途登记、锁。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @return 键名
  static List<String> keys(AccountType accountType, EmailAddress email) {
    String suffix = accountType.getCode() + ":" + sha256Hex(email.value());
    return List.of(
        "idn:login-failures:" + suffix, "idn:login-inflight:" + suffix, "idn:login-lock:" + suffix);
  }

  /// 计算 SHA-256 的十六进制形式。
  ///
  /// @param value 输入
  /// @return 64 位十六进制
  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 执行 Redis 调用，把暂时失败转成 503。
  ///
  /// @param call 调用
  /// @return 脚本返回值；脚本没有真正执行时为 `null`，由调用方决定怎么处理
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
