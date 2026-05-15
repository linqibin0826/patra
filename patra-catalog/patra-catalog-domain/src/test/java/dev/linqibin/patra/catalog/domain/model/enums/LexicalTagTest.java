package dev.linqibin.patra.catalog.domain.model.enums;

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

/// MeSH 入口术语词法标记枚举单元测试。
///
/// @author Jobs
/// @since 0.1.0
@DisplayName("LexicalTag 词法标记枚举")
@Timeout(2)
class LexicalTagTest {

  @Nested
  @DisplayName("fromCode() 方法")
  class FromCodeTests {

    @ParameterizedTest(name = "代码 \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析所有有效代码值")
    @CsvSource({
      "NON, NON",
      "PEF, PEF",
      "LAB, LAB",
      "ABB, ABB",
      "ACR, ACR",
      "NAM, NAM",
      "ABX, ABX",
      "ACX, ACX",
      "EPO, EPO",
      "TRD, TRD",
      "HIST, HIST"
    })
    void shouldParseAllValidCodes(String code, LexicalTag expected) {
      assertThat(LexicalTag.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "小写代码 \"{0}\" 应正确解析")
    @DisplayName("应支持小写代码值")
    @CsvSource({
      "non, NON",
      "pef, PEF",
      "abb, ABB",
      "acr, ACR",
      "abx, ABX",
      "acx, ACX",
      "hist, HIST"
    })
    void shouldParseLowercaseCodes(String code, LexicalTag expected) {
      assertThat(LexicalTag.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "混合大小写代码 \"{0}\" 应正确解析")
    @DisplayName("应支持混合大小写代码值")
    @CsvSource({"Non, NON", "Pef, PEF", "Abb, ABB", "Acr, ACR", "Hist, HIST"})
    void shouldParseMixedCaseCodes(String code, LexicalTag expected) {
      assertThat(LexicalTag.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "带空格代码 \"{0}\" 应正确解析")
    @DisplayName("应去除代码值首尾空格")
    @CsvSource({"' NON ', NON", "' PEF', PEF", "'ABB ', ABB"})
    void shouldTrimWhitespace(String code, LexicalTag expected) {
      assertThat(LexicalTag.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("空值或空白字符串应抛出异常")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldThrowForBlankCodes(String code) {
      assertThatThrownBy(() -> LexicalTag.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("词法标记代码不能为空");
    }

    @ParameterizedTest
    @DisplayName("无效代码值应抛出异常")
    @ValueSource(strings = {"INVALID", "XYZ", "ABC", "123", "PREFERRED"})
    void shouldThrowForInvalidCodes(String code) {
      assertThatThrownBy(() -> LexicalTag.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的词法标记");
    }
  }

  @Nested
  @DisplayName("isPreferred() 方法")
  class IsPreferredTests {

    @Test
    @DisplayName("PEF 应返回 true")
    void shouldReturnTrueForPef() {
      assertThat(LexicalTag.PEF.isPreferred()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("非 PEF 枚举值应返回 false")
    @ValueSource(strings = {"NON", "LAB", "ABB", "ACR", "NAM", "ABX", "ACX", "EPO", "TRD", "HIST"})
    void shouldReturnFalseForNonPef(String tagName) {
      LexicalTag tag = LexicalTag.valueOf(tagName);
      assertThat(tag.isPreferred()).isFalse();
    }
  }

  @Nested
  @DisplayName("isAbbreviation() 方法")
  class IsAbbreviationTests {

    @ParameterizedTest
    @DisplayName("缩写类型应返回 true")
    @ValueSource(strings = {"ABB", "ACR", "ABX", "ACX"})
    void shouldReturnTrueForAbbreviationTypes(String tagName) {
      LexicalTag tag = LexicalTag.valueOf(tagName);
      assertThat(tag.isAbbreviation()).as("%s 应被识别为缩写类型", tagName).isTrue();
    }

    @ParameterizedTest
    @DisplayName("非缩写类型应返回 false")
    @ValueSource(strings = {"NON", "PEF", "LAB", "NAM", "EPO", "TRD", "HIST"})
    void shouldReturnFalseForNonAbbreviationTypes(String tagName) {
      LexicalTag tag = LexicalTag.valueOf(tagName);
      assertThat(tag.isAbbreviation()).as("%s 不应被识别为缩写类型", tagName).isFalse();
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class EnumPropertiesTests {

    @ParameterizedTest(name = "{0} 的代码应为 \"{1}\"")
    @DisplayName("code 属性应返回正确值")
    @CsvSource({
      "NON, NON",
      "PEF, PEF",
      "LAB, LAB",
      "ABB, ABB",
      "ACR, ACR",
      "NAM, NAM",
      "ABX, ABX",
      "ACX, ACX",
      "EPO, EPO",
      "TRD, TRD",
      "HIST, HIST"
    })
    void shouldHaveCorrectCode(LexicalTag tag, String expectedCode) {
      assertThat(tag.getCode()).isEqualTo(expectedCode);
    }

    @Test
    @DisplayName("所有枚举值应有非空描述")
    void allEnumsShouldHaveDescriptions() {
      for (LexicalTag tag : LexicalTag.values()) {
        assertThat(tag.getDescription()).as("%s 应有英文描述", tag.name()).isNotBlank();
        assertThat(tag.getDescriptionZh()).as("%s 应有中文描述", tag.name()).isNotBlank();
      }
    }

    @Test
    @DisplayName("枚举应包含所有 11 个值")
    void shouldHaveAllValues() {
      assertThat(LexicalTag.values()).hasSize(11);
    }

    @ParameterizedTest(name = "{0} 的描述应为 \"{1}\"")
    @DisplayName("新增枚举值应有正确的描述")
    @CsvSource({
      "ABX, Embedded Abbreviation, 嵌入式缩写",
      "ACX, Embedded Acronym, 嵌入式首字母缩写",
      "EPO, Eponym, 人名术语",
      "TRD, Trade Name, 商标名",
      "HIST, Historical Term, 历史术语"
    })
    void newEnumsShouldHaveCorrectDescriptions(
        LexicalTag tag, String expectedDescription, String expectedDescriptionZh) {
      assertThat(tag.getDescription()).isEqualTo(expectedDescription);
      assertThat(tag.getDescriptionZh()).isEqualTo(expectedDescriptionZh);
    }
  }
}
