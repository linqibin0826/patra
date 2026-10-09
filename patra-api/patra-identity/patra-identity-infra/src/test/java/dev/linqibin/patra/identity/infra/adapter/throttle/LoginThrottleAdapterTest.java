package dev.linqibin.patra.identity.infra.adapter.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.TemporarilyUnavailableException;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import io.lettuce.core.RedisLoadingException;
import io.lettuce.core.RedisNoScriptException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/// LoginThrottleAdapter 单元测试：键名，以及脚本没有真正执行时的处理。
@DisplayName("LoginThrottleAdapter 单元测试")
class LoginThrottleAdapterTest {

  private static final LoginThrottlePolicy POLICY =
      LoginThrottlePolicy.of(
          5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30));

  @Test
  @DisplayName("三个键带账号类型和邮箱的 SHA-256，不含明文邮箱")
  void should_build_keys_with_account_type_and_email_digest() {
    List<String> keys =
        LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("chen.yu@example.com"));

    assertThat(keys).hasSize(3);
    assertThat(keys.get(0)).matches("idn:login-failures:user:[0-9a-f]{64}");
    assertThat(keys.get(1)).matches("idn:login-inflight:user:[0-9a-f]{64}");
    assertThat(keys.get(2)).matches("idn:login-lock:user:[0-9a-f]{64}");
    assertThat(String.join(",", keys)).doesNotContain("chen.yu");
  }

  @Test
  @DisplayName("同一个邮箱的三个键后缀相同，不同邮箱不同")
  void should_share_suffix_for_same_email_only() {
    List<String> a = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("a@example.com"));
    List<String> b = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("b@example.com"));

    assertThat(suffix(a.get(0))).isEqualTo(suffix(a.get(2)));
    assertThat(suffix(a.get(0))).isNotEqualTo(suffix(b.get(0)));
  }

  @Test
  @DisplayName("开始脚本返回 null（脚本没有真正执行）时拒绝放行，按依赖不可用处理")
  void should_reject_when_begin_script_returns_null() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    LoginThrottleAdapter adapter =
        new LoginThrottleAdapter(
            redis,
            LoginThrottlePolicy.of(
                5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofSeconds(30)));

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, EmailAddress.of("a@example.com")))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  @DisplayName("Redis 正在加载数据（LOADING）：按依赖不可用处理，返回 503")
  void should_treat_loading_as_unavailable() {
    LoginThrottleAdapter adapter =
        new LoginThrottleAdapter(
            failingWith(
                new RedisSystemException(
                    "Error in execution",
                    new RedisLoadingException("LOADING Redis is loading the dataset in memory"))),
            POLICY);

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, EmailAddress.of("a@example.com")))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  @DisplayName("脚本本身出错（NOSCRIPT）：是缺陷，原样抛出")
  void should_rethrow_script_defects() {
    LoginThrottleAdapter adapter =
        new LoginThrottleAdapter(
            failingWith(
                new RedisSystemException(
                    "Error in execution",
                    new RedisNoScriptException("NOSCRIPT No matching script."))),
            POLICY);

    assertThatThrownBy(() -> adapter.begin(AccountType.USER, EmailAddress.of("a@example.com")))
        .isInstanceOf(RedisSystemException.class);
  }

  /// 一个执行脚本就抛指定异常的模板。不 mock 可变参数的方法，直接覆盖。
  ///
  /// @param failure 要抛的异常
  /// @return 模板
  private static StringRedisTemplate failingWith(RuntimeException failure) {
    return new StringRedisTemplate() {
      @Override
      public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        throw failure;
      }
    };
  }

  /// 取键名最后一段。
  ///
  /// @param key 键名
  /// @return 最后一个冒号之后的部分
  private static String suffix(String key) {
    return key.substring(key.lastIndexOf(':') + 1);
  }
}
