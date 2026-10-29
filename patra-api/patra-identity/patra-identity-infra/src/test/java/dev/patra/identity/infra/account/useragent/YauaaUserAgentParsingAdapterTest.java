package dev.patra.identity.infra.account.useragent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/// YauaaUserAgentParsingAdapter 单元测试：真实 UA 解析成「浏览器主版本 · 系统族」与端（PAP-99 设计第 8.2 节）。
@DisplayName("YauaaUserAgentParsingAdapter 单元测试")
class YauaaUserAgentParsingAdapterTest {

  static final String CHROME_MAC =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/141.0.0.0 Safari/537.36";
  static final String SAFARI_IPHONE =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
          + " Gecko) Version/18.6 Mobile/15E148 Safari/604.1";
  static final String CHROME_ANDROID =
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/141.0.0.0 Mobile Safari/537.36";
  static final String EDGE_WINDOWS =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0";
  static final String SAFARI_IPAD =
      "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"
          + " Version/17.5 Mobile/15E148 Safari/604.1";

  static YauaaUserAgentParsingAdapter adapter;

  @BeforeAll
  static void createAdapter() {
    long started = System.nanoTime();
    adapter = new YauaaUserAgentParsingAdapter();
    System.out.printf("Yauaa 初始化耗时 %d ms%n", (System.nanoTime() - started) / 1_000_000);
  }

  static Stream<Arguments> userAgents() {
    return Stream.of(
        Arguments.of(CHROME_MAC, "Chrome 141 · macOS", DeviceClass.DESKTOP),
        Arguments.of(SAFARI_IPHONE, "Safari 18 · iOS", DeviceClass.MOBILE),
        Arguments.of(CHROME_ANDROID, "Chrome 141 · Android", DeviceClass.MOBILE),
        Arguments.of(EDGE_WINDOWS, "Edge 141 · Windows", DeviceClass.DESKTOP),
        Arguments.of(SAFARI_IPAD, "Safari 17 · iOS", DeviceClass.TABLET),
        Arguments.of("curl/8.7.1", "Curl 8", DeviceClass.UNKNOWN),
        Arguments.of("", null, DeviceClass.UNKNOWN),
        Arguments.of("${jndi:ldap://x}", null, DeviceClass.UNKNOWN));
  }

  @ParameterizedTest
  @MethodSource("userAgents")
  @DisplayName("真实 UA 解析成设备名与端")
  void should_parse_real_user_agents(String userAgent, String name, DeviceClass deviceClass) {
    assertThat(adapter.parse(userAgent)).isEqualTo(DeviceProfile.of(name, deviceClass));
  }

  @Test
  @DisplayName("null 进：名字为空、端未知")
  void should_parse_null_as_unknown() {
    assertThat(adapter.parse(null)).isEqualTo(DeviceProfile.unknown());
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "Mac OS, macOS",
        "Windows NT, Windows",
        "Windows 9x, Windows",
        "Chrome OS, ChromeOS",
        "iOS, iOS",
        "Ubuntu, Ubuntu",
        "Cloud, NULL",
        "Hacker, NULL",
        "Unknown, NULL",
        "??, NULL",
        "Anonymized, NULL",
        "NULL, NULL"
      })
  @DisplayName("系统名归一成系统族，未知值省略")
  void should_normalize_os_family(String os, String family) {
    assertThat(YauaaUserAgentParsingAdapter.osFamily(os)).isEqualTo(family);
  }

  @Test
  @DisplayName("设备名缺哪段省哪段，两段都缺为 null，超过 64 个字符截断")
  void should_compose_device_name() {
    assertThat(YauaaUserAgentParsingAdapter.deviceName("Chrome", "141", "Mac OS"))
        .isEqualTo("Chrome 141 · macOS");
    assertThat(YauaaUserAgentParsingAdapter.deviceName("Chrome", "??", "Mac OS"))
        .isEqualTo("Chrome · macOS");
    assertThat(YauaaUserAgentParsingAdapter.deviceName("Unknown", "??", "Linux"))
        .isEqualTo("Linux");
    assertThat(YauaaUserAgentParsingAdapter.deviceName("Unknown", "??", "Unknown")).isNull();
    assertThat(YauaaUserAgentParsingAdapter.deviceName("X".repeat(80), "1", "Linux")).hasSize(64);
  }
}
