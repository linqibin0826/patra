package dev.patra.interaction.domain.post.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/// 统计窗口：`3y`（默认，按投稿日期近 3 年，含当天）与 `all`。
class SummaryWindowTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-27");

  /// 窗口起点。
  @Test
  void should_compute_since() {
    assertThat(SummaryWindow.THREE_YEARS.since(TODAY)).isEqualTo(LocalDate.parse("2023-10-27"));
    assertThat(SummaryWindow.ALL.since(TODAY)).isEqualTo(LocalDate.parse("1990-01-01"));
  }

  /// 参数：没给是 3y；未知 422。
  @Test
  void should_parse_param() {
    assertThat(SummaryWindow.fromParam(null)).isEqualTo(SummaryWindow.THREE_YEARS);
    assertThat(SummaryWindow.fromParam("all")).isEqualTo(SummaryWindow.ALL);
    assertThat(SummaryWindow.fromParam("3y").param()).isEqualTo("3y");
    assertThatThrownBy(() -> SummaryWindow.fromParam("5y"))
        .isInstanceOf(InvalidPostRequestException.class);
  }
}
