package dev.linqibin.patra.identity.infra.adapter.hashing;

import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/// 密码哈希：Argon2id，参数取 OWASP Password Storage Cheat Sheet 的最低配置。
///
/// 每次计算要占 19 MiB 内存，同时进行的计算有上限；拿不到名额的排队，超时抛
/// {@link TemporarilyUnavailableException}，返回 503。哈希和校验都用 NFKC 之后的密码。
public final class PasswordHashingAdapter implements PasswordHashingPort {

  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_KIB = 19_456;
  private static final int ITERATIONS = 2;

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final Duration waitTimeout;
  private final String dummyHash;

  /// 创建适配器，并用同样的参数生成一个假哈希，供「用户不存在」时校验。
  ///
  /// @param encoder 编码器
  /// @param maxConcurrent 同时进行的计算上限，至少为 1
  /// @param waitTimeout 排队等待的最长时间
  public PasswordHashingAdapter(PasswordEncoder encoder, int maxConcurrent, Duration waitTimeout) {
    if (maxConcurrent < 1) {
      throw new IllegalArgumentException("maxConcurrent 至少为 1");
    }
    this.encoder = Objects.requireNonNull(encoder, "encoder 不能为 null");
    this.permits = new Semaphore(maxConcurrent, true);
    this.waitTimeout = Objects.requireNonNull(waitTimeout, "waitTimeout 不能为 null");
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  /// 用 Argon2id 创建适配器。
  ///
  /// @param maxConcurrent 同时进行的计算上限
  /// @param waitTimeout 排队等待的最长时间
  /// @return 适配器
  public static PasswordHashingAdapter argon2(int maxConcurrent, Duration waitTimeout) {
    return new PasswordHashingAdapter(
        new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS),
        maxConcurrent,
        waitTimeout);
  }

  /// 计算哈希。
  ///
  /// @param password 明文密码
  /// @return 哈希
  @Override
  public PasswordHash hash(PlainPassword password) {
    return withPermit(() -> PasswordHash.of(encoder.encode(password.normalized())));
  }

  /// 校验密码。
  ///
  /// @param password 明文密码
  /// @param hash 存储的哈希
  /// @return 匹配时为 `true`
  @Override
  public boolean matches(PlainPassword password, PasswordHash hash) {
    return withPermit(() -> encoder.matches(password.normalized(), hash.value()));
  }

  /// 拿假哈希做一次校验，结果丢弃。
  ///
  /// @param password 明文密码
  @Override
  public void verifyAgainstDummy(PlainPassword password) {
    withPermit(() -> encoder.matches(password.normalized(), dummyHash));
  }

  /// 拿到名额后执行计算，执行完归还。
  ///
  /// @param work 计算
  /// @param <T> 结果类型
  /// @return 结果
  private <T> T withPermit(Supplier<T> work) {
    boolean acquired;
    try {
      acquired = permits.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TemporarilyUnavailableException(e);
    }
    if (!acquired) {
      throw new TemporarilyUnavailableException();
    }
    try {
      return work.get();
    } finally {
      permits.release();
    }
  }
}
