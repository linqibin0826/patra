package dev.linqibin.patra.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.ClientType;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SessionLifetimePolicy 与 SessionLifetime 单元测试。
@DisplayName("SessionLifetimePolicy 单元测试")
class SessionLifetimePolicyTest {

  private static final SessionLifetime WEB =
      SessionLifetime.of(Duration.ofDays(30), Duration.ofDays(180));

  @Test
  @DisplayName("有效期必须是正数，且不活跃过期不长于绝对过期")
  void should_validate_lifetime() {
    assertThat(WEB.idle()).isEqualTo(Duration.ofDays(30));
    assertThatThrownBy(() -> SessionLifetime.of(Duration.ZERO, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SessionLifetime.of(Duration.ofDays(1), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SessionLifetime.of(Duration.ofDays(2), Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SessionLifetime.of(Duration.ofDays(1), Duration.ofDays(1))).isNotNull();
  }

  @Test
  @DisplayName("按客户端类型取有效期；没配的类型抛 IllegalStateException")
  void should_look_up_lifetime_by_client_type() {
    SessionLifetimePolicy policy = SessionLifetimePolicy.of(Map.of(ClientType.WEB, WEB), 10);

    assertThat(policy.lifetimeFor(ClientType.WEB)).isEqualTo(WEB);
    assertThat(policy.maxSessionsPerUser()).isEqualTo(10);
  }

  @Test
  @DisplayName("至少一行有效期，上限至少 1")
  void should_reject_empty_policy_or_bad_cap() {
    assertThatThrownBy(() -> SessionLifetimePolicy.of(Map.of(), 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SessionLifetimePolicy.of(Map.of(ClientType.WEB, WEB), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
