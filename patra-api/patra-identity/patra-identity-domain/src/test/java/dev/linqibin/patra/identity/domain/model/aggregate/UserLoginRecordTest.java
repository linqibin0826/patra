package dev.linqibin.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserLoginRecord 单元测试。
@DisplayName("UserLoginRecord 单元测试")
class UserLoginRecordTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2027-04-07T08:00:00Z");

  @Test
  @DisplayName("开始一条记录：未结束，带客户端类型、设备标识和绝对过期时间，ID 留给仓储")
  void should_start_unended_record() {
    UserLoginRecord login =
        UserLoginRecord.start(42L, LoginClient.of("web", "mac-safari"), EXPIRES_AT);

    assertThat(login.getId()).isNull();
    assertThat(login.getUserId()).isEqualTo(42L);
    assertThat(login.getClientType()).isEqualTo(ClientType.WEB);
    assertThat(login.getDeviceId()).map(DeviceId::value).contains("mac-safari");
    assertThat(login.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(login.isEnded()).isFalse();
    assertThat(login.getEndedAt()).isNull();
    assertThat(login.getEndReason()).isNull();
  }

  @Test
  @DisplayName("结束一次记下时间和原因；再结束保留第一次")
  void should_keep_first_end() {
    UserLoginRecord login = UserLoginRecord.start(42L, LoginClient.web(), EXPIRES_AT);

    login.end(LoginEndReason.LOGOUT, NOW);
    login.end(LoginEndReason.BANNED, NOW.plusSeconds(60));

    assertThat(login.isEnded()).isTrue();
    assertThat(login.getEndedAt()).isEqualTo(NOW);
    assertThat(login.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
  }

  @Test
  @DisplayName("恢复时结束时间和结束原因要么都有要么都没有")
  void should_require_end_fields_together_on_restore() {
    assertThatThrownBy(
            () ->
                UserLoginRecord.restore(
                    7001L, 42L, ClientType.WEB, null, EXPIRES_AT, NOW, null, 0L, NOW, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    UserLoginRecord ended =
        UserLoginRecord.restore(
            7001L, 42L, ClientType.WEB, null, EXPIRES_AT, NOW, LoginEndReason.BANNED, 1L, NOW, NOW);
    assertThat(ended.isEnded()).isTrue();
    assertThat(ended.getDeviceId()).isEmpty();
    assertThat(ended.toString()).contains("7001").doesNotContain("mac");
  }
}
