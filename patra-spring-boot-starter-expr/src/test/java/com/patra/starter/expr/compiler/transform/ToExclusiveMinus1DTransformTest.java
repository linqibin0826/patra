package com.patra.starter.expr.compiler.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ToExclusiveMinus1DTransform} 单元测试。
 *
 * <p>测试策略：纯单元测试，Mock ProvenanceSnapshot 依赖，重点测试日期转换逻辑和边界条件。
 *
 * @since 1.0.0
 */
@DisplayName("ToExclusiveMinus1DTransform 单元测试")
class ToExclusiveMinus1DTransformTest {

  private ToExclusiveMinus1DTransform transform;
  private ProvenanceSnapshot mockSnapshot;

  @BeforeEach
  void setUp() {
    transform = new ToExclusiveMinus1DTransform();
    mockSnapshot = mock(ProvenanceSnapshot.class);
  }

  @Test
  @DisplayName("code_返回正确的转换代码")
  void code_shouldReturnCorrectCode() {
    // Act
    String code = transform.code();

    // Assert
    assertThat(code).isEqualTo("TO_EXCLUSIVE_MINUS_1D");
  }

  @Test
  @DisplayName("apply_标准日期_正确减去一天")
  void apply_withStandardDate_shouldSubtractOneDay() {
    // Arrange
    String stdKey = "to";
    String value = "2023-12-31";
    String expected = "2023-12-30";

    // Act
    String result = transform.apply(stdKey, value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_多种日期_正确减去一天")
  @CsvSource({
    "'2023-01-15', '2023-01-14'",
    "'2023-02-01', '2023-01-31'",
    "'2023-03-01', '2023-02-28'",
    "'2024-03-01', '2024-02-29'", // 闰年
    "'2023-12-31', '2023-12-30'",
    "'2024-01-01', '2023-12-31'" // 跨年
  })
  void apply_withVariousDates_shouldSubtractOneDay(String input, String expected) {
    // Act
    String result = transform.apply("to", input, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_null或空白值_原样返回")
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t", "\n", "   \t\n  "})
  void apply_withNullOrBlankValue_shouldReturnAsIs(String value) {
    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(value);
  }

  @Test
  @DisplayName("apply_月初日期_正确跨月减一天")
  void apply_withFirstDayOfMonth_shouldHandleMonthBoundary() {
    // Arrange
    String value = "2023-05-01";
    String expected = "2023-04-30";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_年初日期_正确跨年减一天")
  void apply_withFirstDayOfYear_shouldHandleYearBoundary() {
    // Arrange
    String value = "2023-01-01";
    String expected = "2022-12-31";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_闰年2月29日_正确减一天")
  void apply_withLeapYearFeb29_shouldSubtractOneDay() {
    // Arrange
    String value = "2024-02-29";
    String expected = "2024-02-28";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_闰年3月1日_正确减到2月29日")
  void apply_withLeapYearMarch1_shouldSubtractToFeb29() {
    // Arrange
    String value = "2024-03-01";
    String expected = "2024-02-29";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_平年3月1日_正确减到2月28日")
  void apply_withNonLeapYearMarch1_shouldSubtractToFeb28() {
    // Arrange
    String value = "2023-03-01";
    String expected = "2023-02-28";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_无效日期格式_原样返回")
  @ValueSource(
      strings = {
        "2023/12/31", // 错误的分隔符
        "31-12-2023", // 错误的顺序
        "2023-13-01", // 无效月份
        "2023-12-32", // 无效日期
        "2023-02-30", // 2月不存在30日
        "not-a-date", // 完全无效
        "2023", // 只有年份
        "2023-12", // 缺少日期
        "12-31" // 缺少年份
      })
  void apply_withInvalidDateFormat_shouldReturnOriginalValue(String invalidDate) {
    // Act
    String result = transform.apply("to", invalidDate, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(invalidDate);
  }

  @Test
  @DisplayName("apply_日期包含时间部分_原样返回")
  void apply_withDateTimeValue_shouldReturnOriginalValue() {
    // Arrange
    String value = "2023-12-31T23:59:59";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(value);
  }

  @Test
  @DisplayName("apply_历史日期_正确减一天")
  void apply_withHistoricalDate_shouldSubtractOneDay() {
    // Arrange
    String value = "1900-01-01";
    String expected = "1899-12-31";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_未来日期_正确减一天")
  void apply_withFutureDate_shouldSubtractOneDay() {
    // Arrange
    String value = "2100-12-31";
    String expected = "2100-12-30";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_小月月底_正确减一天")
  void apply_withSmallMonthEnd_shouldSubtractOneDay() {
    // Arrange - 4月30日
    String value = "2023-04-30";
    String expected = "2023-04-29";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_大月月底_正确减一天")
  void apply_withBigMonthEnd_shouldSubtractOneDay() {
    // Arrange - 1月31日
    String value = "2023-01-31";
    String expected = "2023-01-30";

    // Act
    String result = transform.apply("to", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }
}
