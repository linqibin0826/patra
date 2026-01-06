package com.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/// OrganizationNameType 枚举测试。
///
/// 基于 ROR Schema v2.0 的名称类型定义：ror_display, label, alias, acronym
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationNameType 枚举测试")
class OrganizationNameTypeTest {

  @Nested
  @DisplayName("枚举值验证")
  class EnumValuesTest {

    @Test
    @DisplayName("应包含 ROR 定义的 4 种名称类型")
    void shouldContainAllRorNameTypes() {
      assertThat(OrganizationNameType.values()).hasSize(4);
      assertThat(OrganizationNameType.values())
          .extracting(OrganizationNameType::name)
          .containsExactlyInAnyOrder("ROR_DISPLAY", "LABEL", "ALIAS", "ACRONYM");
    }

    @ParameterizedTest
    @CsvSource({
      "ROR_DISPLAY, ror_display, ROR 显示名",
      "LABEL, label, 官方标签",
      "ALIAS, alias, 别名",
      "ACRONYM, acronym, 缩写"
    })
    @DisplayName("每个枚举值应有正确的 code 和 description")
    void shouldHaveCorrectCodeAndDescription(
        String enumName, String expectedCode, String expectedDescription) {
      OrganizationNameType type = OrganizationNameType.valueOf(enumName);
      assertThat(type.getCode()).isEqualTo(expectedCode);
      assertThat(type.getDescription()).isEqualTo(expectedDescription);
    }
  }

  @Nested
  @DisplayName("fromCode() 方法测试")
  class FromCodeTest {

    @ParameterizedTest
    @CsvSource({
      "ror_display, ROR_DISPLAY",
      "ROR_DISPLAY, ROR_DISPLAY",
      "label, LABEL",
      "LABEL, LABEL",
      "alias, ALIAS",
      "acronym, ACRONYM"
    })
    @DisplayName("应支持大小写不敏感的代码解析")
    void shouldParseCodeCaseInsensitively(String code, String expectedEnum) {
      assertThat(OrganizationNameType.fromCode(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowExceptionForBlankValue(String code) {
      assertThatThrownBy(() -> OrganizationNameType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownCode() {
      assertThatThrownBy(() -> OrganizationNameType.fromCode("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的名称类型");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isDisplayName() 应正确识别显示名类型")
    void shouldIdentifyDisplayName() {
      assertThat(OrganizationNameType.ROR_DISPLAY.isDisplayName()).isTrue();
      assertThat(OrganizationNameType.LABEL.isDisplayName()).isFalse();
    }

    @Test
    @DisplayName("isOfficialName() 应正确识别官方名称")
    void shouldIdentifyOfficialName() {
      assertThat(OrganizationNameType.ROR_DISPLAY.isOfficialName()).isTrue();
      assertThat(OrganizationNameType.LABEL.isOfficialName()).isTrue();
      assertThat(OrganizationNameType.ALIAS.isOfficialName()).isFalse();
      assertThat(OrganizationNameType.ACRONYM.isOfficialName()).isFalse();
    }

    @Test
    @DisplayName("isAbbreviation() 应正确识别缩写类型")
    void shouldIdentifyAbbreviation() {
      assertThat(OrganizationNameType.ACRONYM.isAbbreviation()).isTrue();
      assertThat(OrganizationNameType.ROR_DISPLAY.isAbbreviation()).isFalse();
    }
  }
}
