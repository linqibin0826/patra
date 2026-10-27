package dev.patra.interaction.domain.post.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.interaction.domain.post.exception.InvalidExperienceException;
import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.tool.core.error.field.FieldViolation;
import java.time.LocalDate;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/// 投稿经验的字段解析：必填、写法、范围、日期先后、首轮与最终结果的一致性（spec 4.1 ~ 4.3），错误一次列全。
class ExperienceDraftTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-27");

  /// 合法的基准：3 月 2 日投稿，4 月 13 日首轮大修，6 月 1 日录用。
  ///
  /// @return 输入
  private static ExperienceInput.ExperienceInputBuilder valid() {
    return ExperienceInput.builder()
        .submittedOn("2026-03-02")
        .firstDecisionOn("2026-04-13")
        .firstDecision("MAJOR_REVISION")
        .finalOutcome("ACCEPTED")
        .finalDecisionOn("2026-06-01")
        .articleType("ORIGINAL_RESEARCH");
  }

  /// 全部字段按值解析，正文规范化。
  @Test
  void should_parse_valid_input() {
    ExperienceDraft draft =
        ExperienceDraft.parse(
            valid()
                .reviewRounds(2)
                .reviewQuality(4)
                .editorCommunication(3)
                .apcPaid(true)
                .previouslyRejected(false)
                .body(" 审稿人问了统计方法 ")
                .build(),
            TODAY);

    ExperienceFields fields = draft.fields();
    assertThat(fields.submittedOn()).isEqualTo(LocalDate.parse("2026-03-02"));
    assertThat(fields.firstDecisionOn()).isEqualTo(LocalDate.parse("2026-04-13"));
    assertThat(fields.firstDecision()).isEqualTo(FirstDecision.MAJOR_REVISION);
    assertThat(fields.finalOutcome()).isEqualTo(FinalOutcome.ACCEPTED);
    assertThat(fields.finalDecisionOn()).isEqualTo(LocalDate.parse("2026-06-01"));
    assertThat(fields.articleType()).isEqualTo(ArticleType.ORIGINAL_RESEARCH);
    assertThat(fields.reviewRounds()).isEqualTo(2);
    assertThat(fields.reviewQuality()).isEqualTo(4);
    assertThat(fields.editorCommunication()).isEqualTo(3);
    assertThat(fields.apcPaid()).isTrue();
    assertThat(fields.previouslyRejected()).isFalse();
    assertThat(draft.body().value()).isEqualTo("审稿人问了统计方法");
  }

  /// 选填全空也合法，正文为空。
  @Test
  void should_accept_only_required_fields() {
    ExperienceDraft draft = ExperienceDraft.parse(valid().finalDecisionOn(null).build(), TODAY);

    assertThat(draft.fields().finalDecisionOn()).isNull();
    assertThat(draft.fields().reviewRounds()).isNull();
    assertThat(draft.body()).isNull();
  }

  /// 空输入：五个必填一次列出。
  @Test
  void should_report_all_missing_required_fields_at_once() {
    assertViolations(
        ExperienceInput.builder().build(),
        tuple("submittedOn", "REQUIRED"),
        tuple("firstDecisionOn", "REQUIRED"),
        tuple("firstDecision", "REQUIRED"),
        tuple("finalOutcome", "REQUIRED"),
        tuple("articleType", "REQUIRED"));
  }

  /// 日期写法不对、枚举值未知都是 `INVALID`；空白字符串当作没填。
  @Test
  void should_reject_malformed_date_and_unknown_enum() {
    assertViolations(
        valid().submittedOn("2026-13-01").firstDecision("MAYBE").articleType(" ").build(),
        tuple("submittedOn", "INVALID"),
        tuple("firstDecision", "INVALID"),
        tuple("articleType", "REQUIRED"));
  }

  /// 晚于今天、早于 1990-01-01 都拒绝；今天本身可以。
  @Test
  void should_reject_future_and_too_early_dates() {
    assertViolations(
        valid()
            .submittedOn("1989-12-31")
            .firstDecisionOn("2026-10-28")
            .finalDecisionOn(null)
            .build(),
        tuple("submittedOn", "TOO_EARLY"),
        tuple("firstDecisionOn", "FUTURE"));
    assertThat(
            ExperienceDraft.parse(
                    valid().firstDecisionOn("2026-10-27").finalDecisionOn("2026-10-27").build(),
                    TODAY)
                .fields()
                .finalDecisionOn())
        .isEqualTo(TODAY);
  }

  /// 首轮决定日期不能早于投稿日期（同一天可以）。
  @Test
  void should_reject_first_decision_before_submission() {
    assertViolations(
        valid().firstDecisionOn("2026-03-01").build(),
        tuple("firstDecisionOn", "BEFORE_SUBMISSION"));
    assertThat(
            ExperienceDraft.parse(valid().firstDecisionOn("2026-03-02").build(), TODAY)
                .fields()
                .firstRoundDays())
        .isZero();
  }

  /// 最终决定日期不能早于首轮决定日期。
  @Test
  void should_reject_final_before_first_decision() {
    assertViolations(
        valid().finalDecisionOn("2026-04-12").build(),
        tuple("finalDecisionOn", "BEFORE_FIRST_DECISION"));
  }

  /// 直接拒稿、审后拒稿的最终结果只能是拒稿。
  @Test
  void should_require_rejected_after_desk_or_review_reject() {
    assertViolations(
        valid()
            .firstDecision("DESK_REJECT")
            .finalOutcome("WITHDRAWN")
            .finalDecisionOn(null)
            .build(),
        tuple("finalOutcome", "INCONSISTENT"));
    assertViolations(
        valid()
            .firstDecision("REJECT_AFTER_REVIEW")
            .finalOutcome("ACCEPTED")
            .finalDecisionOn(null)
            .build(),
        tuple("finalOutcome", "INCONSISTENT"));
  }

  /// 首轮录用的最终结果只能是录用。
  @Test
  void should_require_accepted_after_first_round_accept() {
    assertViolations(
        valid().firstDecision("ACCEPT").finalOutcome("IN_PROGRESS").finalDecisionOn(null).build(),
        tuple("finalOutcome", "INCONSISTENT"));
  }

  /// 进行中、首轮终局都不填最终决定日期。
  @Test
  void should_reject_final_date_when_in_progress_or_terminal() {
    assertViolations(
        valid().finalOutcome("IN_PROGRESS").build(), tuple("finalDecisionOn", "NOT_APPLICABLE"));
    assertViolations(
        valid().firstDecision("ACCEPT").build(), tuple("finalDecisionOn", "NOT_APPLICABLE"));
  }

  /// 直接拒稿没有送审：不填审稿轮数与审稿质量（编辑沟通可以填）。
  @Test
  void should_reject_rounds_and_quality_for_desk_reject() {
    assertViolations(
        valid()
            .firstDecision("DESK_REJECT")
            .finalOutcome("REJECTED")
            .finalDecisionOn(null)
            .reviewRounds(1)
            .reviewQuality(3)
            .editorCommunication(2)
            .build(),
        tuple("reviewRounds", "NOT_APPLICABLE"),
        tuple("reviewQuality", "NOT_APPLICABLE"));
  }

  /// 审后拒稿、首轮录用只审了一轮。
  @Test
  void should_require_single_round_for_terminal_after_review() {
    ExperienceInput.ExperienceInputBuilder rejected =
        valid().firstDecision("REJECT_AFTER_REVIEW").finalOutcome("REJECTED").finalDecisionOn(null);
    assertViolations(rejected.reviewRounds(2).build(), tuple("reviewRounds", "INCONSISTENT"));
    assertThat(
            ExperienceDraft.parse(rejected.reviewRounds(1).build(), TODAY).fields().reviewRounds())
        .isEqualTo(1);
  }

  /// 轮数 1–10、评分 1–5。
  @Test
  void should_reject_out_of_range_numbers() {
    assertViolations(
        valid().reviewRounds(11).reviewQuality(0).editorCommunication(6).build(),
        tuple("reviewRounds", "OUT_OF_RANGE"),
        tuple("reviewQuality", "OUT_OF_RANGE"),
        tuple("editorCommunication", "OUT_OF_RANGE"));
    assertViolations(valid().reviewRounds(0).build(), tuple("reviewRounds", "OUT_OF_RANGE"));
  }

  /// 字段本身没解析成功时，不再判与它有关的交叉规则。
  @Test
  void should_skip_cross_rules_when_a_field_failed() {
    assertViolations(valid().firstDecisionOn("昨天").build(), tuple("firstDecisionOn", "INVALID"));
    assertViolations(
        valid().firstDecision("REJECT").finalOutcome("WITHDRAWN").build(),
        tuple("firstDecision", "INVALID"));
  }

  /// 正文的错误与字段错误一起列出。
  @Test
  void should_include_body_violation_with_field_errors() {
    assertViolations(
        valid().submittedOn(null).body("字".repeat(2001)).build(),
        tuple("submittedOn", "REQUIRED"),
        tuple("body", "TOO_LONG"));
  }

  /// 解析失败，错误正好是给出的这些（按字段顺序）。
  ///
  /// @param input 输入
  /// @param expected （字段，原因码）
  private static void assertViolations(ExperienceInput input, Tuple... expected) {
    InvalidExperienceException ex =
        catchThrowableOfType(
            InvalidExperienceException.class, () -> ExperienceDraft.parse(input, TODAY));
    assertThat(ex).isNotNull();
    assertThat(ex.getFieldViolations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(expected);
  }
}
