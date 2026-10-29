package dev.patra.catalog.infra.publication.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.infra.publication.parser.RunInAbstractLabelSplitter.LabeledText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// RunInAbstractLabelSplitter 单元测试。
///
/// 验证 PubMed 摘要里粘在正文前的段落标签（`ObjectiveEsophageal…`）能被拆成带标签的段落，
/// 不满足拆分条件的文本原样保留。
@DisplayName("RunInAbstractLabelSplitter 粘连标签拆分")
class RunInAbstractLabelSplitterTest {

  @Test
  @DisplayName("PMID 41575322 的四段粘连标签拆成四段")
  void should_split_glued_labels_of_pmid_41575322() {
    List<LabeledText> result =
        RunInAbstractLabelSplitter.split(
            "ObjectiveEsophageal cancer is rising.MethodsThe dataset was used."
                + "ResultsLightGBM won.ConclusionsThe model works.");

    assertThat(result)
        .containsExactly(
            LabeledText.of("OBJECTIVE", "Esophageal cancer is rising."),
            LabeledText.of("METHODS", "The dataset was used."),
            LabeledText.of("RESULTS", "LightGBM won."),
            LabeledText.of("CONCLUSIONS", "The model works."));
  }

  @Test
  @DisplayName("只有开头一个标签时不拆")
  void should_not_split_when_only_leading_label() {
    assertThat(
            RunInAbstractLabelSplitter.split(
                "ObjectiveEsophageal cancer is rising and nothing else."))
        .isEmpty();
  }

  @Test
  @DisplayName("开头不是标签时不拆")
  void should_not_split_when_text_does_not_start_with_label() {
    assertThat(RunInAbstractLabelSplitter.split("Esophageal cancer.MethodsThe dataset.")).isEmpty();
  }

  @Test
  @DisplayName("标签后面有空格时不拆")
  void should_not_split_when_space_after_label() {
    assertThat(RunInAbstractLabelSplitter.split("Objective Esophageal cancer.Methods The dataset."))
        .isEmpty();
  }

  @Test
  @DisplayName("按最长匹配取标签")
  void should_prefer_longest_label() {
    List<LabeledText> result =
        RunInAbstractLabelSplitter.split("ObjectivesTo test.ResultsIt worked.");

    assertThat(result)
        .containsExactly(
            LabeledText.of("OBJECTIVES", "To test."), LabeledText.of("RESULTS", "It worked."));
  }

  @Test
  @DisplayName("右括号之后的标签也能切开")
  void should_split_after_closing_parenthesis() {
    assertThat(RunInAbstractLabelSplitter.split("BackgroundSome text (n = 3).MethodsWe did it."))
        .containsExactly(
            LabeledText.of("BACKGROUND", "Some text (n = 3)."),
            LabeledText.of("METHODS", "We did it."));
    assertThat(RunInAbstractLabelSplitter.split("BackgroundSome text (n = 3)MethodsWe did it."))
        .containsExactly(
            LabeledText.of("BACKGROUND", "Some text (n = 3)"),
            LabeledText.of("METHODS", "We did it."));
  }

  @Test
  @DisplayName("句子中间的标签词不切")
  void should_not_split_label_word_inside_sentence() {
    assertThat(
            RunInAbstractLabelSplitter.split(
                "BackgroundThe Results of trials vary.ConclusionsMore work."))
        .containsExactly(
            LabeledText.of("BACKGROUND", "The Results of trials vary."),
            LabeledText.of("CONCLUSIONS", "More work."));
  }

  @Test
  @DisplayName("中间的多词标签整段切开（mini 实测：Intervention or Exposures、Main Outcome Measures）")
  void should_split_multi_word_inner_labels() {
    assertThat(
            RunInAbstractLabelSplitter.split(
                "DesignA cohort.Intervention or ExposuresEight patients.Main Outcome MeasuresScores"
                    + " fell.Measures & AnalysisWe used t-tests."))
        .extracting(LabeledText::label)
        .containsExactly(
            "DESIGN", "INTERVENTION OR EXPOSURES", "MAIN OUTCOME MEASURES", "MEASURES & ANALYSIS");
  }

  @Test
  @DisplayName("中间标签最多 6 个词：6 个词能切开，7 个词不认作标签")
  void should_limit_inner_label_to_six_words() {
    assertThat(
            RunInAbstractLabelSplitter.split(
                "DesignA cohort.Alpha Beta Gamma Delta Epsilon ZetaScores fell."))
        .extracting(LabeledText::label)
        .containsExactly("DESIGN", "ALPHA BETA GAMMA DELTA EPSILON ZETA");
    assertThat(
            RunInAbstractLabelSplitter.split(
                "DesignA cohort.Alpha Beta Gamma Delta Epsilon Zeta EtaScores fell."))
        .isEmpty();
  }

  @Test
  @DisplayName("标签后面紧跟数字也能切开（Participants266 adults）")
  void should_split_label_followed_by_digit() {
    assertThat(RunInAbstractLabelSplitter.split("SettingOne clinic.Participants266 adults."))
        .containsExactly(
            LabeledText.of("SETTING", "One clinic."),
            LabeledText.of("PARTICIPANTS", "266 adults."));
  }

  @Test
  @DisplayName("空文本返回空列表")
  void should_return_empty_for_blank() {
    assertThat(RunInAbstractLabelSplitter.split(null)).isEmpty();
    assertThat(RunInAbstractLabelSplitter.split("")).isEmpty();
    assertThat(RunInAbstractLabelSplitter.split("   ")).isEmpty();
  }
}
