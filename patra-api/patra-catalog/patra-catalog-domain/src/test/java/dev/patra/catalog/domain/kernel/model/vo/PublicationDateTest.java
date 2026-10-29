package dev.patra.catalog.domain.kernel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.enums.PublicationDateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PublicationDate` 单元测试：对外的 ISO 8601 降精度字符串。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PublicationDate 单元测试")
class PublicationDateTest {

  @Test
  @DisplayName("按精度输出：2023 / 2022-10 / 2021-10-14")
  void should_render_reduced_precision_iso_string() {
    assertThat(PublicationDate.ofYear(PublicationDateType.PUBLISHED, 2023).toIsoString())
        .isEqualTo("2023");
    assertThat(PublicationDate.ofYearMonth(PublicationDateType.PUBLISHED, 2022, 10).toIsoString())
        .isEqualTo("2022-10");
    assertThat(PublicationDate.of(PublicationDateType.PUBLISHED, 2021, 10, 14).toIsoString())
        .isEqualTo("2021-10-14");
  }

  @Test
  @DisplayName("带原文（MedlineDate、季节）时仍输出数字形式，不用原文")
  void should_ignore_date_string_and_season() {
    PublicationDate d =
        PublicationDate.builder()
            .dateType(PublicationDateType.PUBLISHED)
            .year(1998)
            .season("Spring")
            .dateString("1998 Dec-1999 Jan")
            .build();

    assertThat(d.toIsoString()).isEqualTo("1998");
  }

  @Test
  @DisplayName("由部件直接生成降精度字符串；年份为空时为 null")
  void should_render_iso_from_parts() {
    assertThat(PublicationDate.isoString(2023, null, null)).isEqualTo("2023");
    assertThat(PublicationDate.isoString(2022, 10, null)).isEqualTo("2022-10");
    assertThat(PublicationDate.isoString(2021, 10, 14)).isEqualTo("2021-10-14");
    assertThat(PublicationDate.isoString(null, null, null)).isNull();
  }
}
