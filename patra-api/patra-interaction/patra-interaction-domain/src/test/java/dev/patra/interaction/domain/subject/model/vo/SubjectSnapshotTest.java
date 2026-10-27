package dev.patra.interaction.domain.subject.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// 快照的出版日期只接受 ISO 8601 降精度写法。
class SubjectSnapshotTest {

  /// 年、年月、年月日三种精度与缺失。
  ///
  /// @param date 出版日期
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"2019", "2019-03", "2019-03-15"})
  void should_accept_three_precisions_and_null(String date) {
    assertThat(SubjectSnapshot.of("Title", "Journal", date, null).publishedDate()).isEqualTo(date);
  }

  /// 其他写法是来源的坏数据。
  ///
  /// @param date 出版日期
  @ParameterizedTest
  @ValueSource(
      strings = {"19", "2019-3", "2019-13", "2019-00", "2019-03-32", "2019-03-00", "2019/03"})
  void should_reject_malformed_published_date(String date) {
    assertThatThrownBy(() -> SubjectSnapshot.of("Title", "Journal", date, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 四项都可空。
  @Test
  void should_allow_all_fields_null() {
    SubjectSnapshot snapshot = SubjectSnapshot.of(null, null, null, null);

    assertThat(snapshot.title()).isNull();
    assertThat(snapshot.journal()).isNull();
    assertThat(snapshot.publishedDate()).isNull();
    assertThat(snapshot.abbreviation()).isNull();
  }

  /// 期刊对象的快照：全称放 `title`，MEDLINE 缩写放 `abbreviation`。
  @Test
  void should_keep_optional_abbreviation() {
    SubjectSnapshot snapshot = SubjectSnapshot.of("Lancet (London, England)", null, null, "Lancet");

    assertThat(snapshot.title()).isEqualTo("Lancet (London, England)");
    assertThat(snapshot.abbreviation()).isEqualTo("Lancet");
  }
}
