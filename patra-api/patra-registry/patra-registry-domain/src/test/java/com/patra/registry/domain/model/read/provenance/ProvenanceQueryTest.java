package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link ProvenanceQuery} 的单元测试。
/// 
/// 测试覆盖：
/// 
/// - 成功构造场景（所有字段、可选字段为 null）
///   - 验证失败场景（id 必须为正数、必填字段不能为空白）
///   - Trim 逻辑（自动修剪前后空格、trimOrNull 转换）
///   - Record 语义（equals、hashCode、toString、组件访问器）
///   - 不可变性验证
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceQuery 单元测试")
class ProvenanceQueryTest {

  @Nested
  @DisplayName("成功构造测试")
  class SuccessfulConstruction {

    @Test
    @DisplayName("应该成功构造 - 所有字段有效")
    void shouldConstructSuccessfully_whenAllFieldsValid() {
      // Given
      Long id = 1L;
      String code = "PUBMED";
      String name = "PubMed";
      String baseUrlDefault = "https://pubmed.ncbi.nlm.nih.gov";
      String timezoneDefault = "UTC";
      String docsUrl = "https://docs.pubmed.gov";
      boolean active = true;
      String lifecycleStatusCode = "ACTIVE";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.code()).isEqualTo(code);
      assertThat(query.name()).isEqualTo(name);
      assertThat(query.baseUrlDefault()).isEqualTo(baseUrlDefault);
      assertThat(query.timezoneDefault()).isEqualTo(timezoneDefault);
      assertThat(query.docsUrl()).isEqualTo(docsUrl);
      assertThat(query.active()).isTrue();
      assertThat(query.lifecycleStatusCode()).isEqualTo(lifecycleStatusCode);
    }

    @Test
    @DisplayName("应该成功构造 - baseUrlDefault 为 null")
    void shouldConstructSuccessfully_whenBaseUrlDefaultIsNull() {
      // Given
      Long id = 2L;
      String code = "EPMC";
      String name = "Europe PMC";
      String baseUrlDefault = null;
      String timezoneDefault = "Europe/London";
      String docsUrl = "https://europepmc.org/docs";
      boolean active = true;
      String lifecycleStatusCode = "ACTIVE";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then
      assertThat(query.baseUrlDefault()).isNull();
    }

    @Test
    @DisplayName("应该成功构造 - docsUrl 为 null")
    void shouldConstructSuccessfully_whenDocsUrlIsNull() {
      // Given
      Long id = 3L;
      String code = "CUSTOM";
      String name = "Custom Source";
      String baseUrlDefault = "https://custom.example.com";
      String timezoneDefault = "Asia/Shanghai";
      String docsUrl = null;
      boolean active = false;
      String lifecycleStatusCode = "DEPRECATED";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then
      assertThat(query.docsUrl()).isNull();
      assertThat(query.active()).isFalse();
    }

