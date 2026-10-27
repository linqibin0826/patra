package dev.patra.interaction.domain.post.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/// 由日期算出的时长（spec 4.4）与登记快照用的投稿月份。
class ExperienceFieldsTest {

  /// 3 月 2 日投稿、4 月 13 日首轮。
  ///
  /// @return 构建器
  private static ExperienceFields.ExperienceFieldsBuilder base() {
    return ExperienceFields.builder()
        .submittedOn(LocalDate.parse("2026-03-02"))
        .firstDecisionOn(LocalDate.parse("2026-04-13"))
        .articleType(ArticleType.REVIEW);
  }

  /// 修改类有最终日期：投稿到最终决定；没有：null。首轮终局：等于首轮用时。
  @Test
  void should_compute_total_days_per_outcome() {
    ExperienceFields revised =
        base()
            .firstDecision(FirstDecision.MAJOR_REVISION)
            .finalOutcome(FinalOutcome.ACCEPTED)
            .finalDecisionOn(LocalDate.parse("2026-06-01"))
            .build();
    assertThat(revised.firstRoundDays()).isEqualTo(42);
    assertThat(revised.totalDays()).isEqualTo(91);

    assertThat(
            base()
                .firstDecision(FirstDecision.MINOR_REVISION)
                .finalOutcome(FinalOutcome.IN_PROGRESS)
                .build()
                .totalDays())
        .isNull();
    assertThat(
            base()
                .firstDecision(FirstDecision.DESK_REJECT)
                .finalOutcome(FinalOutcome.REJECTED)
                .build()
                .totalDays())
        .isEqualTo(42);
  }

  /// 投稿月份 `yyyy-MM`。
  @Test
  void should_format_submitted_month() {
    assertThat(base().build().submittedMonth()).isEqualTo("2026-03");
  }
}
