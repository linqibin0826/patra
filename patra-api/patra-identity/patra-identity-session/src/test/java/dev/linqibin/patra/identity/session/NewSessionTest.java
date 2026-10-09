package dev.linqibin.patra.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// NewSession 单元测试。
@DisplayName("NewSession 单元测试")
class NewSessionTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

  /// 一个合法的会话，各用例在它上面改一个字段。
  ///
  /// @return 建造器
  private static NewSession.NewSessionBuilder valid() {
    return NewSession.builder()
        .userId(42L)
        .sessionId(7001L)
        .accountType(AccountType.USER)
        .clientType(ClientType.WEB)
        .createdAt(NOW)
        .expiresAt(NOW.plus(Duration.ofDays(180)))
        .idleTimeout(Duration.ofDays(30))
        .maxSessionsPerUser(10);
  }

  @Test
  @DisplayName("合法的会话能建出来，空白的设备标识按没有")
  void should_build_valid_session_and_blank_device_as_absent() {
    assertThat(valid().build().deviceId()).isNull();
    assertThat(valid().deviceId("  ").build().deviceId()).isNull();
    assertThat(valid().deviceId("mac-safari").build().deviceId()).isEqualTo("mac-safari");
  }

  @Test
  @DisplayName("非正数的 ID、上限小于 1、过期不晚于创建、不活跃过期不是正数都被拒绝")
  void should_reject_invalid_values() {
    assertThatThrownBy(() -> valid().userId(0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().sessionId(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().maxSessionsPerUser(0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().expiresAt(NOW).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().idleTimeout(Duration.ZERO).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> valid().accountType(null).build())
        .isInstanceOf(NullPointerException.class);
  }
}
