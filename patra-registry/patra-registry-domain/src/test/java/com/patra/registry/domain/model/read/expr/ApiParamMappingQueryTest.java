package com.patra.registry.domain.model.read.expr;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// ApiParamMappingQuery 查询视图测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ApiParamMappingQuery 查询视图测试")
class ApiParamMappingQueryTest {

  private static final Long VALID_PROVENANCE_ID = 1L;
  private static final String VALID_OPERATION_TYPE = "search";
  private static final String VALID_ENDPOINT_NAME = "articles";
  private static final String VALID_STD_KEY = "author";
  private static final String VALID_PROVIDER_PARAM_NAME = "creator";
  private static final String VALID_TRANSFORM_CODE = "uppercase";
  private static final String VALID_NOTES_JSON = "{\"note\":\"test\"}";
  private static final Instant VALID_EFFECTIVE_FROM = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant VALID_EFFECTIVE_TO = Instant.parse("2024-12-31T23:59:59Z");

  @Nested
  @DisplayName("创建验证")
  class Creation {

    @Test
    @DisplayName("应该使用所有有效字段创建查询视图")
    void shouldCreateWithAllValidFields() {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.endpointName()).isEqualTo(VALID_ENDPOINT_NAME);
      assertThat(query.stdKey()).isEqualTo(VALID_STD_KEY);
      assertThat(query.providerParamName()).isEqualTo(VALID_PROVIDER_PARAM_NAME);
      assertThat(query.transformCode()).isEqualTo(VALID_TRANSFORM_CODE);
      assertThat(query.notesJson()).isEqualTo(VALID_NOTES_JSON);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
    }

    @Test
    @DisplayName("应该创建仅包含必填字段的查询视图")
    void shouldCreateWithOnlyRequiredFields() {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              null, // operationType 可选
              null, // endpointName 可选
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              null, // transformCode 可选
              null, // notesJson 可选
              VALID_EFFECTIVE_FROM,
              null // effectiveTo 可选
              );

