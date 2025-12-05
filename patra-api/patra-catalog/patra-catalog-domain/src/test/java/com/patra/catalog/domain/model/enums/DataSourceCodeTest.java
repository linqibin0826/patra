package com.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 数据来源代码枚举单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DataSourceCode 数据来源代码枚举")
@Timeout(2)
class DataSourceCodeTest {

  @Nested
  @DisplayName("fromCode() 方法")
  class FromCodeTests {

    @ParameterizedTest(name = "代码 \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析所有有效代码值")
    @CsvSource({
      "OPENALEX, OPENALEX",
      "PUBMED, PUBMED",
      "DOAJ, DOAJ",
      "CROSSREF, CROSSREF",
      "JCR, JCR"
    })
    void shouldParseAllValidCodes(String code, DataSourceCode expected) {
      assertThat(DataSourceCode.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "小写代码 \"{0}\" 应正确解析")
    @DisplayName("应支持小写代码值")
    @CsvSource({
      "openalex, OPENALEX",
      "pubmed, PUBMED",
      "doaj, DOAJ",
      "crossref, CROSSREF",
      "jcr, JCR"
    })
    void shouldParseLowercaseCodes(String code, DataSourceCode expected) {
      assertThat(DataSourceCode.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "带空格代码 \"{0}\" 应正确解析")
    @DisplayName("应去除代码值首尾空格")
    @CsvSource({"' OPENALEX ', OPENALEX", "' PUBMED', PUBMED", "'DOAJ ', DOAJ"})
    void shouldTrimWhitespace(String code, DataSourceCode expected) {
      assertThat(DataSourceCode.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("空值或空白字符串应抛出异常")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldThrowForBlankCodes(String code) {
      assertThatThrownBy(() -> DataSourceCode.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("数据源代码不能为空");
    }

    @ParameterizedTest
    @DisplayName("无效代码值应抛出异常")
    @ValueSource(strings = {"INVALID", "WOS", "SCOPUS", "ABC"})
    void shouldThrowForInvalidCodes(String code) {
      assertThatThrownBy(() -> DataSourceCode.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的数据源");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull() 方法")
  class FromCodeOrNullTests {

    @ParameterizedTest(name = "代码 \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析有效代码值")
    @CsvSource({"OPENALEX, OPENALEX", "pubmed, PUBMED", "JCR, JCR"})
    void shouldParseValidCodes(String code, DataSourceCode expected) {
      assertThat(DataSourceCode.fromCodeOrNull(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("无效代码应返回 null")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "INVALID", "WOS"})
    void shouldReturnNullForInvalidCodes(String code) {
      assertThat(DataSourceCode.fromCodeOrNull(code)).isNull();
    }
  }

  @Nested
  @DisplayName("类型判断方法")
  class TypeCheckTests {

    @Test
    @DisplayName("isOpenAlex() 应正确判断")
    void shouldIdentifyOpenAlex() {
      assertThat(DataSourceCode.OPENALEX.isOpenAlex()).isTrue();
      assertThat(DataSourceCode.PUBMED.isOpenAlex()).isFalse();
    }

    @Test
    @DisplayName("isPubMed() 应正确判断")
    void shouldIdentifyPubMed() {
      assertThat(DataSourceCode.PUBMED.isPubMed()).isTrue();
      assertThat(DataSourceCode.OPENALEX.isPubMed()).isFalse();
    }

    @Test
    @DisplayName("isDoaj() 应正确判断")
    void shouldIdentifyDoaj() {
      assertThat(DataSourceCode.DOAJ.isDoaj()).isTrue();
      assertThat(DataSourceCode.JCR.isDoaj()).isFalse();
    }

    @Test
    @DisplayName("isCrossref() 应正确判断")
    void shouldIdentifyCrossref() {
      assertThat(DataSourceCode.CROSSREF.isCrossref()).isTrue();
      assertThat(DataSourceCode.DOAJ.isCrossref()).isFalse();
    }

    @Test
    @DisplayName("isJcr() 应正确判断")
    void shouldIdentifyJcr() {
      assertThat(DataSourceCode.JCR.isJcr()).isTrue();
      assertThat(DataSourceCode.CROSSREF.isJcr()).isFalse();
    }
  }

  @Nested
  @DisplayName("功能分类方法")
  class CategoryTests {

    @Test
    @DisplayName("isMetadataSource() 应正确判断元数据源")
    void shouldIdentifyMetadataSource() {
      assertThat(DataSourceCode.OPENALEX.isMetadataSource()).isTrue();
      assertThat(DataSourceCode.PUBMED.isMetadataSource()).isTrue();
      assertThat(DataSourceCode.CROSSREF.isMetadataSource()).isTrue();
      assertThat(DataSourceCode.DOAJ.isMetadataSource()).isFalse();
      assertThat(DataSourceCode.JCR.isMetadataSource()).isFalse();
    }

    @Test
    @DisplayName("isOaSource() 应正确判断 OA 数据源")
    void shouldIdentifyOaSource() {
      assertThat(DataSourceCode.OPENALEX.isOaSource()).isTrue();
      assertThat(DataSourceCode.DOAJ.isOaSource()).isTrue();
      assertThat(DataSourceCode.PUBMED.isOaSource()).isFalse();
      assertThat(DataSourceCode.JCR.isOaSource()).isFalse();
    }

    @Test
    @DisplayName("providesRatingData() 只有 JCR 应返回 true")
    void shouldIdentifyRatingDataProvider() {
      assertThat(DataSourceCode.JCR.isProvidesRatingData()).isTrue();
      assertThat(DataSourceCode.OPENALEX.isProvidesRatingData()).isFalse();
      assertThat(DataSourceCode.DOAJ.isProvidesRatingData()).isFalse();
    }
  }

  @Nested
  @DisplayName("优先级比较")
  class PriorityTests {

    @Test
    @DisplayName("OPENALEX 优先级应最高")
    void openalexShouldHaveHighestPriority() {
      assertThat(DataSourceCode.OPENALEX.comparePriority(DataSourceCode.PUBMED)).isNegative();
      assertThat(DataSourceCode.OPENALEX.comparePriority(DataSourceCode.JCR)).isNegative();
    }

    @Test
    @DisplayName("JCR 优先级应最低")
    void jcrShouldHaveLowestPriority() {
      assertThat(DataSourceCode.JCR.comparePriority(DataSourceCode.OPENALEX)).isPositive();
      assertThat(DataSourceCode.JCR.comparePriority(DataSourceCode.DOAJ)).isPositive();
    }

    @Test
    @DisplayName("相同数据源优先级比较应返回 0")
    void sameSourcShouldReturnZero() {
      assertThat(DataSourceCode.OPENALEX.comparePriority(DataSourceCode.OPENALEX)).isZero();
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class EnumPropertiesTests {

    @Test
    @DisplayName("应包含所有 5 个数据源")
    void shouldHaveAllSources() {
      assertThat(DataSourceCode.values()).hasSize(5);
    }

    @Test
    @DisplayName("所有枚举值应有非空描述")
    void allEnumsShouldHaveDescriptions() {
      for (DataSourceCode source : DataSourceCode.values()) {
        assertThat(source.getCode()).as("%s 应有代码", source.name()).isNotBlank();
        assertThat(source.getDescription()).as("%s 应有描述", source.name()).isNotBlank();
      }
    }
  }
}
