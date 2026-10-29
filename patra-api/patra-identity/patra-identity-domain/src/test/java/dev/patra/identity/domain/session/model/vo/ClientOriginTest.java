package dev.patra.identity.domain.session.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ClientOrigin 单元测试：转成安全通知（PAP-99 设计第 13 节）。
@DisplayName("ClientOrigin 单元测试")
class ClientOriginTest {

  static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

  @Test
  @DisplayName("公网地址：通知里的设备、IP、属地照搬")
  void should_carry_device_ip_and_region_into_notice() {
    ClientOrigin origin =
        ClientOrigin.builder()
            .ip("113.92.157.29")
            .deviceName("Chrome 141 · macOS")
            .deviceClass(DeviceClass.DESKTOP)
            .ipRegion("中国 广东 深圳")
            .build();

    SecurityNotice notice = origin.toNotice(NOW, "不是我");

    assertThat(notice.occurredAt()).isEqualTo(NOW);
    assertThat(notice.device()).isEqualTo("Chrome 141 · macOS");
    assertThat(notice.ipAddress()).isEqualTo("113.92.157.29");
    assertThat(notice.ipLocation()).isEqualTo("中国 广东 深圳");
    assertThat(notice.notMeAdvice()).isEqualTo("不是我");
  }

  @Test
  @DisplayName("内网地址：属地写「内网」")
  void should_label_private_network_in_notice() {
    ClientOrigin origin =
        ClientOrigin.builder()
            .ip("100.70.1.1")
            .deviceClass(DeviceClass.UNKNOWN)
            .privateNetwork(true)
            .build();

    assertThat(origin.toNotice(NOW, "x").ipLocation())
        .isEqualTo(ClientOrigin.PRIVATE_NETWORK_LABEL);
    assertThat(ClientOrigin.PRIVATE_NETWORK_LABEL).isEqualTo("内网");
  }

  @Test
  @DisplayName("没有 IP：IP 与属地都为 null")
  void should_leave_ip_and_location_empty_without_ip() {
    SecurityNotice notice =
        ClientOrigin.builder().deviceClass(DeviceClass.UNKNOWN).build().toNotice(NOW, "x");

    assertThat(notice.ipAddress()).isNull();
    assertThat(notice.ipLocation()).isNull();
    assertThat(notice.device()).isNull();
  }
}
