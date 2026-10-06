package dev.linqibin.patra.identity.infra.adapter.hashing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/// PasswordHashingAdapter 单元测试。
@DisplayName("PasswordHashingAdapter 单元测试")
class PasswordHashingAdapterTest {

  private final PasswordHashingAdapter argon2 =
      PasswordHashingAdapter.argon2(4, Duration.ofSeconds(3));

  @Test
  @DisplayName("输出 Argon2id 编码串，参数是 OWASP 的最低配置（实测点 1）")
  void should_produce_argon2id_hash_with_owasp_parameters() {
    PasswordHash hash = argon2.hash(PlainPassword.of("correct horse battery"));

    assertThat(hash.value()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
  }

  @Test
  @DisplayName("同一个密码能校验通过，别的密码不行")
  void should_match_only_the_same_password() {
    PasswordHash hash = argon2.hash(PlainPassword.of("correct horse battery"));

    assertThat(argon2.matches(PlainPassword.of("correct horse battery"), hash)).isTrue();
    assertThat(argon2.matches(PlainPassword.of("correct horse battery "), hash)).isFalse();
    assertThat(argon2.matches(PlainPassword.of("Correct horse battery"), hash)).isFalse();
  }

  @Test
  @DisplayName("NFKC 前后等价的两个密码互相能校验通过（全角注册、半角登录）")
  void should_treat_nfkc_equivalent_passwords_as_same() {
    PasswordHash hash = argon2.hash(PlainPassword.of("ｐａｓｓｗｏｒｄ－２０２６"));

    assertThat(argon2.matches(PlainPassword.of("password-2026"), hash)).isTrue();
  }

  @Test
  @DisplayName("假哈希校验确实做一次校验，用的是 NFKC 之后的密码")
  void should_complete_dummy_verification() {
    RecordingPasswordEncoder encoder = new RecordingPasswordEncoder();
    PasswordHashingAdapter adapter = new PasswordHashingAdapter(encoder, 1, Duration.ofSeconds(1));

    adapter.verifyAgainstDummy(PlainPassword.of("ｗｈａｔｅｖｅｒ－ｐａｓｓ"));

    assertThat(encoder.matchedPasswords()).containsExactly("whatever-pass");
  }

  @Test
  @DisplayName("同时进行的计算到上限时，排队超时抛 TemporarilyUnavailableException")
  void should_reject_when_queue_wait_times_out() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    PasswordHashingAdapter adapter =
        new PasswordHashingAdapter(
            new BlockingPasswordEncoder(entered, release), 1, Duration.ofMillis(100));
    PasswordHash anyHash = PasswordHash.of("{fake}x");
    CompletableFuture<Boolean> holder =
        CompletableFuture.supplyAsync(() -> adapter.matches(PlainPassword.of("holder"), anyHash));
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> adapter.hash(PlainPassword.of("waiting-one")))
        .isInstanceOf(TemporarilyUnavailableException.class);

    release.countDown();
    assertThat(holder.get(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  @DisplayName("计算完成后归还名额")
  void should_release_permit_after_work() {
    PasswordHashingAdapter adapter =
        new PasswordHashingAdapter(
            new BlockingPasswordEncoder(new CountDownLatch(1), new CountDownLatch(0)),
            1,
            Duration.ofMillis(100));

    adapter.hash(PlainPassword.of("first-call"));

    assertThatCode(() -> adapter.hash(PlainPassword.of("second-call"))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("并发上限至少为 1")
  void should_require_positive_max_concurrent() {
    assertThatThrownBy(() -> PasswordHashingAdapter.argon2(0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 校验时阻塞的假编码器：进入 `matches` 后先发信号，再等放行。
  private static final class BlockingPasswordEncoder implements PasswordEncoder {

    private final CountDownLatch entered;
    private final CountDownLatch release;

    /// 创建假编码器。
    ///
    /// @param entered 进入 `matches` 时计数
    /// @param release 放行信号
    BlockingPasswordEncoder(CountDownLatch entered, CountDownLatch release) {
      this.entered = entered;
      this.release = release;
    }

    /// 返回一个假的编码串，不阻塞。
    ///
    /// @param rawPassword 明文
    /// @return 假编码串
    @Override
    public String encode(CharSequence rawPassword) {
      return "{fake}" + rawPassword;
    }

    /// 阻塞直到放行，然后返回 `true`。
    ///
    /// @param rawPassword 明文
    /// @param encodedPassword 编码串
    /// @return `true`
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      entered.countDown();
      try {
        return release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  /// 记下 `matches` 收到的明文的假编码器。
  private static final class RecordingPasswordEncoder implements PasswordEncoder {

    private final List<String> matchedPasswords = new ArrayList<>();

    /// 返回一个假的编码串。
    ///
    /// @param rawPassword 明文
    /// @return 假编码串
    @Override
    public String encode(CharSequence rawPassword) {
      return "{fake}" + rawPassword;
    }

    /// 记下明文，返回 `false`。
    ///
    /// @param rawPassword 明文
    /// @param encodedPassword 编码串
    /// @return `false`
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      matchedPasswords.add(rawPassword.toString());
      return false;
    }

    /// 返回 `matches` 收到的明文，按调用顺序。
    ///
    /// @return 明文列表
    List<String> matchedPasswords() {
      return List.copyOf(matchedPasswords);
    }
  }
}
