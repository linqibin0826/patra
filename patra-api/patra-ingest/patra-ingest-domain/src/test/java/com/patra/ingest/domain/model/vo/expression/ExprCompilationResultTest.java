package com.patra.ingest.domain.model.vo.expression;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ExprCompilationResult 单元测试 */
@DisplayName("ExprCompilationResult 值对象测试")
class ExprCompilationResultTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Nested
  @DisplayName("基础构造器测试")
  class BasicConstructorTest {

    @Test
    @DisplayName("应该创建完整的编译结果对象")
    void shouldCreateResultWithAllFields() {
      // Given
      String query = "cancer AND treatment";
      JsonNode params = createJsonNode("{\"retmax\": 100}");
      String normalizedExpression = "{\"type\": \"boolean_query\"}";
      String errors = null;
      String warnings = "Field 'sort' not specified";

      // When
      ExprCompilationResult result =
          new ExprCompilationResult(query, params, normalizedExpression, errors, warnings);

      // Then
      assertThat(result.query()).isEqualTo(query);
      assertThat(result.params()).isEqualTo(params);
      assertThat(result.normalizedExpression()).isEqualTo(normalizedExpression);
      assertThat(result.errors()).isNull();
      assertThat(result.warnings()).isEqualTo(warnings);
    }

    @Test
    @DisplayName("应该允许所有字段为 null")
    void shouldAllowAllNullFields() {
      // When
      ExprCompilationResult result = new ExprCompilationResult(null, null, null, null, null);

      // Then
      assertThat(result.query()).isNull();
      assertThat(result.params()).isNull();
      assertThat(result.normalizedExpression()).isNull();
      assertThat(result.errors()).isNull();
      assertThat(result.warnings()).isNull();
    }
  }

  @Nested
  @DisplayName("isValid 方法测试")
  class IsValidMethodTest {

    @Test
    @DisplayName("应该返回 true 当 errors 为 null")
    void shouldReturnTrueWhenErrorsNull() {
      // Given
      ExprCompilationResult result =
          new ExprCompilationResult("query", null, "normalized", null, "some warning");

      // When & Then
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("应该返回 true 当 errors 为空字符串")
    void shouldReturnTrueWhenErrorsEmpty() {
      // Given
      ExprCompilationResult result =
          new ExprCompilationResult("query", null, "normalized", "", "warning");

      // When & Then
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("应该返回 true 当 errors 为空白字符串")
    void shouldReturnTrueWhenErrorsBlank() {
      // Given
      ExprCompilationResult result =
          new ExprCompilationResult("query", null, "normalized", "   ", "warning");

      // When & Then
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("应该返回 false 当 errors 包含错误消息")
    void shouldReturnFalseWhenErrorsPresent() {
      // Given
      ExprCompilationResult result =
          new ExprCompilationResult(null, null, null, "Invalid syntax", null);

      // When & Then
      assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 errors 包含非空白内容")
    void shouldReturnFalseWhenErrorsNonBlank() {
      // Given
      ExprCompilationResult result =
          new ExprCompilationResult(null, null, null, "  error  ", null);

      // When & Then
      assertThat(result.isValid()).isFalse();
    }
  }

  @Nested
  @DisplayName("validationMessage 方法测试")
  class ValidationMessageMethodTest {

    @Test
    @DisplayName("应该返回 null 当 errors 和 warnings 都为 null")
    void shouldReturnNullWhenBothNull() {
      // Given
      ExprCompilationResult result = new ExprCompilationResult("query", null, "normalized", null, null);

      // When
      String message = result.validationMessage();

      // Then
      assertThat(message).isNull();
    }

    @Test
    @DisplayName("应该返回 null 当 errors 和 warnings 都为空")
    void shouldReturnNullWhenBothEmpty() {
      // Given
      ExprCompilationResult result = new ExprCompilationResult("query", null, "normalized", "", "");

      // When
      String message = result.validationMessage();

      // Then
      assertThat(message).isNull();
    }

    @Test
    @DisplayName("应该只返回 errors 当 warnings 为空")
    void shouldReturnErrorsOnlyWhenWarningsEmpty() {
      // Given
      String errors = "Missing required field 'query'";
      ExprCompilationResult result = new ExprCompilationResult(null, null, null, errors, null);

      // When
      String message = result.validationMessage();

      // Then
      assertThat(message).isEqualTo(errors);
    }

    @Test
    @DisplayName("应该返回组合消息当 errors 和 warnings 都存在")
    void shouldReturnCombinedMessageWhenBothPresent() {
      // Given
      String errors = "Syntax error at line 5";
      String warnings = "Deprecated field 'old_param' used";
      ExprCompilationResult result =
          new ExprCompilationResult(null, null, null, errors, warnings);

      // When
      String message = result.validationMessage();

      // Then
      assertThat(message)
          .contains("Errors: " + errors)
          .contains("Warnings: " + warnings)
          .contains(";");
    }

    @Test
    @DisplayName("应该只返回 warnings 当 errors 为空")
    void shouldReturnWarningsOnlyWhenErrorsEmpty() {
      // Given
      String warnings = "Optional field 'retmax' not set, using default";
      ExprCompilationResult result = new ExprCompilationResult("query", null, "normalized", null, warnings);

      // When
      String message = result.validationMessage();

      // Then
      assertThat(message).isEqualTo("Warnings: " + warnings);
    }

    @Test
    @DisplayName("应该返回 null 当 warnings 为空白字符串")
    void shouldReturnNullWhenWarningsBlank() {
      // Given
      ExprCompilationResult result = new ExprCompilationResult("query", null, "normalized", null, "  ");

      // When
      String message = result.validationMessage();

      // Then
      assertThat(message).isNull();
    }
  }

  @Nested
  @DisplayName("success 工厂方法测试")
  class SuccessFactoryMethodTest {

    @Test
    @DisplayName("应该创建成功的编译结果（包含 warnings）")
    void shouldCreateSuccessResultWithWarnings() {
      // Given
      String query = "diabetes OR hypertension";
      JsonNode params = createJsonNode("{\"page\": 1, \"size\": 20}");
      String normalizedExpression = "{\"operator\": \"OR\"}";
      String warnings = "Using default sort order";

      // When
      ExprCompilationResult result =
          ExprCompilationResult.success(query, params, normalizedExpression, warnings);

      // Then
      assertThat(result.query()).isEqualTo(query);
      assertThat(result.params()).isEqualTo(params);
      assertThat(result.normalizedExpression()).isEqualTo(normalizedExpression);
      assertThat(result.errors()).isNull();
      assertThat(result.warnings()).isEqualTo(warnings);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("应该创建成功的编译结果（无 warnings）")
    void shouldCreateSuccessResultWithoutWarnings() {
      // Given
      String query = "cancer";
      JsonNode params = createJsonNode("{\"retmax\": 50}");
      String normalizedExpression = "{\"simple_query\": true}";

      // When
      ExprCompilationResult result =
          ExprCompilationResult.success(query, params, normalizedExpression, null);

      // Then
      assertThat(result.query()).isEqualTo(query);
      assertThat(result.params()).isEqualTo(params);
      assertThat(result.normalizedExpression()).isEqualTo(normalizedExpression);
      assertThat(result.errors()).isNull();
      assertThat(result.warnings()).isNull();
      assertThat(result.isValid()).isTrue();
      assertThat(result.validationMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("failure 工厂方法测试")
  class FailureFactoryMethodTest {

    @Test
    @DisplayName("应该创建失败的编译结果")
    void shouldCreateFailureResult() {
      // Given
      String errors = "Compilation failed: unknown operator 'XOR'";

      // When
      ExprCompilationResult result = ExprCompilationResult.failure(errors);

      // Then
      assertThat(result.query()).isNull();
      assertThat(result.params()).isNull();
      assertThat(result.normalizedExpression()).isNull();
      assertThat(result.errors()).isEqualTo(errors);
      assertThat(result.warnings()).isNull();
      assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("应该创建失败结果，validationMessage 返回错误信息")
    void shouldReturnErrorsInValidationMessage() {
      // Given
      String errors = "Missing required field: 'boolean_operator'";

      // When
      ExprCompilationResult result = ExprCompilationResult.failure(errors);

      // Then
      assertThat(result.validationMessage()).isEqualTo(errors);
    }
  }

  @Nested
  @DisplayName("Record 特性测试")
  class RecordFeatureTest {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同内容视为相等）")
    void shouldImplementEqualsCorrectly() {
      // Given
      JsonNode params1 = createJsonNode("{\"retmax\": 10}");
      JsonNode params2 = createJsonNode("{\"retmax\": 10}");
      ExprCompilationResult result1 =
          new ExprCompilationResult("query", params1, "normalized", null, "warning");
      ExprCompilationResult result2 =
          new ExprCompilationResult("query", params2, "normalized", null, "warning");

      // When & Then
      assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同内容视为不相等）")
    void shouldNotEqualWhenContentDiffers() {
      // Given
      ExprCompilationResult result1 =
          ExprCompilationResult.success("query1", null, "normalized1", null);
      ExprCompilationResult result2 =
          ExprCompilationResult.success("query2", null, "normalized2", null);

      // When & Then
      assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given
      JsonNode params1 = createJsonNode("{\"page\": 1}");
      JsonNode params2 = createJsonNode("{\"page\": 1}");
      ExprCompilationResult result1 =
          new ExprCompilationResult("query", params1, "normalized", null, null);
      ExprCompilationResult result2 =
          new ExprCompilationResult("query", params2, "normalized", null, null);

      // When & Then
      assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("应该生成包含所有字段的 toString 结果")
    void shouldGenerateToStringWithAllFields() {
      // Given
      ExprCompilationResult result =
          new ExprCompilationResult(
              "test query", null, "normalized expression", "error msg", "warning msg");

      // When
      String toString = result.toString();

      // Then
      assertThat(toString)
          .contains("ExprCompilationResult")
          .contains("test query")
          .contains("normalized expression")
          .contains("error msg")
          .contains("warning msg");
    }
  }

  @Nested
  @DisplayName("业务场景集成测试")
  class BusinessScenarioTest {

    @Test
    @DisplayName("应该正确表示成功的 PubMed 查询编译")
    void shouldRepresentSuccessfulPubMedCompilation() {
      // Given
      String query = "(cancer[Title]) AND (2023[Publication Date])";
      JsonNode params = createJsonNode("{\"retmax\": 100, \"sort\": \"relevance\"}");
      String normalized = "{\"fields\": [\"Title\"], \"year\": 2023}";

      // When
      ExprCompilationResult result = ExprCompilationResult.success(query, params, normalized, null);

      // Then
      assertThat(result.isValid()).isTrue();
      assertThat(result.query()).contains("cancer", "2023");
      assertThat(result.params().get("retmax").asInt()).isEqualTo(100);
      assertThat(result.validationMessage()).isNull();
    }

    @Test
    @DisplayName("应该正确表示带警告的 EPMC 查询编译")
    void shouldRepresentEpmcCompilationWithWarnings() {
      // Given
      String query = "diabetes";
      JsonNode params = createJsonNode("{\"pageSize\": 50}");
      String normalized = "{\"simple_term\": \"diabetes\"}";
      String warnings = "Pagination parameter 'cursorMark' not set, results may be incomplete";

      // When
      ExprCompilationResult result =
          ExprCompilationResult.success(query, params, normalized, warnings);

      // Then
      assertThat(result.isValid()).isTrue();
      assertThat(result.validationMessage()).contains("Warnings").contains("cursorMark");
    }

    @Test
    @DisplayName("应该正确表示编译失败场景")
    void shouldRepresentCompilationFailure() {
      // Given
      String errors = "Unsupported boolean operator: 'XOR' at position 15";

      // When
      ExprCompilationResult result = ExprCompilationResult.failure(errors);

      // Then
      assertThat(result.isValid()).isFalse();
      assertThat(result.query()).isNull();
      assertThat(result.params()).isNull();
      assertThat(result.validationMessage()).isEqualTo(errors);
    }
  }

  /**
   * 辅助方法：从 JSON 字符串创建 JsonNode
   *
   * @param json JSON 字符串
   * @return JsonNode 对象
   */
  private static JsonNode createJsonNode(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      // 测试中如果 JSON 解析失败，使用空对象节点
      return JsonNodeFactory.instance.objectNode();
    }
  }
}
