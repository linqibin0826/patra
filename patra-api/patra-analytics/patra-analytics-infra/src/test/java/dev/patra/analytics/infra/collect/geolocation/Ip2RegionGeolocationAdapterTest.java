package dev.patra.analytics.infra.collect.geolocation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.collect.model.vo.GeoLocation;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Ip2RegionGeolocationAdapterTest {

  private static Ip2RegionGeolocationAdapter adapter;

  @BeforeAll
  static void open() {
    adapter =
        new Ip2RegionGeolocationAdapter(
            Path.of(System.getProperty("patra.analytics.ip-geolocation.directory")));
  }

  @AfterAll
  static void close() {
    adapter.close();
  }

  @Test
  void locate_china_ip_gives_cn_and_province() {
    GeoLocation location = adapter.locate("114.114.114.114");

    assertThat(location.countryCode()).isEqualTo("CN");
    assertThat(location.province()).matches(".+(省|市|自治区|特别行政区)");
  }

  @Test
  void locate_foreign_ip_gives_country_without_province() {
    GeoLocation location = adapter.locate("8.8.8.8");

    assertThat(location.countryCode()).isEqualTo("US");
    assertThat(location.province()).isNull();
  }

  @Test
  void locate_private_ip_is_unknown() {
    assertThat(adapter.locate("192.168.97.1")).isEqualTo(GeoLocation.unknown());
  }

  @Test
  void locate_returns_empty_for_malformed_ip() {
    assertThat(adapter.locate("unknown")).isEqualTo(GeoLocation.unknown());
    assertThat(adapter.locate("")).isEqualTo(GeoLocation.unknown());
  }

  @Test
  void locate_handles_ipv4_mapped_ipv6() {
    assertThat(adapter.locate("::ffff:114.114.114.114").countryCode()).isEqualTo("CN");
  }
}
