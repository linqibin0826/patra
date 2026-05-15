package dev.linqibin.patra.registry.domain.model.read.expr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ExprFieldQuery 的单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExprFieldQuery 测试")
class ExprFieldQueryTest {

  @Nested
  @DisplayName("构造器验证")
  class ConstructorValidation {

    @Test
    @DisplayName("成功构造 - 所有字段有效")
    void shouldConstructSuccessfully_whenAllFieldsValid() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";
      boolean exposable = true;
      boolean dateField = false;

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey,
              displayName,
              description,
              dataTypeCode,
              cardinalityCode,
              exposable,
              dateField);

      // Then
      assertThat(query.fieldKey()).isEqualTo("author");
      assertThat(query.displayName()).isEqualTo("作者");
      assertThat(query.description()).isEqualTo("文献作者");
      assertThat(query.dataTypeCode()).isEqualTo("STRING");
      assertThat(query.cardinalityCode()).isEqualTo("MULTIPLE");
      assertThat(query.exposable()).isTrue();
      assertThat(query.dateField()).isFalse();
    }

    @Test
    @DisplayName("失败 - fieldKey 为 null")
    void shouldThrowException_whenFieldKeyIsNull() {
      // Given
      String fieldKey = null;
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Field key 不能为空白");
    }

    @Test
    @DisplayName("失败 - fieldKey 为空字符串")
    void shouldThrowException_whenFieldKeyIsEmpty() {
      // Given
      String fieldKey = "";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Field key 不能为空白");
    }

    @Test
    @DisplayName("失败 - fieldKey 仅包含空白字符")
    void shouldThrowException_whenFieldKeyIsBlank() {
      // Given
      String fieldKey = "   ";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Field key 不能为空白");
    }

    @Test
    @DisplayName("失败 - dataTypeCode 为 null")
    void shouldThrowException_whenDataTypeCodeIsNull() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = null;
      String cardinalityCode = "MULTIPLE";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Data type code 不能为空白");
    }

    @Test
    @DisplayName("失败 - dataTypeCode 为空字符串")
    void shouldThrowException_whenDataTypeCodeIsEmpty() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "";
      String cardinalityCode = "MULTIPLE";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Data type code 不能为空白");
    }

    @Test
    @DisplayName("失败 - dataTypeCode 仅包含空白字符")
    void shouldThrowException_whenDataTypeCodeIsBlank() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "  ";
      String cardinalityCode = "MULTIPLE";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Data type code 不能为空白");
    }

    @Test
    @DisplayName("失败 - cardinalityCode 为 null")
    void shouldThrowException_whenCardinalityCodeIsNull() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Cardinality code 不能为空白");
    }

    @Test
    @DisplayName("失败 - cardinalityCode 为空字符串")
    void shouldThrowException_whenCardinalityCodeIsEmpty() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Cardinality code 不能为空白");
    }

    @Test
    @DisplayName("失败 - cardinalityCode 仅包含空白字符")
    void shouldThrowException_whenCardinalityCodeIsBlank() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "   ";

      // When & Then
      assertThatThrownBy(
              () ->
                  new ExprFieldQuery(
                      fieldKey,
                      displayName,
                      description,
                      dataTypeCode,
                      cardinalityCode,
                      true,
                      false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("Cardinality code 不能为空白");
    }
  }

  @Nested
  @DisplayName("字段 Trim 逻辑")
  class FieldTrimming {

    @Test
    @DisplayName("fieldKey 应被 trim")
    void shouldTrimFieldKey() {
      // Given
      String fieldKey = "  author  ";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.fieldKey()).isEqualTo("author");
    }

    @Test
    @DisplayName("dataTypeCode 应被 trim")
    void shouldTrimDataTypeCode() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "  STRING  ";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.dataTypeCode()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("cardinalityCode 应被 trim")
    void shouldTrimCardinalityCode() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "  MULTIPLE  ";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.cardinalityCode()).isEqualTo("MULTIPLE");
    }

    @Test
    @DisplayName("displayName 应被 trim - 非 null")
    void shouldTrimDisplayName_whenNotNull() {
      // Given
      String fieldKey = "author";
      String displayName = "  作者  ";
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.displayName()).isEqualTo("作者");
    }

    @Test
    @DisplayName("displayName 为 null 时应返回空字符串")
    void shouldReturnEmptyString_whenDisplayNameIsNull() {
      // Given
      String fieldKey = "author";
      String displayName = null;
      String description = "文献作者";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.displayName()).isEmpty();
    }

    @Test
    @DisplayName("description 应被 trim - 非 null")
    void shouldTrimDescription_whenNotNull() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = "  文献作者  ";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.description()).isEqualTo("文献作者");
    }

    @Test
    @DisplayName("description 为 null 时应返回空字符串")
    void shouldReturnEmptyString_whenDescriptionIsNull() {
      // Given
      String fieldKey = "author";
      String displayName = "作者";
      String description = null;
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.description()).isEmpty();
    }

    @Test
    @DisplayName("displayName 和 description 同时为 null 时应返回空字符串")
    void shouldReturnEmptyStrings_whenBothDisplayNameAndDescriptionAreNull() {
      // Given
      String fieldKey = "author";
      String displayName = null;
      String description = null;
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.displayName()).isEmpty();
      assertThat(query.description()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Record 语义")
  class RecordSemantics {

    @Test
    @DisplayName("equals - 相同内容应相等")
    void shouldBeEqual_whenSameContent() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);

      // When & Then
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("equals - 不同 fieldKey 应不相等")
    void shouldNotBeEqual_whenDifferentFieldKey() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("title", "作者", "文献作者", "STRING", "MULTIPLE", true, false);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("equals - 不同 displayName 应不相等")
    void shouldNotBeEqual_whenDifferentDisplayName() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "标题", "文献作者", "STRING", "MULTIPLE", true, false);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("equals - 不同 description 应不相等")
    void shouldNotBeEqual_whenDifferentDescription() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "其他描述", "STRING", "MULTIPLE", true, false);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("equals - 不同 dataTypeCode 应不相等")
    void shouldNotBeEqual_whenDifferentDataTypeCode() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "文献作者", "INTEGER", "MULTIPLE", true, false);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("equals - 不同 cardinalityCode 应不相等")
    void shouldNotBeEqual_whenDifferentCardinalityCode() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "SINGLE", true, false);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("equals - 不同 exposable 应不相等")
    void shouldNotBeEqual_whenDifferentExposable() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", false, false);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("equals - 不同 dateField 应不相等")
    void shouldNotBeEqual_whenDifferentDateField() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, true);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("hashCode - 相同内容应产生相同的哈希码")
    void shouldHaveSameHashCode_whenSameContent() {
      // Given
      ExprFieldQuery query1 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);

      // When & Then
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("toString - 应包含所有字段信息")
    void shouldContainAllFields_inToString() {
      // Given
      ExprFieldQuery query =
          new ExprFieldQuery("author", "作者", "文献作者", "STRING", "MULTIPLE", true, false);

      // When
      String result = query.toString();

      // Then
      assertThat(result)
          .contains("author")
          .contains("作者")
          .contains("文献作者")
          .contains("STRING")
          .contains("MULTIPLE")
          .contains("true")
          .contains("false");
    }
  }

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditions {

    @Test
    @DisplayName("最小有效输入 - 必填字段非空，可选字段为 null")
    void shouldAcceptMinimalValidInput() {
      // Given
      String fieldKey = "a";
      String displayName = null;
      String description = null;
      String dataTypeCode = "S";
      String cardinalityCode = "M";
      boolean exposable = false;
      boolean dateField = false;

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey,
              displayName,
              description,
              dataTypeCode,
              cardinalityCode,
              exposable,
              dateField);

      // Then
      assertThat(query.fieldKey()).isEqualTo("a");
      assertThat(query.displayName()).isEmpty();
      assertThat(query.description()).isEmpty();
      assertThat(query.dataTypeCode()).isEqualTo("S");
      assertThat(query.cardinalityCode()).isEqualTo("M");
      assertThat(query.exposable()).isFalse();
      assertThat(query.dateField()).isFalse();
    }

    @Test
    @DisplayName("超长字符串 - 应成功处理")
    void shouldHandleLongStrings() {
      // Given
      String fieldKey = "a".repeat(1000);
      String displayName = "中文".repeat(500);
      String description = "描述".repeat(500);
      String dataTypeCode = "S".repeat(100);
      String cardinalityCode = "M".repeat(100);

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.fieldKey()).hasSize(1000);
      assertThat(query.displayName()).hasSize(1000);
      assertThat(query.description()).hasSize(1000);
      assertThat(query.dataTypeCode()).hasSize(100);
      assertThat(query.cardinalityCode()).hasSize(100);
    }

    @Test
    @DisplayName("特殊字符 - 应正确处理")
    void shouldHandleSpecialCharacters() {
      // Given
      String fieldKey = "field@#$%^&*()";
      String displayName = "名称!@#$%^&*()";
      String description = "描述<>?:\"{}|";
      String dataTypeCode = "STRING@123";
      String cardinalityCode = "MULTIPLE#456";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.fieldKey()).isEqualTo("field@#$%^&*()");
      assertThat(query.displayName()).isEqualTo("名称!@#$%^&*()");
      assertThat(query.description()).isEqualTo("描述<>?:\"{}|");
      assertThat(query.dataTypeCode()).isEqualTo("STRING@123");
      assertThat(query.cardinalityCode()).isEqualTo("MULTIPLE#456");
    }

    @Test
    @DisplayName("Unicode 字符 - 应正确处理 Emoji")
    void shouldHandleUnicodeCharacters() {
      // Given
      String fieldKey = "field_😀";
      String displayName = "名称_🎉";
      String description = "描述_👍";
      String dataTypeCode = "STRING";
      String cardinalityCode = "MULTIPLE";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.fieldKey()).isEqualTo("field_😀");
      assertThat(query.displayName()).isEqualTo("名称_🎉");
      assertThat(query.description()).isEqualTo("描述_👍");
    }

    @Test
    @DisplayName("布尔边界 - exposable=true dateField=true")
    void shouldHandleBooleanBoundary_whenBothTrue() {
      // Given
      String fieldKey = "publishDate";
      String displayName = "发布日期";
      String description = "文献发布日期";
      String dataTypeCode = "DATE";
      String cardinalityCode = "SINGLE";
      boolean exposable = true;
      boolean dateField = true;

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey,
              displayName,
              description,
              dataTypeCode,
              cardinalityCode,
              exposable,
              dateField);

      // Then
      assertThat(query.exposable()).isTrue();
      assertThat(query.dateField()).isTrue();
    }

    @Test
    @DisplayName("布尔边界 - exposable=false dateField=false")
    void shouldHandleBooleanBoundary_whenBothFalse() {
      // Given
      String fieldKey = "internalId";
      String displayName = "内部ID";
      String description = "系统内部标识";
      String dataTypeCode = "LONG";
      String cardinalityCode = "SINGLE";
      boolean exposable = false;
      boolean dateField = false;

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey,
              displayName,
              description,
              dataTypeCode,
              cardinalityCode,
              exposable,
              dateField);

      // Then
      assertThat(query.exposable()).isFalse();
      assertThat(query.dateField()).isFalse();
    }

    @Test
    @DisplayName("空白字符混合 - 各种空白字符应被正确 trim")
    void shouldTrimVariousWhitespaceCharacters() {
      // Given
      String fieldKey = "\t\n  author  \r\n\t";
      String displayName = "\t作者\n";
      String description = " \r文献作者\t ";
      String dataTypeCode = "\nSTRING\r";
      String cardinalityCode = "\tMULTIPLE ";

      // When
      ExprFieldQuery query =
          new ExprFieldQuery(
              fieldKey, displayName, description, dataTypeCode, cardinalityCode, true, false);

      // Then
      assertThat(query.fieldKey()).isEqualTo("author");
      assertThat(query.displayName()).isEqualTo("作者");
      assertThat(query.description()).isEqualTo("文献作者");
      assertThat(query.dataTypeCode()).isEqualTo("STRING");
      assertThat(query.cardinalityCode()).isEqualTo("MULTIPLE");
    }
  }
}
