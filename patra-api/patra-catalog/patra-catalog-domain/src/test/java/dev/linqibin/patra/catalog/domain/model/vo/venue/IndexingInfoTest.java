package dev.linqibin.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// MEDLINE 索引收录信息值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("IndexingInfo 索引收录信息值对象")
class IndexingInfoTest {

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("of() 应创建完整的索引信息")
    void shouldCreateWithAllFields() {
      // When
      IndexingInfo info = IndexingInfo.of("C", "Nature", "Nature");

      // Then
      assertThat(info.status()).isEqualTo("C");
      assertThat(info.medlineTa()).isEqualTo("Nature");
      assertThat(info.isoAbbreviation()).isEqualTo("Nature");
    }

    @Test
    @DisplayName("ofStatus() 应创建仅状态的索引信息")
    void shouldCreateWithStatusOnly() {
      // When
      IndexingInfo info = IndexingInfo.ofStatus("N");

      // Then
      assertThat(info.status()).isEqualTo("N");
      assertThat(info.medlineTa()).isNull();
      assertThat(info.isoAbbreviation()).isNull();
    }
  }

  @Nested
  @DisplayName("收录状态判断")
  class IndexingStatusTests {

    @Test
    @DisplayName("状态 C 应被识别为当前被索引")
    void statusCShouldBeCurrentlyIndexed() {
      IndexingInfo info = IndexingInfo.ofStatus("C");
      assertThat(info.isCurrentlyIndexed()).isTrue();
      assertThat(info.isDiscontinued()).isFalse();
      assertThat(info.isNeverIndexed()).isFalse();
    }

    @Test
    @DisplayName("状态 Y 应被识别为当前被索引（子集）")
    void statusYShouldBeCurrentlyIndexed() {
      IndexingInfo info = IndexingInfo.ofStatus("Y");
      assertThat(info.isCurrentlyIndexed()).isTrue();
      assertThat(info.isDiscontinued()).isFalse();
      assertThat(info.isNeverIndexed()).isFalse();
    }

    @Test
    @DisplayName("状态 N 应被识别为从未被索引")
    void statusNShouldBeNeverIndexed() {
      IndexingInfo info = IndexingInfo.ofStatus("N");
      assertThat(info.isCurrentlyIndexed()).isFalse();
      assertThat(info.isDiscontinued()).isFalse();
      assertThat(info.isNeverIndexed()).isTrue();
    }

    @Test
    @DisplayName("状态 D 应被识别为已停止收录")
    void statusDShouldBeDiscontinued() {
      IndexingInfo info = IndexingInfo.ofStatus("D");
      assertThat(info.isCurrentlyIndexed()).isFalse();
      assertThat(info.isDiscontinued()).isTrue();
      assertThat(info.isNeverIndexed()).isFalse();
    }
  }

  @Nested
  @DisplayName("缩写标题判断")
  class AbbreviationTests {

    @Test
    @DisplayName("hasMedlineTa() 应正确判断 MEDLINE 缩写")
    void shouldCheckHasMedlineTa() {
      IndexingInfo withTa = IndexingInfo.of("C", "N Engl J Med", null);
      IndexingInfo withoutTa = IndexingInfo.of("C", null, "N. Engl. J. Med.");

      assertThat(withTa.hasMedlineTa()).isTrue();
      assertThat(withoutTa.hasMedlineTa()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("空白 MEDLINE 缩写应返回 false")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnFalseForBlankMedlineTa(String medlineTa) {
      IndexingInfo info = IndexingInfo.of("C", medlineTa, "Nature");
      assertThat(info.hasMedlineTa()).isFalse();
    }

    @Test
    @DisplayName("hasIsoAbbreviation() 应正确判断 ISO 缩写")
    void shouldCheckHasIsoAbbreviation() {
      IndexingInfo withIso = IndexingInfo.of("C", null, "N. Engl. J. Med.");
      IndexingInfo withoutIso = IndexingInfo.of("C", "N Engl J Med", null);

      assertThat(withIso.hasIsoAbbreviation()).isTrue();
      assertThat(withoutIso.hasIsoAbbreviation()).isFalse();
    }

    @ParameterizedTest
    @DisplayName("空白 ISO 缩写应返回 false")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnFalseForBlankIsoAbbreviation(String isoAbbr) {
      IndexingInfo info = IndexingInfo.of("C", "Nature", isoAbbr);
      assertThat(info.hasIsoAbbreviation()).isFalse();
    }
  }

  @Nested
  @DisplayName("getPreferredAbbreviation()")
  class GetPreferredAbbreviationTests {

    @Test
    @DisplayName("有 MEDLINE 缩写时应优先返回 MEDLINE 缩写")
    void shouldPreferMedlineTa() {
      IndexingInfo info = IndexingInfo.of("C", "N Engl J Med", "N. Engl. J. Med.");
      assertThat(info.getPreferredAbbreviation()).isEqualTo("N Engl J Med");
    }

    @Test
    @DisplayName("无 MEDLINE 缩写时应返回 ISO 缩写")
    void shouldFallbackToIsoAbbreviation() {
      IndexingInfo info = IndexingInfo.of("C", null, "N. Engl. J. Med.");
      assertThat(info.getPreferredAbbreviation()).isEqualTo("N. Engl. J. Med.");
    }

    @Test
    @DisplayName("两者都没有时应返回 null")
    void shouldReturnNullWhenBothEmpty() {
      IndexingInfo info = IndexingInfo.ofStatus("N");
      assertThat(info.getPreferredAbbreviation()).isNull();
    }
  }

  @Nested
  @DisplayName("状态常量")
  class StatusConstantsTests {

    @Test
    @DisplayName("状态常量应正确定义")
    void shouldHaveCorrectStatusConstants() {
      assertThat(IndexingInfo.STATUS_CURRENTLY_INDEXED).isEqualTo("C");
      assertThat(IndexingInfo.STATUS_INDEXED_SUBSET).isEqualTo("Y");
      assertThat(IndexingInfo.STATUS_NOT_INDEXED).isEqualTo("N");
      assertThat(IndexingInfo.STATUS_DISCONTINUED).isEqualTo("D");
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("equals 和 hashCode 应基于所有字段")
    void shouldImplementEqualsAndHashCode() {
      IndexingInfo info1 = IndexingInfo.of("C", "Nature", "Nature");
      IndexingInfo info2 = IndexingInfo.of("C", "Nature", "Nature");
      IndexingInfo info3 = IndexingInfo.of("N", "Nature", "Nature");

      assertThat(info1).isEqualTo(info2);
      assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
      assertThat(info1).isNotEqualTo(info3);
    }

    @Test
    @DisplayName("toString 应包含所有字段")
    void shouldHaveToString() {
      IndexingInfo info = IndexingInfo.of("C", "Nature", "Nature");
      String str = info.toString();

      assertThat(str).contains("C");
      assertThat(str).contains("Nature");
    }
  }
}
