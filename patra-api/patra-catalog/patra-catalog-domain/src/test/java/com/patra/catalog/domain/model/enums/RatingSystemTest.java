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

/// 期刊评价体系枚举单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RatingSystem 期刊评价体系枚举")
@Timeout(2)
class RatingSystemTest {

  @Nested
  @DisplayName("fromCode() 方法")
  class FromCodeTests {

    @ParameterizedTest(name = "代码 \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析所有有效代码值")
    @CsvSource({"JCR, JCR", "CAS, CAS", "SCOPUS, SCOPUS"})
    void shouldParseAllValidCodes(String code, RatingSystem expected) {
      assertThat(RatingSystem.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "小写代码 \"{0}\" 应正确解析")
    @DisplayName("应支持小写代码值")
    @CsvSource({"jcr, JCR", "cas, CAS", "scopus, SCOPUS"})
    void shouldParseLowercaseCodes(String code, RatingSystem expected) {
      assertThat(RatingSystem.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "带空格代码 \"{0}\" 应正确解析")
    @DisplayName("应去除代码值首尾空格")
    @CsvSource({"' JCR ', JCR", "' CAS', CAS", "'SCOPUS ', SCOPUS"})
    void shouldTrimWhitespace(String code, RatingSystem expected) {
      assertThat(RatingSystem.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("空值或空白字符串应抛出异常")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldThrowForBlankCodes(String code) {
      assertThatThrownBy(() -> RatingSystem.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("评价体系代码不能为空");
    }

    @ParameterizedTest
    @DisplayName("无效代码值应抛出异常")
    @ValueSource(strings = {"INVALID", "WOS", "IF", "CNKI"})
    void shouldThrowForInvalidCodes(String code) {
      assertThatThrownBy(() -> RatingSystem.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的评价体系");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull() 方法")
  class FromCodeOrNullTests {

    @ParameterizedTest(name = "代码 \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析有效代码值")
    @CsvSource({"JCR, JCR", "cas, CAS", "SCOPUS, SCOPUS"})
    void shouldParseValidCodes(String code, RatingSystem expected) {
      assertThat(RatingSystem.fromCodeOrNull(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("无效代码应返回 null")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "INVALID", "WOS"})
    void shouldReturnNullForInvalidCodes(String code) {
      assertThat(RatingSystem.fromCodeOrNull(code)).isNull();
    }
  }

  @Nested
  @DisplayName("类型判断方法")
  class TypeCheckTests {

    @Test
    @DisplayName("isJcr() 应正确判断")
    void shouldIdentifyJcr() {
      assertThat(RatingSystem.JCR.isJcr()).isTrue();
      assertThat(RatingSystem.CAS.isJcr()).isFalse();
      assertThat(RatingSystem.SCOPUS.isJcr()).isFalse();
    }

    @Test
    @DisplayName("isCas() 应正确判断")
    void shouldIdentifyCas() {
      assertThat(RatingSystem.CAS.isCas()).isTrue();
      assertThat(RatingSystem.JCR.isCas()).isFalse();
      assertThat(RatingSystem.SCOPUS.isCas()).isFalse();
    }

    @Test
    @DisplayName("isScopus() 应正确判断")
    void shouldIdentifyScopus() {
      assertThat(RatingSystem.SCOPUS.isScopus()).isTrue();
      assertThat(RatingSystem.JCR.isScopus()).isFalse();
      assertThat(RatingSystem.CAS.isScopus()).isFalse();
    }
  }

  @Nested
  @DisplayName("国际/国内判断")
  class RegionTests {

    @Test
    @DisplayName("isInternational() 应识别国际评价体系")
    void shouldIdentifyInternational() {
      assertThat(RatingSystem.JCR.isInternational()).isTrue();
      assertThat(RatingSystem.SCOPUS.isInternational()).isTrue();
      assertThat(RatingSystem.CAS.isInternational()).isFalse();
    }

    @Test
    @DisplayName("isDomestic() 应识别国内评价体系")
    void shouldIdentifyDomestic() {
      assertThat(RatingSystem.CAS.isDomestic()).isTrue();
      assertThat(RatingSystem.JCR.isDomestic()).isFalse();
      assertThat(RatingSystem.SCOPUS.isDomestic()).isFalse();
    }
  }

  @Nested
  @DisplayName("顶级分区标识")
  class TopQuartileLabelTests {

    @Test
    @DisplayName("JCR 的顶级分区应为 Q1")
    void jcrTopQuartileShouldBeQ1() {
      assertThat(RatingSystem.JCR.getTopQuartileLabel()).isEqualTo("Q1");
    }

    @Test
    @DisplayName("SCOPUS 的顶级分区应为 Q1")
    void scopusTopQuartileShouldBeQ1() {
      assertThat(RatingSystem.SCOPUS.getTopQuartileLabel()).isEqualTo("Q1");
    }

    @Test
    @DisplayName("CAS 的顶级分区应为 1区")
    void casTopQuartileShouldBe1Qu() {
      assertThat(RatingSystem.CAS.getTopQuartileLabel()).isEqualTo("1区");
    }
  }

  @Nested
  @DisplayName("优先级比较")
  class PriorityTests {

    @Test
    @DisplayName("JCR 优先级应最高")
    void jcrShouldHaveHighestPriority() {
      assertThat(RatingSystem.JCR.comparePriority(RatingSystem.CAS)).isNegative();
      assertThat(RatingSystem.JCR.comparePriority(RatingSystem.SCOPUS)).isNegative();
    }

    @Test
    @DisplayName("SCOPUS 优先级应最低")
    void scopusShouldHaveLowestPriority() {
      assertThat(RatingSystem.SCOPUS.comparePriority(RatingSystem.JCR)).isPositive();
      assertThat(RatingSystem.SCOPUS.comparePriority(RatingSystem.CAS)).isPositive();
    }

    @Test
    @DisplayName("相同评价体系优先级比较应返回 0")
    void sameSystemShouldReturnZero() {
      assertThat(RatingSystem.JCR.comparePriority(RatingSystem.JCR)).isZero();
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class EnumPropertiesTests {

    @Test
    @DisplayName("应包含所有 3 种评价体系")
    void shouldHaveAllSystems() {
      assertThat(RatingSystem.values()).hasSize(3);
    }

    @Test
    @DisplayName("所有枚举值应有非空描述")
    void allEnumsShouldHaveDescriptions() {
      for (RatingSystem system : RatingSystem.values()) {
        assertThat(system.getCode()).as("%s 应有代码", system.name()).isNotBlank();
        assertThat(system.getDescription()).as("%s 应有描述", system.name()).isNotBlank();
      }
    }
  }
}
