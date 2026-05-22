package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// 字典类型枚举单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DictionaryType 字典类型枚举")
class DictionaryTypeTest {

  @Nested
  @DisplayName("枚举值属性")
  class EnumProperties {

    @ParameterizedTest(name = "{0} 应映射到 typeCode=\"{1}\"")
    @DisplayName("所有枚举值应正确映射 typeCode")
    @CsvSource({"COUNTRY, country", "LANGUAGE, language", "SUBJECT, subject"})
    void shouldMapTypeCodeCorrectly(DictionaryType type, String expectedCode) {
      assertThat(type.getTypeCode()).isEqualTo(expectedCode);
    }

    @Test
    @DisplayName("应包含所有预定义的字典类型")
    void shouldContainAllDictionaryTypes() {
      assertThat(DictionaryType.values()).hasSize(3);
    }
  }

  @Nested
  @DisplayName("fromTypeCode() 方法")
  class FromTypeCodeTests {

    @ParameterizedTest(name = "typeCode \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析所有有效 typeCode")
    @CsvSource({"country, COUNTRY", "language, LANGUAGE", "subject, SUBJECT"})
    void shouldParseValidTypeCodes(String typeCode, DictionaryType expected) {
      assertThat(DictionaryType.fromTypeCode(typeCode)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("空值或空白字符串应抛出异常")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldThrowForBlankTypeCode(String typeCode) {
      assertThatThrownBy(() -> DictionaryType.fromTypeCode(typeCode))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("字典类型代码不能为空");
    }

    @ParameterizedTest
    @DisplayName("未知 typeCode 应抛出异常")
    @ValueSource(strings = {"unknown", "COUNTRY", "invalid"})
    void shouldThrowForUnknownTypeCode(String typeCode) {
      assertThatThrownBy(() -> DictionaryType.fromTypeCode(typeCode))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的字典类型代码");
    }
  }

  @Nested
  @DisplayName("fromTypeCodeOrNull() 方法")
  class FromTypeCodeOrNullTests {

    @ParameterizedTest(name = "typeCode \"{0}\" 应解析为 {1}")
    @DisplayName("应正确解析有效 typeCode")
    @CsvSource({"country, COUNTRY", "language, LANGUAGE", "subject, SUBJECT"})
    void shouldParseValidTypeCodes(String typeCode, DictionaryType expected) {
      assertThat(DictionaryType.fromTypeCodeOrNull(typeCode)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("无效 typeCode 应返回 null")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "unknown", "COUNTRY"})
    void shouldReturnNullForInvalidTypeCodes(String typeCode) {
      assertThat(DictionaryType.fromTypeCodeOrNull(typeCode)).isNull();
    }
  }

  @Nested
  @DisplayName("类型判断方法")
  class TypeCheckTests {

    @Test
    @DisplayName("isCountry() 应正确判断")
    void shouldIdentifyCountry() {
      assertThat(DictionaryType.COUNTRY.isCountry()).isTrue();
      assertThat(DictionaryType.LANGUAGE.isCountry()).isFalse();
    }

    @Test
    @DisplayName("isLanguage() 应正确判断")
    void shouldIdentifyLanguage() {
      assertThat(DictionaryType.LANGUAGE.isLanguage()).isTrue();
      assertThat(DictionaryType.COUNTRY.isLanguage()).isFalse();
    }

    @Test
    @DisplayName("isSubject() 应正确判断")
    void shouldIdentifySubject() {
      assertThat(DictionaryType.SUBJECT.isSubject()).isTrue();
      assertThat(DictionaryType.COUNTRY.isSubject()).isFalse();
    }
  }
}
