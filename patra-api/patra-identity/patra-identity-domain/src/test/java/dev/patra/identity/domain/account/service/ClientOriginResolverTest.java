package dev.patra.identity.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.port.geolocation.IpGeolocationPort;
import dev.patra.identity.domain.account.port.useragent.UserAgentParsingPort;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ClientOriginResolver 单元测试：内网不查属地，公网查（PAP-99 设计第 8.1 节）。
@DisplayName("ClientOriginResolver 单元测试")
class ClientOriginResolverTest {

  static final String UA = "Mozilla/5.0 Chrome";

  private final UserAgentParsingPort parser = mock(UserAgentParsingPort.class);
  private final IpGeolocationPort geolocation = mock(IpGeolocationPort.class);
  private final ClientOriginResolver resolver = new ClientOriginResolver(parser, geolocation);

  @BeforeEach
  void stubParser() {
    when(parser.parse(UA)).thenReturn(DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP));
  }

  @Test
  @DisplayName("公网地址：查属地，带上设备名与端")
  void should_locate_public_address() {
    when(geolocation.locate("113.92.157.29")).thenReturn(Optional.of("中国 广东 深圳"));

    ClientOrigin origin = resolver.resolve(RequestOrigin.of("113.92.157.29", UA));

    assertThat(origin.ip()).isEqualTo("113.92.157.29");
    assertThat(origin.userAgent()).isEqualTo(UA);
    assertThat(origin.deviceName()).isEqualTo("Chrome 141 · macOS");
    assertThat(origin.deviceClass()).isEqualTo(DeviceClass.DESKTOP);
    assertThat(origin.ipRegion()).isEqualTo("中国 广东 深圳");
    assertThat(origin.privateNetwork()).isFalse();
  }

  @Test
  @DisplayName("内网地址：不查属地，标记内网")
  void should_not_locate_private_address() {
    ClientOrigin origin = resolver.resolve(RequestOrigin.of("192.168.97.1", UA));

    assertThat(origin.ipRegion()).isNull();
    assertThat(origin.privateNetwork()).isTrue();
    verifyNoInteractions(geolocation);
  }

  @Test
  @DisplayName("没有 IP：不查属地，也不算内网")
  void should_not_locate_without_ip() {
    when(parser.parse(null)).thenReturn(DeviceProfile.unknown());

    ClientOrigin origin = resolver.resolve(RequestOrigin.unknown());

    assertThat(origin.ip()).isNull();
    assertThat(origin.ipRegion()).isNull();
    assertThat(origin.privateNetwork()).isFalse();
    assertThat(origin.deviceClass()).isEqualTo(DeviceClass.UNKNOWN);
    verifyNoInteractions(geolocation);
  }

  @Test
  @DisplayName("UA 解析出错：按未知设备，不往外抛（安全通知在事务提交之后才解析，抛出会让已生效的操作回 500）")
  void should_fall_back_to_unknown_device_when_parser_fails() {
    when(parser.parse("broken")).thenThrow(new IllegalStateException("规则加载失败"));
    when(geolocation.locate("113.92.157.29")).thenReturn(Optional.of("中国 广东 深圳"));

    ClientOrigin origin = resolver.resolve(RequestOrigin.of("113.92.157.29", "broken"));

    assertThat(origin.deviceName()).isNull();
    assertThat(origin.deviceClass()).isEqualTo(DeviceClass.UNKNOWN);
    assertThat(origin.ipRegion()).isEqualTo("中国 广东 深圳");
  }

  @Test
  @DisplayName("属地查询出错：属地为空，不往外抛")
  void should_fall_back_to_no_region_when_geolocation_fails() {
    when(geolocation.locate("113.92.157.29")).thenThrow(new IllegalStateException("数据文件被换掉"));

    ClientOrigin origin = resolver.resolve(RequestOrigin.of("113.92.157.29", UA));

    assertThat(origin.ipRegion()).isNull();
    assertThat(origin.deviceName()).isEqualTo("Chrome 141 · macOS");
  }
}
