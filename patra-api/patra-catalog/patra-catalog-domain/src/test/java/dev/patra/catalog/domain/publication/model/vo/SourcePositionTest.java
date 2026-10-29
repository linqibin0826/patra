package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `SourcePosition` 单元测试：由 PubMed 文件名算位置，比较新旧。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SourcePosition 单元测试")
class SourcePositionTest {

  @Test
  @DisplayName("rank = 年 × 10000 + 序号")
  void should_compute_rank_from_file_name() {
    assertThat(SourcePosition.ofFileName("pubmed26n1333.xml.gz").rank()).isEqualTo(261_333);
    assertThat(SourcePosition.ofFileName("pubmed26n1333.xml.gz").fileName())
        .isEqualTo("pubmed26n1333.xml.gz");
  }

  @Test
  @DisplayName("下一年的新 baseline 比上一年所有文件都新")
  void should_order_next_year_baseline_after_previous_year_updates() {
    assertThat(SourcePosition.ofFileName("pubmed27n0001.xml.gz").rank())
        .isGreaterThan(SourcePosition.ofFileName("pubmed26n1999.xml.gz").rank());
  }

  @Test
  @DisplayName("isOlderThan：库里没有位置时不算旧；比库里小才算旧")
  void should_compare_against_stored_rank() {
    SourcePosition p = SourcePosition.ofFileName("pubmed26n1333.xml.gz");

    assertThat(p.isOlderThan(null)).isFalse();
    assertThat(p.isOlderThan(261_340)).isTrue();
    assertThat(p.isOlderThan(261_333)).isFalse();
    assertThat(p.isOlderThan(261_300)).isFalse();
  }

  @Test
  @DisplayName("文件名不合规时拒绝")
  void should_reject_invalid_file_name() {
    assertThatThrownBy(() -> SourcePosition.ofFileName("foo.xml.gz"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
