package dev.linqibin.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginTemporarilyLockedException 单元测试。
@DisplayName("LoginTemporarilyLockedException 单元测试")
class LoginTemporarilyLockedExceptionTest {

  @Test
  @DisplayName("带上剩余等待时间")
  void should_carry_retry_after() {
    LoginTemporarilyLockedException exception =
        new LoginTemporarilyLockedException(Duration.ofSeconds(899).plusMillis(1));

    assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(899).plusMillis(1));
    assertThat(exception.getRetryAfterSeconds()).isEqualTo(900);
  }
}
