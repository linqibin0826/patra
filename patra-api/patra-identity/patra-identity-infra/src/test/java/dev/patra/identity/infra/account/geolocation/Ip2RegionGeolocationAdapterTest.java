package dev.patra.identity.infra.account.geolocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Ip2RegionGeolocationAdapter 单元测试：用 Gradle 任务 `downloadIp2RegionData` 下好的真实数据（tag 见
/// `ip2region.properties`），目录由系统属性 `patra.identity.ip-geolocation.directory` 给出。
@DisplayName("Ip2RegionGeolocationAdapter 单元测试")
class Ip2RegionGeolocationAdapterTest {

  static Ip2RegionGeolocationAdapter adapter;

  @BeforeAll
  static void open() {
    adapter =
        new Ip2RegionGeolocationAdapter(
            Path.of(System.getProperty("patra.identity.ip-geolocation.directory")));
  }

  @AfterAll
  static void close() {
    adapter.close();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      value = {
        "113.92.157.29; 中国 广东 深圳",
        "202.96.209.133; 中国 上海",
        "8.8.8.8; 美国",
        "240e:3b7:3272:d8d0:db09:c067:8d59:539e; 中国 广东 深圳",
        "2001:4860:4860::8888; 美国"
      })
  @DisplayName("IPv4 与 IPv6 的公网地址查得到属地")
  void should_locate_public_addresses(String ip, String region) {
    assertThat(adapter.locate(ip)).contains(region);
  }

  @Test
  @DisplayName("不是 IP 字面量：为空，不做 DNS 解析")
  void should_not_resolve_host_names() {
    assertThat(adapter.locate("not-an-ip")).isEmpty();
    assertThat(adapter.locate("localhost")).isEmpty();
  }

  @Test
  @DisplayName("数据文件缺失：启动失败，信息里指向获取脚本")
  void should_fail_fast_when_data_files_are_missing(@TempDir Path empty) {
    assertThatThrownBy(() -> new Ip2RegionGeolocationAdapter(empty))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fetch-ip2region.sh")
        .hasMessageContaining(empty.toAbsolutePath().toString());
  }
}
