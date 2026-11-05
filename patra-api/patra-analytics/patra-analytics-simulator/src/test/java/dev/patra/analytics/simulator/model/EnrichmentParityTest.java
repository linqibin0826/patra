package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.collect.model.vo.GeoLocation;
import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;
import dev.patra.analytics.infra.collect.geolocation.Ip2RegionGeolocationAdapter;
import dev.patra.analytics.infra.collect.robot.CounterRobotListAdapter;
import dev.patra.analytics.infra.collect.useragent.YauaaUserAgentAdapter;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/// 模拟器预先写好的 UA / IP 富化结果，必须与采集端点真正算出来的一致（spec §5.2）：灌历史直接写表里的值，实时压测交给端点算。
class EnrichmentParityTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final TrafficModel MODEL = TrafficModel.load(null, MAPPER);
  private static final YauaaUserAgentAdapter YAUAA = new YauaaUserAgentAdapter();
  private static final CounterRobotListAdapter ROBOTS = new CounterRobotListAdapter(MAPPER);
  private static final Ip2RegionGeolocationAdapter GEO =
      new Ip2RegionGeolocationAdapter(
          Path.of(System.getProperty("patra.analytics.ip-geolocation.directory")));

  @AfterAll
  static void close() {
    GEO.close();
  }

  @Test
  void human_uas_are_not_robots_and_parse_as_declared() {
    for (UaProfile ua : MODEL.userAgents()) {
      if (ua.crawler()) {
        continue;
      }
      assertThat(ROBOTS.listed(ua.userAgent())).as(ua.userAgent()).isFalse();
      assertThat(YAUAA.parse(ua.userAgent()))
          .as(ua.userAgent())
          .isEqualTo(new UserAgentInfo(ua.browserFamily(), ua.osFamily(), ua.deviceClass()));
    }
  }

  @Test
  void crawler_uas_hit_counter_robots_and_parse_as_declared() {
    assertThat(MODEL.userAgents()).anyMatch(UaProfile::crawler);
    for (UaProfile ua : MODEL.userAgents()) {
      if (!ua.crawler()) {
        continue;
      }
      assertThat(ROBOTS.listed(ua.userAgent())).as(ua.userAgent()).isTrue();
      assertThat(YAUAA.parse(ua.userAgent()))
          .as(ua.userAgent())
          .isEqualTo(new UserAgentInfo(ua.browserFamily(), ua.osFamily(), ua.deviceClass()));
    }
  }

  @Test
  void every_region_ip_resolves_to_declared_country_and_province() {
    for (Region region : MODEL.regions()) {
      assertThat(region.ips()).isNotEmpty();
      for (String ip : region.ips()) {
        assertThat(GEO.locate(ip))
            .as(ip)
            .isEqualTo(new GeoLocation(region.countryCode(), region.province()));
      }
    }
  }

  @Test
  void region_shares_match_spec() {
    double total = MODEL.regions().stream().mapToDouble(Region::weight).sum();
    double domestic =
        MODEL.regions().stream()
            .filter(r -> "CN".equals(r.countryCode()))
            .mapToDouble(Region::weight)
            .sum();
    double unknown =
        MODEL.regions().stream()
            .filter(r -> r.countryCode() == null)
            .mapToDouble(Region::weight)
            .sum();

    assertThat(domestic / total).isBetween(0.929, 0.931);
    assertThat(unknown / total).isBetween(0.019, 0.021);
  }
}
