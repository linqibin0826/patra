package dev.linqibin.patra.catalog.domain.model.vo.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EvidenceLevel 证据等级衍生")
class EvidenceLevelTest {

  @Test
  @DisplayName("RCT → 4 档")
  void rct() {
    assertThat(EvidenceLevel.classify(List.of("Randomized Controlled Trial")))
        .isEqualTo(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL);
  }

  @Test
  @DisplayName("Meta 分析 → 5 档")
  void metaAnalysis() {
    assertThat(EvidenceLevel.classify(List.of("Meta-Analysis")))
        .isEqualTo(EvidenceLevel.SYSTEMATIC_REVIEW);
  }

  @Test
  @DisplayName("系统综述 → 5 档")
  void systematicReview() {
    assertThat(EvidenceLevel.classify(List.of("Systematic Review")))
        .isEqualTo(EvidenceLevel.SYSTEMATIC_REVIEW);
  }

  @Test
  @DisplayName("Scoping review 不做效应合成 → 2 档（非系统综述）")
  void scopingReview_ranksAsNonSystematicReview() {
    assertThat(EvidenceLevel.classify(List.of("Scoping Review")))
        .isEqualTo(EvidenceLevel.NON_SYSTEMATIC_REVIEW);
  }

  @Test
  @DisplayName("多类型取最强档：Journal Article + Meta-Analysis → 5 档")
  void takesStrongest() {
    assertThat(EvidenceLevel.classify(List.of("Journal Article", "Meta-Analysis")))
        .isEqualTo(EvidenceLevel.SYSTEMATIC_REVIEW);
  }

  @Test
  @DisplayName("观察性研究 → 3 档")
  void cohortOrCaseControl() {
    assertThat(EvidenceLevel.classify(List.of("Observational Study")))
        .isEqualTo(EvidenceLevel.COHORT_OR_CASE_CONTROL);
  }

  @Test
  @DisplayName("病例报告 → 1 档")
  void caseReport() {
    assertThat(EvidenceLevel.classify(List.of("Case Reports")))
        .isEqualTo(EvidenceLevel.CASE_REPORT);
  }

  @Test
  @DisplayName("无法判定（仅 Journal Article / Editorial）→ UNKNOWN")
  void unknown() {
    assertThat(EvidenceLevel.classify(List.of("Journal Article", "Editorial")))
        .isEqualTo(EvidenceLevel.UNKNOWN);
  }

  @Test
  @DisplayName("空列表 → UNKNOWN")
  void emptyList_returnsUnknown() {
    assertThat(EvidenceLevel.classify(List.of())).isEqualTo(EvidenceLevel.UNKNOWN);
  }

  @Test
  @DisplayName("null → UNKNOWN")
  void nullCollection_returnsUnknown() {
    assertThat(EvidenceLevel.classify(null)).isEqualTo(EvidenceLevel.UNKNOWN);
  }

  @Test
  @DisplayName("大小写 / 空格不敏感")
  void caseInsensitive() {
    assertThat(EvidenceLevel.classify(List.of("  randomized controlled trial ")))
        .isEqualTo(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL);
  }

  @Test
  @DisplayName("UNKNOWN 之外均视为已衍生（derived 标记）")
  void derivedFlag() {
    assertThat(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL.isDerived()).isTrue();
    assertThat(EvidenceLevel.UNKNOWN.isDerived()).isFalse();
  }

  @Test
  @DisplayName("typeValuesOf 返回该档全部小写类型值，与 classify 互为逆映射")
  void typeValuesOf_roundTripsWithClassify() {
    for (EvidenceLevel level : EvidenceLevel.values()) {
      for (String typeValue : EvidenceLevel.typeValuesOf(level)) {
        assertThat(typeValue).isEqualTo(typeValue.toLowerCase(Locale.ROOT));
        assertThat(EvidenceLevel.classify(List.of(typeValue))).isEqualTo(level);
      }
    }
    assertThat(EvidenceLevel.typeValuesOf(EvidenceLevel.SYSTEMATIC_REVIEW))
        .containsExactlyInAnyOrder("systematic review", "meta-analysis", "network meta-analysis");
    assertThat(EvidenceLevel.typeValuesOf(EvidenceLevel.CASE_REPORT))
        .containsExactly("case reports");
  }

  @Test
  @DisplayName("UNKNOWN 无类型值")
  void typeValuesOf_unknownIsEmpty() {
    assertThat(EvidenceLevel.typeValuesOf(EvidenceLevel.UNKNOWN)).isEmpty();
  }

  @Test
  @DisplayName("rank 固定为 5..0，SQL 里的 CASE 常量依赖这组值")
  void ranksArePinned() {
    assertThat(EvidenceLevel.SYSTEMATIC_REVIEW.rank()).isEqualTo(5);
    assertThat(EvidenceLevel.RANDOMIZED_CONTROLLED_TRIAL.rank()).isEqualTo(4);
    assertThat(EvidenceLevel.COHORT_OR_CASE_CONTROL.rank()).isEqualTo(3);
    assertThat(EvidenceLevel.NON_SYSTEMATIC_REVIEW.rank()).isEqualTo(2);
    assertThat(EvidenceLevel.CASE_REPORT.rank()).isEqualTo(1);
    assertThat(EvidenceLevel.UNKNOWN.rank()).isEqualTo(0);
  }
}
