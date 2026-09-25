package dev.linqibin.patra.catalog.adapter.rest.portal.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [PortalPublicationSearchRequest] 紧凑构造器归一化与年份区间自检；注解校验在控制器 IT 里以 422 验证。
@DisplayName("PortalPublicationSearchRequest 归一化")
class PortalPublicationSearchRequestTest {

  private static PortalPublicationSearchRequest of(
      List<Long> venue,
      List<String> type,
      List<String> evidence,
      List<String> lang,
      Integer from,
      Integer to) {
    return new PortalPublicationSearchRequest(
        null, null, null, null, from, to, venue, type, evidence, null, lang, null, null, null);
  }

  @Test
  @DisplayName("列表 trim + 去空 token；null 列表 → 空不可变列表；值内逗号保留；venue 里的 null 元素被丢弃")
  void normalizesLists() {
    var req =
        of(
            Arrays.asList(1L, null),
            Arrays.asList(" Clinical Trial, Phase III ", "", "  ", "Review"),
            null,
            null,
            null,
            null);
    assertThat(req.type()).containsExactly("Clinical Trial, Phase III", "Review");
    assertThat(req.venue()).containsExactly(1L);
    assertThat(req.evidence()).isEmpty();
    assertThat(req.lang()).isEmpty();
  }

  @Test
  @DisplayName("yearFrom ≤ yearTo 或任一为 null 时区间合法，否则非法")
  void yearRangeValidity() {
    assertThat(of(null, null, null, null, 2020, 2026).isYearRangeValid()).isTrue();
    assertThat(of(null, null, null, null, null, 2026).isYearRangeValid()).isTrue();
    assertThat(of(null, null, null, null, 2020, null).isYearRangeValid()).isTrue();
    assertThat(of(null, null, null, null, 2026, 2020).isYearRangeValid()).isFalse();
  }
}
