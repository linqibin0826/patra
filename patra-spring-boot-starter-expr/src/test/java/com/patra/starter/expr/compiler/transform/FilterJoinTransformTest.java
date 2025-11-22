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

/// {@link FilterJoinTransform} 单元测试。
/// 
/// 测试策略：纯单元测试，Mock ProvenanceSnapshot 依赖。
/// 
/// @since 1.0.0
@DisplayName("FilterJoinTransform 单元测试")
class FilterJoinTransformTest {

  private FilterJoinTransform transform;
  private ProvenanceSnapshot mockSnapshot;

  @BeforeEach
  void setUp() {
    transform = new FilterJoinTransform();
    mockSnapshot = mock(ProvenanceSnapshot.class);
  }

  @Test
  @DisplayName("code_返回正确的转换代码")
  void code_shouldReturnCorrectCode() {
    // Act
    String code = transform.code();

    // Assert
    assertThat(code).isEqualTo("FILTER_JOIN");
  }

  @Test
  @DisplayName("apply_标准场景_正确转换过滤器连接")
  void apply_withStandardInput_shouldTransformCorrectly() {
    // Arrange
    String stdKey = "filter";
    String value = "from-pub-date:2022-01-01||until-pub-date:2022-12-31";
    String expected = "from-pub-date:2022-01-01,until-pub-date:2022-12-31";

    // Act
    String result = transform.apply(stdKey, value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_多种过滤器组合_正确转换")
  @CsvSource({
    "'key1:value1||key2:value2||key3:value3', 'key1:value1,key2:value2,key3:value3'",
    "'single:value', 'single:value'",
    "'a:1||b:2', 'a:1,b:2'",
    "'filter:2023-01-01||filter:2023-12-31||status:active', 'filter:2023-01-01,filter:2023-12-31,status:active'"
  })
  void apply_withVariousFilterCombinations_shouldTransformCorrectly(String input, String expected) {
    // Act
    String result = transform.apply("filter", input, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_null或空白值_原样返回")
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t", "\n", "   \t\n  "})
  void apply_withNullOrBlankValue_shouldReturnAsIs(String value) {
    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(value);
  }

  @Test
  @DisplayName("apply_包含空白段落_过滤空白段落")
  void apply_withBlankSegments_shouldFilterBlankSegments() {
    // Arrange
    String value = "key1:value1||||key2:value2";
    String expected = "key1:value1,key2:value2";

    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_所有段落为空白_返回空字符串")
  void apply_withAllBlankSegments_shouldReturnEmptyString() {
    // Arrange
    String value = "||||||";
    String expected = "";

    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_值中包含空格_保留空格")
  void apply_withSpacesInValue_shouldPreserveSpaces() {
    // Arrange
    String value = "key1:value with spaces||key2:another value";
    String expected = "key1:value with spaces,key2:another value";

    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_单个过滤器_不添加分隔符")
  void apply_withSingleFilter_shouldNotAddSeparator() {
    // Arrange
    String value = "single-filter:value";
    String expected = "single-filter:value";

    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_复杂日期过滤器_正确转换")
  void apply_withComplexDateFilters_shouldTransformCorrectly() {
    // Arrange
    String value =
        "from-pub-date:2022-01-01||until-pub-date:2022-12-31||from-update-date:2023-01-01||until-update-date:2023-12-31";
    String expected =
        "from-pub-date:2022-01-01,until-pub-date:2022-12-31,from-update-date:2023-01-01,until-update-date:2023-12-31";

    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_值包含特殊字符_正确处理")
  void apply_withSpecialCharacters_shouldHandleCorrectly() {
    // Arrange
    String value = "key1:value-with-dash||key2:value_with_underscore||key3:value.with.dots";
    String expected = "key1:value-with-dash,key2:value_with_underscore,key3:value.with.dots";

    // Act
    String result = transform.apply("filter", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }
}