    @Test
    @DisplayName("应该成功构造 - baseUrlDefault 和 docsUrl 都为 null")
    void shouldConstructSuccessfully_whenBothOptionalFieldsAreNull() {
      // Given
      Long id = 4L;
      String code = "MINIMAL";
      String name = "Minimal Source";
      String baseUrlDefault = null;
      String timezoneDefault = "UTC";
      String docsUrl = null;
      boolean active = true;
      String lifecycleStatusCode = "ACTIVE";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then
      assertThat(query.baseUrlDefault()).isNull();
      assertThat(query.docsUrl()).isNull();
    }
  }

  @Nested
  @DisplayName("验证失败测试 - ID")
  class IdValidationFailures {

    @Test
    @DisplayName("应该抛出异常 - id 为 null")
    void shouldThrowException_whenIdIsNull() {
      // Given
      Long id = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      id,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常 - id 为 0")
    void shouldThrowException_whenIdIsZero() {
      // Given
      Long id = 0L;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      id,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常 - id 为负数")
    void shouldThrowException_whenIdIsNegative() {
      // Given
      Long id = -1L;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      id,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }
  }

  @Nested
  @DisplayName("验证失败测试 - Code")
  class CodeValidationFailures {

    @Test
    @DisplayName("应该抛出异常 - code 为 null")
    void shouldThrowException_whenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      code,
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - code 为空字符串")
    void shouldThrowException_whenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      code,
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - code 为空白")
    void shouldThrowException_whenCodeIsBlank() {
      // Given
      String code = "   ";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      code,
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance code")
          .hasMessageContaining("不能为空白");
    }
  }

  @Nested
  @DisplayName("验证失败测试 - Name")
  class NameValidationFailures {

    @Test
    @DisplayName("应该抛出异常 - name 为 null")
    void shouldThrowException_whenNameIsNull() {
      // Given
      String name = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      name,
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - name 为空字符串")
    void shouldThrowException_whenNameIsEmpty() {
      // Given
      String name = "";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      name,
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - name 为空白")
    void shouldThrowException_whenNameIsBlank() {
      // Given
      String name = "   ";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      name,
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance name")
          .hasMessageContaining("不能为空白");
    }
  }

  @Nested
  @DisplayName("验证失败测试 - TimezoneDefault")
  class TimezoneDefaultValidationFailures {

    @Test
    @DisplayName("应该抛出异常 - timezoneDefault 为 null")
    void shouldThrowException_whenTimezoneDefaultIsNull() {
      // Given
      String timezoneDefault = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      timezoneDefault,
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Timezone default")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - timezoneDefault 为空字符串")
    void shouldThrowException_whenTimezoneDefaultIsEmpty() {
      // Given
      String timezoneDefault = "";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      timezoneDefault,
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Timezone default")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - timezoneDefault 为空白")
    void shouldThrowException_whenTimezoneDefaultIsBlank() {
      // Given
      String timezoneDefault = "   ";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      timezoneDefault,
                      "https://docs.pubmed.gov",
                      true,
                      "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Timezone default")
          .hasMessageContaining("不能为空白");
    }
  }

  @Nested
  @DisplayName("验证失败测试 - LifecycleStatusCode")
  class LifecycleStatusCodeValidationFailures {

    @Test
    @DisplayName("应该抛出异常 - lifecycleStatusCode 为 null")
    void shouldThrowException_whenLifecycleStatusCodeIsNull() {
      // Given
      String lifecycleStatusCode = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      lifecycleStatusCode))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Lifecycle status code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - lifecycleStatusCode 为空字符串")
    void shouldThrowException_whenLifecycleStatusCodeIsEmpty() {
      // Given
      String lifecycleStatusCode = "";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      lifecycleStatusCode))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Lifecycle status code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常 - lifecycleStatusCode 为空白")
    void shouldThrowException_whenLifecycleStatusCodeIsBlank() {
      // Given
      String lifecycleStatusCode = "   ";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ProvenanceQuery(
                      1L,
                      "PUBMED",
                      "PubMed",
                      "https://pubmed.ncbi.nlm.nih.gov",
                      "UTC",
                      "https://docs.pubmed.gov",
                      true,
                      lifecycleStatusCode))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Lifecycle status code")
          .hasMessageContaining("不能为空白");
    }
  }

  @Nested
  @DisplayName("Trim 逻辑测试")
  class TrimLogic {

    @Test
    @DisplayName("应该自动 trim - code 前后空格")
    void shouldTrim_codeWithLeadingAndTrailingSpaces() {
      // Given
      String codeWithSpaces = "  PUBMED  ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              codeWithSpaces,
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // Then
      assertThat(query.code()).isEqualTo("PUBMED");
    }

    @Test
    @DisplayName("应该自动 trim - name 前后空格")
    void shouldTrim_nameWithLeadingAndTrailingSpaces() {
      // Given
      String nameWithSpaces = "  PubMed  ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              nameWithSpaces,
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // Then
      assertThat(query.name()).isEqualTo("PubMed");
    }

    @Test
    @DisplayName("应该自动 trim - timezoneDefault 前后空格")
    void shouldTrim_timezoneDefaultWithLeadingAndTrailingSpaces() {
      // Given
      String timezoneWithSpaces = "  UTC  ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              timezoneWithSpaces,
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // Then
      assertThat(query.timezoneDefault()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("应该自动 trim - lifecycleStatusCode 前后空格")
    void shouldTrim_lifecycleStatusCodeWithLeadingAndTrailingSpaces() {
      // Given
      String statusCodeWithSpaces = "  ACTIVE  ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              statusCodeWithSpaces);

      // Then
      assertThat(query.lifecycleStatusCode()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该自动 trim - baseUrlDefault 前后空格")
    void shouldTrim_baseUrlDefaultWithLeadingAndTrailingSpaces() {
      // Given
      String baseUrlWithSpaces = "  https://pubmed.ncbi.nlm.nih.gov  ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              baseUrlWithSpaces,
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // Then
      assertThat(query.baseUrlDefault()).isEqualTo("https://pubmed.ncbi.nlm.nih.gov");
    }

    @Test
    @DisplayName("应该自动 trim - docsUrl 前后空格")
    void shouldTrim_docsUrlWithLeadingAndTrailingSpaces() {
      // Given
      String docsUrlWithSpaces = "  https://docs.pubmed.gov  ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              docsUrlWithSpaces,
              true,
              "ACTIVE");

      // Then
      assertThat(query.docsUrl()).isEqualTo("https://docs.pubmed.gov");
    }

    @Test
    @DisplayName("应该转为 null - baseUrlDefault 为空白字符串")
    void shouldConvertToNull_whenBaseUrlDefaultIsBlankString() {
      // Given
      String baseUrlDefault = "   ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(1L, "PUBMED", "PubMed", baseUrlDefault, "UTC", null, true, "ACTIVE");

      // Then
      assertThat(query.baseUrlDefault()).isEqualTo("");
    }

    @Test
    @DisplayName("应该转为 null - docsUrl 为空白字符串")
    void shouldConvertToNull_whenDocsUrlIsBlankString() {
      // Given
      String docsUrl = "   ";

      // When
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              docsUrl,
              true,
              "ACTIVE");

      // Then
      assertThat(query.docsUrl()).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemantics {

    @Test
    @DisplayName("应该满足 equals 相等性 - 相同值")
    void shouldBeEqual_whenAllFieldsAreSame() {
      // Given
      ProvenanceQuery query1 =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      ProvenanceQuery query2 =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // When & Then
      assertThat(query1).isEqualTo(query2);
      assertThat(query1).hasSameHashCodeAs(query2);
    }

    @Test
    @DisplayName("应该不相等 - 不同 id")
    void shouldNotBeEqual_whenIdIsDifferent() {
      // Given
      ProvenanceQuery query1 =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      ProvenanceQuery query2 =
          new ProvenanceQuery(
              2L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("应该不相等 - 不同 code")
    void shouldNotBeEqual_whenCodeIsDifferent() {
      // Given
      ProvenanceQuery query1 =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      ProvenanceQuery query2 =
          new ProvenanceQuery(
              1L,
              "EPMC",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("应该不相等 - 不同 active 状态")
    void shouldNotBeEqual_whenActiveStatusIsDifferent() {
      // Given
      ProvenanceQuery query1 =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      ProvenanceQuery query2 =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              false,
              "ACTIVE");

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("应该提供 toString 表示")
    void shouldProvideToStringRepresentation() {
      // Given
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // When
      String toString = query.toString();

      // Then
      assertThat(toString).contains("ProvenanceQuery");
      assertThat(toString).contains("id=1");
      assertThat(toString).contains("code=PUBMED");
      assertThat(toString).contains("name=PubMed");
      assertThat(toString).contains("baseUrlDefault=https://pubmed.ncbi.nlm.nih.gov");
      assertThat(toString).contains("timezoneDefault=UTC");
      assertThat(toString).contains("docsUrl=https://docs.pubmed.gov");
      assertThat(toString).contains("active=true");
      assertThat(toString).contains("lifecycleStatusCode=ACTIVE");
    }

    @Test
    @DisplayName("应该提供组件访问器")
    void shouldProvideComponentAccessors() {
      // Given
      Long expectedId = 1L;
      String expectedCode = "PUBMED";
      String expectedName = "PubMed";
      String expectedBaseUrl = "https://pubmed.ncbi.nlm.nih.gov";
      String expectedTimezone = "UTC";
      String expectedDocsUrl = "https://docs.pubmed.gov";
      boolean expectedActive = true;
      String expectedStatusCode = "ACTIVE";

      ProvenanceQuery query =
          new ProvenanceQuery(
              expectedId,
              expectedCode,
              expectedName,
              expectedBaseUrl,
              expectedTimezone,
              expectedDocsUrl,
              expectedActive,
              expectedStatusCode);

      // When & Then
      assertThat(query.id()).isEqualTo(expectedId);
      assertThat(query.code()).isEqualTo(expectedCode);
      assertThat(query.name()).isEqualTo(expectedName);
      assertThat(query.baseUrlDefault()).isEqualTo(expectedBaseUrl);
      assertThat(query.timezoneDefault()).isEqualTo(expectedTimezone);
      assertThat(query.docsUrl()).isEqualTo(expectedDocsUrl);
      assertThat(query.active()).isEqualTo(expectedActive);
      assertThat(query.lifecycleStatusCode()).isEqualTo(expectedStatusCode);
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class Immutability {

    @Test
    @DisplayName("应该是不可变的 - Record 语义保证")
    void shouldBeImmutable() {
      // Given
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      // When - 访问字段
      Long id = query.id();
      String code = query.code();
      String name = query.name();
      String baseUrl = query.baseUrlDefault();
      String timezone = query.timezoneDefault();
      String docsUrl = query.docsUrl();
      boolean active = query.active();
      String statusCode = query.lifecycleStatusCode();

      // Then - 字段值不受外部修改影响（Record 语义保证）
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.code()).isEqualTo(code);
      assertThat(query.name()).isEqualTo(name);
      assertThat(query.baseUrlDefault()).isEqualTo(baseUrl);
      assertThat(query.timezoneDefault()).isEqualTo(timezone);
      assertThat(query.docsUrl()).isEqualTo(docsUrl);
      assertThat(query.active()).isEqualTo(active);
      assertThat(query.lifecycleStatusCode()).isEqualTo(statusCode);
    }

    @Test
    @DisplayName("应该是不可变的 - 创建后无法修改")
    void shouldRemainImmutable_afterCreation() {
      // Given
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://pubmed.ncbi.nlm.nih.gov",
              "UTC",
              "https://docs.pubmed.gov",
              true,
              "ACTIVE");

      Long originalId = query.id();
      String originalCode = query.code();
      String originalName = query.name();

      // When - 尝试获取引用（Record 返回的是不可变视图）
      Long idReference = query.id();
      String codeReference = query.code();
      String nameReference = query.name();

      // Then - 原始值未被修改
      assertThat(query.id()).isEqualTo(originalId);
      assertThat(query.code()).isEqualTo(originalCode);
      assertThat(query.name()).isEqualTo(originalName);
      assertThat(idReference).isEqualTo(originalId);
      assertThat(codeReference).isEqualTo(originalCode);
      assertThat(nameReference).isEqualTo(originalName);
    }
  }
}
