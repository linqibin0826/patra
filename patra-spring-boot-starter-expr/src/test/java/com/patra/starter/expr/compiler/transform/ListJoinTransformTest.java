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

/// {@link ListJoinTransform} 单元测试。
/// 
/// 测试策略：纯单元测试，Mock ProvenanceSnapshot 依赖。
/// 
/// @since 1.0.0
@DisplayName("ListJoinTransform 单元测试")
class ListJoinTransformTest {

  private ListJoinTransform transform;
  private ProvenanceSnapshot mockSnapshot;

  @BeforeEach
  void setUp() {
    transform = new ListJoinTransform();
    mockSnapshot = mock(ProvenanceSnapshot.class);
  }

  @Test
  @DisplayName("code_返回正确的转换代码")
  void code_shouldReturnCorrectCode() {
    // Act
    String code = transform.code();

    // Assert
    assertThat(code).isEqualTo("LIST_JOIN");
  }

  @Test
  @DisplayName("apply_标准场景_正确转换列表连接")
  void apply_withStandardInput_shouldTransformCorrectly() {
    // Arrange
    String stdKey = "authors";
    String value = "value1||value2||value3";
    String expected = "value1,value2,value3";

    // Act
    String result = transform.apply(stdKey, value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_多种列表组合_正确转换")
  @CsvSource({
    "'item1||item2||item3||item4', 'item1,item2,item3,item4'",
    "'single', 'single'",
    "'a||b', 'a,b'",
    "'author1||author2||author3||author4||author5', 'author1,author2,author3,author4,author5'"
  })
  void apply_withVariousListCombinations_shouldTransformCorrectly(String input, String expected) {
    // Act
    String result = transform.apply("list", input, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("apply_null或空白值_原样返回")
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t", "\n", "   \t\n  "})
  void apply_withNullOrBlankValue_shouldReturnAsIs(String value) {
    // Act
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(value);
  }

  @Test
  @DisplayName("apply_包含空白段落_过滤空白段落")
  void apply_withBlankSegments_shouldFilterBlankSegments() {
    // Arrange
    String value = "value1||||value2";
    String expected = "value1,value2";

    // Act
    String result = transform.apply("list", value, mockSnapshot);

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
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_值中包含空格_保留空格")
  void apply_withSpacesInValue_shouldPreserveSpaces() {
    // Arrange
    String value = "value with spaces||another value||third value";
    String expected = "value with spaces,another value,third value";

    // Act
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_单个值_不添加分隔符")
  void apply_withSingleValue_shouldNotAddSeparator() {
    // Arrange
    String value = "single-value";
    String expected = "single-value";

    // Act
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_数字列表_正确转换")
  void apply_withNumericList_shouldTransformCorrectly() {
    // Arrange
    String value = "123||456||789||012";
    String expected = "123,456,789,012";

    // Act
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_混合内容列表_正确转换")
  void apply_withMixedContentList_shouldTransformCorrectly() {
    // Arrange
    String value = "text||123||2023-01-01||special-char_value";
    String expected = "text,123,2023-01-01,special-char_value";

    // Act
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_值包含特殊字符_正确处理")
  void apply_withSpecialCharacters_shouldHandleCorrectly() {
    // Arrange
    String value = "value-with-dash||value_with_underscore||value.with.dots||value:with:colons";
    String expected = "value-with-dash,value_with_underscore,value.with.dots,value:with:colons";

    // Act
    String result = transform.apply("list", value, mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("apply_长列表_正确转换")
  void apply_withLongList_shouldTransformCorrectly() {
    // Arrange
    StringBuilder valueBuilder = new StringBuilder();
    StringBuilder expectedBuilder = new StringBuilder();
    for (int i = 1; i <= 20; i++) {
      if (i > 1) {
        valueBuilder.append("||");
        expectedBuilder.append(",");
      }
      valueBuilder.append("item").append(i);
      expectedBuilder.append("item").append(i);
    }

    // Act
    String result = transform.apply("list", valueBuilder.toString(), mockSnapshot);

    // Assert
    assertThat(result).isEqualTo(expectedBuilder.toString());
  }
}