      // Assert
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isNull();
      assertThat(query.endpointName()).isNull();
      assertThat(query.stdKey()).isEqualTo(VALID_STD_KEY);
      assertThat(query.providerParamName()).isEqualTo(VALID_PROVIDER_PARAM_NAME);
      assertThat(query.transformCode()).isNull();
      assertThat(query.notesJson()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isNull();
    }
  }

  @Nested
  @DisplayName("Provenance ID 验证")
  class ProvenanceIdValidation {

    @Test
    @DisplayName("应该拒绝 null 的 Provenance ID")
    void shouldRejectNullProvenanceId() {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  new ApiParamMappingQuery(
                      null,
                      VALID_OPERATION_TYPE,
                      VALID_ENDPOINT_NAME,
                      VALID_STD_KEY,
                      VALID_PROVIDER_PARAM_NAME,
                      VALID_TRANSFORM_CODE,
                      VALID_NOTES_JSON,
                      VALID_EFFECTIVE_FROM,
                      VALID_EFFECTIVE_TO))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @ParameterizedTest
    @DisplayName("应该拒绝非正数的 Provenance ID")
    @ValueSource(longs = {0L, -1L, -100L})
    void shouldRejectNonPositiveProvenanceId(Long invalidId) {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  new ApiParamMappingQuery(
                      invalidId,
                      VALID_OPERATION_TYPE,
                      VALID_ENDPOINT_NAME,
                      VALID_STD_KEY,
                      VALID_PROVIDER_PARAM_NAME,
                      VALID_TRANSFORM_CODE,
                      VALID_NOTES_JSON,
                      VALID_EFFECTIVE_FROM,
                      VALID_EFFECTIVE_TO))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }
  }

  @Nested
  @DisplayName("Standard Key 验证")
  class StandardKeyValidation {

    @ParameterizedTest
    @DisplayName("应该拒绝空白的 Standard Key")
    @NullSource
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    void shouldRejectBlankStandardKey(String blankKey) {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  new ApiParamMappingQuery(
                      VALID_PROVENANCE_ID,
                      VALID_OPERATION_TYPE,
                      VALID_ENDPOINT_NAME,
                      blankKey,
                      VALID_PROVIDER_PARAM_NAME,
                      VALID_TRANSFORM_CODE,
                      VALID_NOTES_JSON,
                      VALID_EFFECTIVE_FROM,
                      VALID_EFFECTIVE_TO))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Standard key")
          .hasMessageContaining("不能为空白");
    }

    @ParameterizedTest
    @DisplayName("应该自动 trim Standard Key 的空白")
    @ValueSource(strings = {" author", "author ", " author ", "\tauthor\n"})
    void shouldTrimStandardKey(String keyWithWhitespace) {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              keyWithWhitespace,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.stdKey()).isEqualTo("author");
    }
  }

  @Nested
  @DisplayName("Provider Param Name 验证")
  class ProviderParamNameValidation {

    @ParameterizedTest
    @DisplayName("应该拒绝空白的 Provider Param Name")
    @NullSource
    @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
    void shouldRejectBlankProviderParamName(String blankName) {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  new ApiParamMappingQuery(
                      VALID_PROVENANCE_ID,
                      VALID_OPERATION_TYPE,
                      VALID_ENDPOINT_NAME,
                      VALID_STD_KEY,
                      blankName,
                      VALID_TRANSFORM_CODE,
                      VALID_NOTES_JSON,
                      VALID_EFFECTIVE_FROM,
                      VALID_EFFECTIVE_TO))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provider param name")
          .hasMessageContaining("不能为空白");
    }

    @ParameterizedTest
    @DisplayName("应该自动 trim Provider Param Name 的空白")
    @ValueSource(strings = {" creator", "creator ", " creator ", "\tcreator\n"})
    void shouldTrimProviderParamName(String nameWithWhitespace) {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              nameWithWhitespace,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.providerParamName()).isEqualTo("creator");
    }
  }

  @Nested
  @DisplayName("Effective From 验证")
  class EffectiveFromValidation {

    @Test
    @DisplayName("应该拒绝 null 的 Effective From")
    void shouldRejectNullEffectiveFrom() {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  new ApiParamMappingQuery(
                      VALID_PROVENANCE_ID,
                      VALID_OPERATION_TYPE,
                      VALID_ENDPOINT_NAME,
                      VALID_STD_KEY,
                      VALID_PROVIDER_PARAM_NAME,
                      VALID_TRANSFORM_CODE,
                      VALID_NOTES_JSON,
                      null,
                      VALID_EFFECTIVE_TO))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }
  }

  @Nested
  @DisplayName("可选字段 Trim 行为")
  class OptionalFieldsTrim {

    @ParameterizedTest
    @DisplayName("应该自动 trim Operation Type")
    @ValueSource(strings = {" search", "search ", " search ", "\tsearch\n"})
    void shouldTrimOperationType(String valueWithWhitespace) {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              valueWithWhitespace,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.operationType()).isEqualTo("search");
    }

    @ParameterizedTest
    @DisplayName("应该自动 trim Endpoint Name")
    @ValueSource(strings = {" articles", "articles ", " articles ", "\tarticles\n"})
    void shouldTrimEndpointName(String valueWithWhitespace) {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              valueWithWhitespace,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.endpointName()).isEqualTo("articles");
    }

    @ParameterizedTest
    @DisplayName("应该自动 trim Transform Code")
    @ValueSource(strings = {" uppercase", "uppercase ", " uppercase ", "\tuppercase\n"})
    void shouldTrimTransformCode(String valueWithWhitespace) {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              valueWithWhitespace,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.transformCode()).isEqualTo("uppercase");
    }

    @Test
    @DisplayName("应该接受 null 的可选字段")
    void shouldAcceptNullOptionalFields() {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              null,
              null,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              null,
              null,
              VALID_EFFECTIVE_FROM,
              null);

      // Assert
      assertThat(query.operationType()).isNull();
      assertThat(query.endpointName()).isNull();
      assertThat(query.transformCode()).isNull();
      assertThat(query.notesJson()).isNull();
      assertThat(query.effectiveTo()).isNull();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemantics {

    @Test
    @DisplayName("相同值的两个实例应该相等")
    void shouldBeEqualForSameValues() {
      // Arrange
      ApiParamMappingQuery query1 =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      ApiParamMappingQuery query2 =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同 Provenance ID 的实例不应该相等")
    void shouldNotBeEqualForDifferentProvenanceId() {
      // Arrange
      ApiParamMappingQuery query1 =
          new ApiParamMappingQuery(
              1L,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      ApiParamMappingQuery query2 =
          new ApiParamMappingQuery(
              2L,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("不同 Standard Key 的实例不应该相等")
    void shouldNotBeEqualForDifferentStdKey() {
      // Arrange
      ApiParamMappingQuery query1 =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              "author",
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      ApiParamMappingQuery query2 =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              "title",
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("toString 应该包含所有字段信息")
    void shouldIncludeAllFieldsInToString() {
      // Arrange
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Act
      String toString = query.toString();

      // Assert
      assertThat(toString)
          .contains("ApiParamMappingQuery")
          .contains("provenanceId=1")
          .contains("operationType=search")
          .contains("endpointName=articles")
          .contains("stdKey=author")
          .contains("providerParamName=creator")
          .contains("transformCode=uppercase")
          .contains("notesJson={\"note\":\"test\"}")
          .contains("effectiveFrom=2024-01-01T00:00:00Z")
          .contains("effectiveTo=2024-12-31T23:59:59Z");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditions {

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Arrange
      String longString = "a".repeat(1000);

      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              longString,
              longString,
              longString,
              longString,
              longString,
              longString,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.operationType()).hasSize(1000);
      assertThat(query.endpointName()).hasSize(1000);
      assertThat(query.stdKey()).hasSize(1000);
      assertThat(query.providerParamName()).hasSize(1000);
      assertThat(query.transformCode()).hasSize(1000);
      assertThat(query.notesJson()).hasSize(1000);
    }

    @Test
    @DisplayName("应该处理最小正数 Provenance ID")
    void shouldHandleMinimumPositiveProvenanceId() {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              1L,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.provenanceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该处理最大 Long 值的 Provenance ID")
    void shouldHandleMaximumLongProvenanceId() {
      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              Long.MAX_VALUE,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.provenanceId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理相同的 Effective From 和 Effective To")
    void shouldHandleSameEffectiveFromAndTo() {
      // Arrange
      Instant sameInstant = Instant.parse("2024-06-15T12:00:00Z");

      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              sameInstant,
              sameInstant);

      // Assert
      assertThat(query.effectiveFrom()).isEqualTo(sameInstant);
      assertThat(query.effectiveTo()).isEqualTo(sameInstant);
    }

    @Test
    @DisplayName("应该处理 Effective To 早于 Effective From（不验证时间逻辑）")
    void shouldAllowEffectiveToBeforeEffectiveFrom() {
      // Arrange
      Instant earlier = Instant.parse("2024-01-01T00:00:00Z");
      Instant later = Instant.parse("2024-12-31T23:59:59Z");

      // Act - 注意：Query 视图不验证业务逻辑，只验证字段非空
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              VALID_NOTES_JSON,
              later,
              earlier);

      // Assert
      assertThat(query.effectiveFrom()).isEqualTo(later);
      assertThat(query.effectiveTo()).isEqualTo(earlier);
    }
  }

  @Nested
  @DisplayName("特殊字符处理")
  class SpecialCharacterHandling {

    @Test
    @DisplayName("应该处理包含特殊字符的字段")
    void shouldHandleSpecialCharacters() {
      // Arrange
      String specialChars = "author@name!#$%";

      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              specialChars,
              specialChars,
              specialChars,
              specialChars,
              specialChars,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.operationType()).isEqualTo(specialChars);
      assertThat(query.endpointName()).isEqualTo(specialChars);
      assertThat(query.stdKey()).isEqualTo(specialChars);
      assertThat(query.providerParamName()).isEqualTo(specialChars);
      assertThat(query.transformCode()).isEqualTo(specialChars);
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的字段")
    void shouldHandleUnicodeCharacters() {
      // Arrange
      String unicode = "作者名称🔍";

      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              unicode,
              unicode,
              unicode,
              unicode,
              unicode,
              VALID_NOTES_JSON,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.operationType()).isEqualTo(unicode);
      assertThat(query.endpointName()).isEqualTo(unicode);
      assertThat(query.stdKey()).isEqualTo(unicode);
      assertThat(query.providerParamName()).isEqualTo(unicode);
      assertThat(query.transformCode()).isEqualTo(unicode);
    }

    @Test
    @DisplayName("应该处理 JSON 格式的 Notes")
    void shouldHandleJsonNotes() {
      // Arrange
      String complexJson = "{\"note\":\"测试\",\"priority\":1,\"tags\":[\"important\",\"urgent\"]}";

      // Act
      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_ENDPOINT_NAME,
              VALID_STD_KEY,
              VALID_PROVIDER_PARAM_NAME,
              VALID_TRANSFORM_CODE,
              complexJson,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO);

      // Assert
      assertThat(query.notesJson()).isEqualTo(complexJson);
    }
  }
}
