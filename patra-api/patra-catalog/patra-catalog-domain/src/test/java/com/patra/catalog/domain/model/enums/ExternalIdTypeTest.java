package com.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/// ExternalIdType 枚举测试。
///
/// 基于 ROR Schema v2.0 的外部标识符类型定义：grid, isni, wikidata, fundref
/// 扩展 ringgold 以支持更多场景。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExternalIdType 枚举测试")
class ExternalIdTypeTest {

  @Nested
  @DisplayName("枚举值验证")
  class EnumValuesTest {

    @Test
    @DisplayName("应包含 5 种外部标识符类型")
    void shouldContainAllExternalIdTypes() {
      assertThat(ExternalIdType.values()).hasSize(5);
      assertThat(ExternalIdType.values())
          .extracting(ExternalIdType::name)
          .containsExactlyInAnyOrder("GRID", "ISNI", "WIKIDATA", "FUNDREF", "RINGGOLD");
    }

    @ParameterizedTest
    @CsvSource({
      "GRID, grid, GRID ID",
      "ISNI, isni, ISNI",
      "WIKIDATA, wikidata, Wikidata QID",
      "FUNDREF, fundref, FundRef ID",
      "RINGGOLD, ringgold, Ringgold ID"
    })
    @DisplayName("每个枚举值应有正确的 code 和 description")
    void shouldHaveCorrectCodeAndDescription(
        String enumName, String expectedCode, String expectedDescription) {
      ExternalIdType type = ExternalIdType.valueOf(enumName);
      assertThat(type.getCode()).isEqualTo(expectedCode);
      assertThat(type.getDescription()).isEqualTo(expectedDescription);
    }
  }

  @Nested
  @DisplayName("fromCode() 方法测试")
  class FromCodeTest {

    @ParameterizedTest
    @CsvSource({
      "grid, GRID",
      "GRID, GRID",
      "isni, ISNI",
      "ISNI, ISNI",
      "wikidata, WIKIDATA",
      "fundref, FUNDREF",
      "ringgold, RINGGOLD"
    })
    @DisplayName("应支持大小写不敏感的代码解析")
    void shouldParseCodeCaseInsensitively(String code, String expectedEnum) {
      assertThat(ExternalIdType.fromCode(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowExceptionForBlankValue(String code) {
      assertThatThrownBy(() -> ExternalIdType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownCode() {
      assertThatThrownBy(() -> ExternalIdType.fromCode("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的外部标识符类型");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull() 方法测试")
  class FromCodeOrNullTest {

    @ParameterizedTest
    @CsvSource({"grid, GRID", "GRID, GRID", "isni, ISNI", "wikidata, WIKIDATA"})
    @DisplayName("有效代码应返回对应枚举")
    void shouldReturnEnumForValidCode(String code, String expectedEnum) {
      assertThat(ExternalIdType.fromCodeOrNull(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null 或空字符串应返回 null")
    void shouldReturnNullForNullOrEmpty(String code) {
      assertThat(ExternalIdType.fromCodeOrNull(code)).isNull();
    }

    @Test
    @DisplayName("空白字符串应返回 null")
    void shouldReturnNullForBlank() {
      assertThat(ExternalIdType.fromCodeOrNull("  ")).isNull();
    }

    @Test
    @DisplayName("未知代码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(ExternalIdType.fromCodeOrNull("unknown")).isNull();
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isRorNative() 应正确识别 ROR 原生支持的标识符")
    void shouldIdentifyRorNative() {
      assertThat(ExternalIdType.GRID.isRorNative()).isTrue();
      assertThat(ExternalIdType.ISNI.isRorNative()).isTrue();
      assertThat(ExternalIdType.WIKIDATA.isRorNative()).isTrue();
      assertThat(ExternalIdType.FUNDREF.isRorNative()).isTrue();
      assertThat(ExternalIdType.RINGGOLD.isRorNative()).isFalse();
    }

    @Test
    @DisplayName("isDeprecated() 应正确识别已废弃的标识符")
    void shouldIdentifyDeprecated() {
      assertThat(ExternalIdType.GRID.isDeprecated()).isTrue();
      assertThat(ExternalIdType.ISNI.isDeprecated()).isFalse();
    }

    @Test
    @DisplayName("isFunderRelated() 应正确识别资助相关标识符")
    void shouldIdentifyFunderRelated() {
      assertThat(ExternalIdType.FUNDREF.isFunderRelated()).isTrue();
      assertThat(ExternalIdType.GRID.isFunderRelated()).isFalse();
    }
  }
}
