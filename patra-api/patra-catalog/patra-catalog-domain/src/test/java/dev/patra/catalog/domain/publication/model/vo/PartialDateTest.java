package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PartialDate` 单元测试：按部件推断精度、校验部件组合。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PartialDate 单元测试")
class PartialDateTest {

  @Test
  @DisplayName("精度按部件推断：年 / 月 / 日")
  void should_infer_precision_from_parts() {
    assertThat(PartialDate.ofYear(2023).precision()).isEqualTo("year");
    assertThat(PartialDate.ofYearMonth(2022, 10).precision()).isEqualTo("month");
    assertThat(PartialDate.ofDay(LocalDate.of(2021, 10, 14)).precision()).isEqualTo("day");
  }

  @Test
  @DisplayName("ofDay 拆成三个部件")
  void should_split_local_date_into_parts() {
    assertThat(PartialDate.ofDay(LocalDate.of(2021, 10, 14)))
        .isEqualTo(new PartialDate(2021, 10, 14, null, null));
  }

  @Test
  @DisplayName("月不在 1 ~ 12、有日无月、日对该月不合法：拒绝")
  void should_reject_invalid_part_combinations() {
    assertThatThrownBy(() -> new PartialDate(2021, 13, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PartialDate(2021, null, 5, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PartialDate(2021, 2, 30, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
