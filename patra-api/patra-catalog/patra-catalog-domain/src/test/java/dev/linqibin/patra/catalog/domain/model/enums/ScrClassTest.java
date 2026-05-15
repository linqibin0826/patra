package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// ScrClass 枚举单元测试。
///
/// 测试 SCR（Supplementary Concept Record）类别枚举的所有功能。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScrClass 枚举测试")
class ScrClassTest {

  @ParameterizedTest
  @CsvSource({
    "1, CHEMICAL, 化学物质",
    "2, PROTOCOL, 化疗方案",
    "3, DISEASE, 疾病",
    "4, ORGANISM, 生物体",
    "5, POPULATION_GROUP, 人群组",
    "6, ANATOMY, 解剖结构"
  })
  @DisplayName("每个类别应该有正确的 code 和 displayName")
  void eachClassShouldHaveCorrectProperties(int code, String enumName, String displayName) {
    // when
    ScrClass scrClass = ScrClass.valueOf(enumName);

    // then
    assertThat(scrClass.getCode()).isEqualTo(code);
    assertThat(scrClass.getDisplayName()).isEqualTo(displayName);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6})
  @DisplayName("fromCode 应该正确解析所有有效值")
  void fromCodeShouldParseAllValidValues(int code) {
    // when
    ScrClass result = ScrClass.fromCode(code);

    // then
    assertThat(result.getCode()).isEqualTo(code);
  }

  @Test
  @DisplayName("fromCode 应该拒绝无效 code")
  void fromCodeShouldRejectInvalid() {
    assertThatThrownBy(() -> ScrClass.fromCode(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未知的 SCR 类别");

    assertThatThrownBy(() -> ScrClass.fromCode(7))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未知的 SCR 类别");
  }

  @Test
  @DisplayName("CHEMICAL 应该是默认类别（code=1）")
  void chemicalShouldBeDefault() {
    assertThat(ScrClass.CHEMICAL.getCode()).isEqualTo(1);
  }

  @Test
  @DisplayName("fromCodeOrDefault 应该返回 CHEMICAL 作为默认值")
  void fromCodeOrDefaultShouldReturnChemical() {
    // when
    ScrClass result = ScrClass.fromCodeOrDefault(null);

    // then
    assertThat(result).isEqualTo(ScrClass.CHEMICAL);
  }

  @Test
  @DisplayName("fromCodeOrDefault 应该正确解析有效 code")
  void fromCodeOrDefaultShouldParseValid() {
    // when
    ScrClass result = ScrClass.fromCodeOrDefault(3);

    // then
    assertThat(result).isEqualTo(ScrClass.DISEASE);
  }

  @ParameterizedTest
  @CsvSource({
    "1, CHEMICAL",
    "2, PROTOCOL",
    "3, DISEASE",
    "4, ORGANISM",
    "5, POPULATION_GROUP",
    "6, ANATOMY"
  })
  @DisplayName("fromCodeString 应该正确解析字符串格式的 code")
  void fromCodeStringShouldParseAllValues(String codeStr, String enumName) {
    // when
    ScrClass result = ScrClass.fromCodeString(codeStr);

    // then
    assertThat(result).isEqualTo(ScrClass.valueOf(enumName));
  }

  @Test
  @DisplayName("fromCodeString 应该拒绝无效字符串")
  void fromCodeStringShouldRejectInvalid() {
    assertThatThrownBy(() -> ScrClass.fromCodeString("abc"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> ScrClass.fromCodeString("0"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
