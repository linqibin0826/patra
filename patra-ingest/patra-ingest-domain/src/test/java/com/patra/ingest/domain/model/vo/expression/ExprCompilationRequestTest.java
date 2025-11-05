package com.patra.ingest.domain.model.vo.expression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** ExprCompilationRequest 单元测试 */
@DisplayName("ExprCompilationRequest 值对象测试")
class ExprCompilationRequestTest {

  @Nested
  @DisplayName("完整构造器测试")
  class FullConstructorTest {

    @Test
    @DisplayName("应该创建完整的请求对象（包含所有字段）")
    void shouldCreateRequestWithAllFields() {
      // Given
      String provenanceCode = "PUBMED";
      String endpointName = "SEARCH";
      String rawExpression = "{\"query\": \"cancer AND treatment\"}";

      // When
      ExprCompilationRequest request =
          new ExprCompilationRequest(provenanceCode, endpointName, rawExpression);

      // Then
      assertThat(request.provenanceCode()).isEqualTo(provenanceCode);
      assertThat(request.endpointName()).isEqualTo(endpointName);
      assertThat(request.rawExpression()).isEqualTo(rawExpression);
    }

    @Test
    @DisplayName("应该允许 endpointName 为 null（全局配置场景）")
    void shouldAllowNullEndpointName() {
      // Given
      String provenanceCode = "EPMC";
      String rawExpression = "{\"type\": \"global\"}";

      // When
      ExprCompilationRequest request =
          new ExprCompilationRequest(provenanceCode, null, rawExpression);

      // Then
      assertThat(request.provenanceCode()).isEqualTo(provenanceCode);
      assertThat(request.endpointName()).isNull();
      assertThat(request.rawExpression()).isEqualTo(rawExpression);
    }

    @Test
    @DisplayName("应该允许空字符串的 endpointName")
    void shouldAllowEmptyEndpointName() {
      // Given
      String provenanceCode = "SCOPUS";
      String endpointName = "";
      String rawExpression = "{\"query\": \"test\"}";

      // When
      ExprCompilationRequest request =
          new ExprCompilationRequest(provenanceCode, endpointName, rawExpression);

      // Then
      assertThat(request.endpointName()).isEmpty();
    }
  }

  @Nested
  @DisplayName("便捷构造器测试")
  class ConvenienceConstructorTest {

    @Test
    @DisplayName("应该使用便捷构造器创建请求（endpointName 默认为 null）")
    void shouldCreateRequestWithDefaultNullEndpoint() {
      // Given
      String provenanceCode = "WOS";
      String rawExpression = "{\"filter\": \"year:2023\"}";

      // When
      ExprCompilationRequest request = new ExprCompilationRequest(provenanceCode, rawExpression);

      // Then
      assertThat(request.provenanceCode()).isEqualTo(provenanceCode);
      assertThat(request.endpointName()).isNull();
      assertThat(request.rawExpression()).isEqualTo(rawExpression);
    }

    @Test
    @DisplayName("应该与完整构造器（endpointName=null）创建相同对象")
    void shouldBeEquivalentToFullConstructorWithNullEndpoint() {
      // Given
      String provenanceCode = "EMBASE";
      String rawExpression = "{\"terms\": [\"diabetes\"]}";

      // When
      ExprCompilationRequest request1 = new ExprCompilationRequest(provenanceCode, rawExpression);
      ExprCompilationRequest request2 =
          new ExprCompilationRequest(provenanceCode, null, rawExpression);

      // Then
      assertThat(request1).isEqualTo(request2);
      assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }
  }

  @Nested
  @DisplayName("Record 特性测试")
  class RecordFeatureTest {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同内容视为相等）")
    void shouldImplementEqualsCorrectly() {
      // Given
      ExprCompilationRequest request1 =
          new ExprCompilationRequest("PUBMED", "DETAIL", "{\"id\": \"12345\"}");
      ExprCompilationRequest request2 =
          new ExprCompilationRequest("PUBMED", "DETAIL", "{\"id\": \"12345\"}");

      // When & Then
      assertThat(request1).isEqualTo(request2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同内容视为不相等）")
    void shouldNotEqualWhenContentDiffers() {
      // Given
      ExprCompilationRequest request1 =
          new ExprCompilationRequest("PUBMED", "SEARCH", "{\"query\": \"A\"}");
      ExprCompilationRequest request2 =
          new ExprCompilationRequest("EPMC", "SEARCH", "{\"query\": \"A\"}");

      // When & Then
      assertThat(request1).isNotEqualTo(request2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法（相同对象产生相同 hashCode）")
    void shouldImplementHashCodeCorrectly() {
      // Given
      ExprCompilationRequest request1 =
          new ExprCompilationRequest("SCOPUS", "FETCH", "{\"doi\": \"10.1000/xyz\"}");
      ExprCompilationRequest request2 =
          new ExprCompilationRequest("SCOPUS", "FETCH", "{\"doi\": \"10.1000/xyz\"}");

      // When & Then
      assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("应该生成包含所有字段的 toString 结果")
    void shouldGenerateToStringWithAllFields() {
      // Given
      ExprCompilationRequest request =
          new ExprCompilationRequest("WOS", "EXPORT", "{\"format\": \"bibtex\"}");

      // When
      String toString = request.toString();

      // Then
      assertThat(toString)
          .contains("ExprCompilationRequest")
          .contains("WOS")
          .contains("EXPORT")
          .contains("{\"format\": \"bibtex\"}");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTest {

    @Test
    @DisplayName("应该接受复杂 JSON 表达式")
    void shouldAcceptComplexJsonExpression() {
      // Given
      String complexJson =
          """
          {
            "boolean_operator": "AND",
            "conditions": [
              {"field": "title", "value": "cancer"},
              {"field": "year", "value": "2023"}
            ],
            "pagination": {"page": 1, "size": 50}
          }
          """;

      // When
      ExprCompilationRequest request = new ExprCompilationRequest("PUBMED", complexJson);

      // Then
      assertThat(request.rawExpression()).isEqualTo(complexJson);
    }

    @Test
    @DisplayName("应该接受空 JSON 对象")
    void shouldAcceptEmptyJson() {
      // Given
      String emptyJson = "{}";

      // When
      ExprCompilationRequest request = new ExprCompilationRequest("EPMC", "SEARCH", emptyJson);

      // Then
      assertThat(request.rawExpression()).isEqualTo(emptyJson);
    }

    @Test
    @DisplayName("应该允许 provenanceCode 和 rawExpression 为 null（虽然业务上不推荐）")
    void shouldAllowNullFields() {
      // When
      ExprCompilationRequest request = new ExprCompilationRequest(null, null, null);

      // Then
      assertThat(request.provenanceCode()).isNull();
      assertThat(request.endpointName()).isNull();
      assertThat(request.rawExpression()).isNull();
    }
  }
}
