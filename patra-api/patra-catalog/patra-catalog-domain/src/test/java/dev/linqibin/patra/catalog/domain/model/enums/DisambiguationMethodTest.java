package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// DisambiguationMethod 枚举单元测试。
///
/// 测试范围：
///
/// - 代码解析（fromCode、fromCodeOrNull）
/// - 业务判断（isIdentifierBased、isAutomatic、needsConfirmation）
/// - 属性访问（getCode、getDescription、getDefaultScore）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DisambiguationMethod 枚举测试")
class DisambiguationMethodTest {

  @Nested
  @DisplayName("fromCode 解析测试")
  class FromCodeTests {

    @Test
    @DisplayName("解析 ROR_ID 代码")
    void shouldParseRorIdCode() {
      assertThat(DisambiguationMethod.fromCode("ROR_ID")).isEqualTo(DisambiguationMethod.ROR_ID);
    }

    @Test
    @DisplayName("解析 RINGGOLD 代码")
    void shouldParseRinggoldCode() {
      assertThat(DisambiguationMethod.fromCode("RINGGOLD"))
          .isEqualTo(DisambiguationMethod.RINGGOLD);
    }

    @Test
    @DisplayName("解析 GRID 代码")
    void shouldParseGridCode() {
      assertThat(DisambiguationMethod.fromCode("GRID")).isEqualTo(DisambiguationMethod.GRID);
    }

    @Test
    @DisplayName("解析 NAME_MATCH 代码")
    void shouldParseNameMatchCode() {
      assertThat(DisambiguationMethod.fromCode("NAME_MATCH"))
          .isEqualTo(DisambiguationMethod.NAME_MATCH);
    }

