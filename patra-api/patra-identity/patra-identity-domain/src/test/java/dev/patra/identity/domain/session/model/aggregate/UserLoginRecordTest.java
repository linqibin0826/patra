package dev.patra.identity.domain.session.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.tool.core.security.ClientType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserLoginRecord 单元测试。
@DisplayName("UserLoginRecord 单元测试")
class UserLoginRecordTest {

  private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2027-04-07T08:00:00Z");
  private static final ClientOrigin UNKNOWN_ORIGIN =
      ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build();

  @Test
  @DisplayName("开始一条记录：带上请求来源的 UA、设备名、端、IP 与属地")
  void should_start_with_client_origin() {
    ClientOrigin origin =
        ClientOrigin.builder()
            .ip("113.92.157.29")
            .userAgent("Mozilla/5.0 Chrome")
            .deviceName("Chrome 141 · macOS")
            .deviceClass(DeviceClass.DESKTOP)
            .ipRegion("中国 广东 深圳")
            .build();

    UserLoginRecord login = UserLoginRecord.start(42L, LoginClient.web(), origin, EXPIRES_AT);

    assertThat(login.getUserAgent()).isEqualTo("Mozilla/5.0 Chrome");
    assertThat(login.getDeviceName()).isEqualTo("Chrome 141 · macOS");
    assertThat(login.getDeviceClass()).isEqualTo(DeviceClass.DESKTOP);
    assertThat(login.getClientIp()).isEqualTo("113.92.157.29");
    assertThat(login.getIpRegion()).isEqualTo("中国 广东 深圳");
  }

  @Test
  @DisplayName("开始一条记录：未结束，带客户端类型、设备标识和绝对过期时间，ID 留给仓储")
  void should_start_unended_record() {
    UserLoginRecord login =
        UserLoginRecord.start(
            42L,
            LoginClient.of("web", "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy"),
            UNKNOWN_ORIGIN,
            EXPIRES_AT);

    assertThat(login.getId()).isNull();
    assertThat(login.getUserId()).isEqualTo(42L);
    assertThat(login.getClientType()).isEqualTo(ClientType.WEB);
    assertThat(login.getDeviceId())
        .map(DeviceId::value)
        .contains("Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy");
    assertThat(login.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(login.isEnded()).isFalse();
    assertThat(login.getEndedAt()).isNull();
    assertThat(login.getEndReason()).isNull();
  }

  @Test
  @DisplayName("结束一次记下时间和原因；再结束保留第一次")
  void should_keep_first_end() {
    UserLoginRecord login =
        UserLoginRecord.start(42L, LoginClient.web(), UNKNOWN_ORIGIN, EXPIRES_AT);

    login.end(LoginEndReason.LOGOUT, NOW);
    login.end(LoginEndReason.BANNED, NOW.plusSeconds(60));

    assertThat(login.isEnded()).isTrue();
    assertThat(login.getEndedAt()).isEqualTo(NOW);
    assertThat(login.getEndReason()).isEqualTo(LoginEndReason.LOGOUT);
  }

  @Test
  @DisplayName("恢复时结束时间和结束原因要么都有要么都没有")
  void should_require_end_fields_together_on_restore() {
    assertThatThrownBy(() -> restored().endedAt(NOW).build())
        .isInstanceOf(IllegalArgumentException.class);
    UserLoginRecord ended = restored().endedAt(NOW).endReason(LoginEndReason.BANNED).build();
    assertThat(ended.isEnded()).isTrue();
    assertThat(ended.getDeviceId()).isEmpty();
    assertThat(ended.toString()).contains("7001").doesNotContain("mac");
  }

  /// 一条从库里恢复的记录的构建器，必填字段已填。
  ///
  /// @return 构建器
  private static UserLoginRecord.UserLoginRecordBuilder restored() {
    return UserLoginRecord.restore()
        .id(7001L)
        .userId(42L)
        .clientType(ClientType.WEB)
        .deviceClass(DeviceClass.UNKNOWN)
        .expiresAt(EXPIRES_AT)
        .version(1L)
        .createdAt(NOW)
        .updatedAt(NOW);
  }
}