    @Test
    @DisplayName("解析 MANUAL 代码")
    void shouldParseManualCode() {
      assertThat(DisambiguationMethod.fromCode("MANUAL")).isEqualTo(DisambiguationMethod.MANUAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ror_id", "Ror_Id", "ROR_ID", " ROR_ID ", "ror_id "})
    @DisplayName("解析应不区分大小写并忽略空格")
    void shouldParseCaseInsensitiveWithTrim(String input) {
      assertThat(DisambiguationMethod.fromCode(input)).isEqualTo(DisambiguationMethod.ROR_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowOnBlankValue(String input) {
      assertThatThrownBy(() -> DisambiguationMethod.fromCode(input))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowOnUnknownCode() {
      assertThatThrownBy(() -> DisambiguationMethod.fromCode("UNKNOWN"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的消歧方法");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull 安全解析测试")
  class FromCodeOrNullTests {

    @Test
    @DisplayName("解析有效代码应返回枚举值")
    void shouldReturnEnumForValidCode() {
      assertThat(DisambiguationMethod.fromCodeOrNull("ROR_ID"))
          .isEqualTo(DisambiguationMethod.ROR_ID);
      assertThat(DisambiguationMethod.fromCodeOrNull("RINGGOLD"))
          .isEqualTo(DisambiguationMethod.RINGGOLD);
      assertThat(DisambiguationMethod.fromCodeOrNull("GRID")).isEqualTo(DisambiguationMethod.GRID);
      assertThat(DisambiguationMethod.fromCodeOrNull("NAME_MATCH"))
          .isEqualTo(DisambiguationMethod.NAME_MATCH);
      assertThat(DisambiguationMethod.fromCodeOrNull("MANUAL"))
          .isEqualTo(DisambiguationMethod.MANUAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ror_id", "ringgold", "grid", "name_match", "manual"})
    @DisplayName("解析应不区分大小写")
    void shouldParseCaseInsensitive(String input) {
      assertThat(DisambiguationMethod.fromCodeOrNull(input)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("空白值应返回 null")
    void shouldReturnNullForBlankValue(String input) {
      assertThat(DisambiguationMethod.fromCodeOrNull(input)).isNull();
    }

    @Test
    @DisplayName("未知代码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(DisambiguationMethod.fromCodeOrNull("UNKNOWN")).isNull();
      assertThat(DisambiguationMethod.fromCodeOrNull("AI_MATCH")).isNull();
    }
  }

  @Nested
  @DisplayName("isIdentifierBased 方法测试")
  class IsIdentifierBasedTests {

    @Test
    @DisplayName("ROR_ID、RINGGOLD、GRID 应为基于标识符的方法")
    void shouldReturnTrueForIdentifierBasedMethods() {
      assertThat(DisambiguationMethod.ROR_ID.isIdentifierBased()).isTrue();
      assertThat(DisambiguationMethod.RINGGOLD.isIdentifierBased()).isTrue();
      assertThat(DisambiguationMethod.GRID.isIdentifierBased()).isTrue();
    }

    @Test
    @DisplayName("NAME_MATCH、MANUAL 应不是基于标识符的方法")
    void shouldReturnFalseForNonIdentifierBasedMethods() {
      assertThat(DisambiguationMethod.NAME_MATCH.isIdentifierBased()).isFalse();
      assertThat(DisambiguationMethod.MANUAL.isIdentifierBased()).isFalse();
    }
  }

  @Nested
  @DisplayName("isAutomatic 方法测试")
  class IsAutomaticTests {

    @Test
    @DisplayName("除 MANUAL 外所有方法应为自动匹配")
    void shouldReturnTrueForAutomaticMethods() {
      assertThat(DisambiguationMethod.ROR_ID.isAutomatic()).isTrue();
      assertThat(DisambiguationMethod.RINGGOLD.isAutomatic()).isTrue();
      assertThat(DisambiguationMethod.GRID.isAutomatic()).isTrue();
      assertThat(DisambiguationMethod.NAME_MATCH.isAutomatic()).isTrue();
    }

    @Test
    @DisplayName("MANUAL 应不是自动匹配")
    void shouldReturnFalseForManual() {
      assertThat(DisambiguationMethod.MANUAL.isAutomatic()).isFalse();
    }
  }

  @Nested
  @DisplayName("needsConfirmation 方法测试")
  class NeedsConfirmationTests {

    @Test
    @DisplayName("仅 NAME_MATCH 需要人工确认")
    void shouldReturnTrueOnlyForNameMatch() {
      assertThat(DisambiguationMethod.NAME_MATCH.needsConfirmation()).isTrue();
    }

    @Test
    @DisplayName("其他方法不需要人工确认")
    void shouldReturnFalseForOtherMethods() {
      assertThat(DisambiguationMethod.ROR_ID.needsConfirmation()).isFalse();
      assertThat(DisambiguationMethod.RINGGOLD.needsConfirmation()).isFalse();
      assertThat(DisambiguationMethod.GRID.needsConfirmation()).isFalse();
      assertThat(DisambiguationMethod.MANUAL.needsConfirmation()).isFalse();
    }
  }

  @Nested
  @DisplayName("属性访问测试")
  class PropertyAccessTests {

    @Test
    @DisplayName("获取代码值")
    void shouldReturnCorrectCode() {
      assertThat(DisambiguationMethod.ROR_ID.getCode()).isEqualTo("ROR_ID");
      assertThat(DisambiguationMethod.RINGGOLD.getCode()).isEqualTo("RINGGOLD");
      assertThat(DisambiguationMethod.GRID.getCode()).isEqualTo("GRID");
      assertThat(DisambiguationMethod.NAME_MATCH.getCode()).isEqualTo("NAME_MATCH");
      assertThat(DisambiguationMethod.MANUAL.getCode()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("获取描述")
    void shouldReturnCorrectDescription() {
      assertThat(DisambiguationMethod.ROR_ID.getDescription()).isEqualTo("ROR ID 匹配");
      assertThat(DisambiguationMethod.RINGGOLD.getDescription()).isEqualTo("Ringgold ID 匹配");
      assertThat(DisambiguationMethod.GRID.getDescription()).isEqualTo("GRID ID 匹配");
      assertThat(DisambiguationMethod.NAME_MATCH.getDescription()).isEqualTo("名称匹配");
      assertThat(DisambiguationMethod.MANUAL.getDescription()).isEqualTo("人工消歧");
    }

    @Test
    @DisplayName("获取默认置信度分数")
    void shouldReturnCorrectDefaultScore() {
      assertThat(DisambiguationMethod.ROR_ID.getDefaultScore())
          .isEqualByComparingTo(new BigDecimal("1.0000"));
      assertThat(DisambiguationMethod.RINGGOLD.getDefaultScore())
          .isEqualByComparingTo(new BigDecimal("0.9500"));
      assertThat(DisambiguationMethod.GRID.getDefaultScore())
          .isEqualByComparingTo(new BigDecimal("0.8500"));
      assertThat(DisambiguationMethod.NAME_MATCH.getDefaultScore())
          .isEqualByComparingTo(new BigDecimal("0.7000"));
      assertThat(DisambiguationMethod.MANUAL.getDefaultScore())
          .isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    @DisplayName("置信度分数应符合优先级顺序")
    void shouldHaveCorrectScorePriority() {
      // ROR_ID >= MANUAL > RINGGOLD > GRID > NAME_MATCH
      BigDecimal rorScore = DisambiguationMethod.ROR_ID.getDefaultScore();
      BigDecimal manualScore = DisambiguationMethod.MANUAL.getDefaultScore();
      BigDecimal ringgoldScore = DisambiguationMethod.RINGGOLD.getDefaultScore();
      BigDecimal gridScore = DisambiguationMethod.GRID.getDefaultScore();
      BigDecimal nameMatchScore = DisambiguationMethod.NAME_MATCH.getDefaultScore();

      assertThat(rorScore).isEqualByComparingTo(manualScore);
      assertThat(ringgoldScore).isLessThan(rorScore);
      assertThat(gridScore).isLessThan(ringgoldScore);
      assertThat(nameMatchScore).isLessThan(gridScore);
    }
  }
}
