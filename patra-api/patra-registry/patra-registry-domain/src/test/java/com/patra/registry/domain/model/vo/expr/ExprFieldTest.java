package com.patra.registry.domain.model.vo.expr;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ExprField 值对象单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
 *   <li>验证字符串字段自动 trim 处理
 *   <li>测试可选字段的 null 处理（displayName, description）
 *   <li>验证 equals/hashCode 仅基于 fieldKey（业务键）
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>覆盖范围：
 *
 * <ul>
 *   <li>✅ record 构造函数验证测试
 *   <li>✅ 正整数 ID 验证
 *   <li>✅ 非空白字符串验证（fieldKey, dataTypeCode, cardinalityCode）
 *   <li>✅ 字符串 trim 处理测试
 *   <li>✅ 可选字段处理（displayName, description）
 *   <li>✅ record 的 equals/hashCode 基于 fieldKey
 *   <li>✅ record 的 toString 测试
 *   <li>✅ 不变性保证
 *   <li>✅ 业务场景测试（不同数据类型、基数等）
 *   <li>✅ 边界条件处理
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@DisplayName("ExprField 单元测试")
class ExprFieldTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的表达式字段")
    void shouldCreateExprFieldWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      String fieldKey = "publish_date";
      String displayName = "发布日期";
      String description = "文献发布日期，用于时间范围查询";
      String dataTypeCode = "DATE";
      String cardinalityCode = "SINGLE";
      boolean exposable = true;
      boolean dateField = true;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(
              id, fieldKey, displayName, description, dataTypeCode, cardinalityCode, exposable,
              dateField);

      // Then: 验证所有字段正确赋值
      assertThat(field).isNotNull();
      assertThat(field.id()).isEqualTo(id);
      assertThat(field.fieldKey()).isEqualTo(fieldKey);
      assertThat(field.displayName()).isEqualTo(displayName);
      assertThat(field.description()).isEqualTo(description);
      assertThat(field.dataTypeCode()).isEqualTo(dataTypeCode);
      assertThat(field.cardinalityCode()).isEqualTo(cardinalityCode);
      assertThat(field.exposable()).isTrue();
      assertThat(field.dateField()).isTrue();
      assertThat(field.isExposable()).isTrue();
      assertThat(field.isDateField()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalExprField() {
      // Given: 只有必需字段，可选字段为 null
      Long id = 1001L;
      String fieldKey = "ti";
      String displayName = null;
      String description = null;
      String dataTypeCode = "TEXT";
      String cardinalityCode = "SINGLE";
      boolean exposable = false;
      boolean dateField = false;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(
              id, fieldKey, displayName, description, dataTypeCode, cardinalityCode, exposable,
              dateField);

      // Then: 验证必需字段正确赋值，可选字段转为空字符串
      assertThat(field).isNotNull();
      assertThat(field.id()).isEqualTo(id);
      assertThat(field.fieldKey()).isEqualTo(fieldKey);
      assertThat(field.displayName()).isEmpty();
      assertThat(field.description()).isEmpty();
      assertThat(field.dataTypeCode()).isEqualTo(dataTypeCode);
      assertThat(field.cardinalityCode()).isEqualTo(cardinalityCode);
      assertThat(field.exposable()).isFalse();
      assertThat(field.dateField()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建多值字段（MULTI 基数）")
    void shouldCreateMultiValueField() {
      // Given: cardinalityCode 为 MULTI
      String cardinalityCode = "MULTI";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "keywords", null, null, "KEYWORD", cardinalityCode, true, false);

      // Then: 验证成功创建
      assertThat(field.cardinalityCode()).isEqualTo("MULTI");
    }

    @Test
    @DisplayName("应该成功创建不可暴露的字段")
    void shouldCreateNonExposableField() {
      // Given: exposable 为 false
      boolean exposable = false;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "internal_id", null, null, "NUMBER", "SINGLE", exposable, false);

      // Then: 验证成功创建
      assertThat(field.exposable()).isFalse();
      assertThat(field.isExposable()).isFalse();
    }
  }

  // ========== ID 验证测试 ==========

  @Nested
  @DisplayName("ID 正整数验证")
  class IdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 id 为 null")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given: id 为 null
      Long id = null;

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(id, "ti", null, null, "TEXT", "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(id, "ti", null, null, "TEXT", "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(id, "ti", null, null, "TEXT", "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的字段")
    void shouldCreateFieldWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 ExprField
      ExprField field = new ExprField(id, "ti", null, null, "TEXT", "SINGLE", true, false);

      // Then: 验证成功创建
      assertThat(field.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的字段")
    void shouldCreateFieldWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 ExprField
      ExprField field = new ExprField(id, "ti", null, null, "TEXT", "SINGLE", true, false);

      // Then: 验证成功创建
      assertThat(field.id()).isEqualTo(Long.MAX_VALUE);
    }
  }

  // ========== FieldKey 验证测试 ==========

  @Nested
  @DisplayName("FieldKey 非空白验证")
  class FieldKeyValidationTests {

    @Test
    @DisplayName("应该抛出异常当 fieldKey 为 null")
    void shouldThrowExceptionWhenFieldKeyIsNull() {
      // Given: fieldKey 为 null
      String fieldKey = null;

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, fieldKey, null, null, "TEXT", "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 fieldKey 为空字符串")
    void shouldThrowExceptionWhenFieldKeyIsEmpty() {
      // Given: fieldKey 为空字符串
      String fieldKey = "";

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, fieldKey, null, null, "TEXT", "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 fieldKey 仅包含空白字符")
    void shouldThrowExceptionWhenFieldKeyIsBlank() {
      // Given: fieldKey 仅包含空白字符
      String fieldKey = "   ";

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, fieldKey, null, null, "TEXT", "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim fieldKey 字段")
    void shouldTrimFieldKey() {
      // Given: fieldKey 包含首尾空白
      String fieldKey = "  publish_date  ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, fieldKey, null, null, "DATE", "SINGLE", true, true);

      // Then: 验证 fieldKey 已被 trim
      assertThat(field.fieldKey()).isEqualTo("publish_date");
    }
  }

  // ========== DataTypeCode 验证测试 ==========

  @Nested
  @DisplayName("DataTypeCode 非空白验证")
  class DataTypeCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 dataTypeCode 为 null")
    void shouldThrowExceptionWhenDataTypeCodeIsNull() {
      // Given: dataTypeCode 为 null
      String dataTypeCode = null;

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, "ti", null, null, dataTypeCode, "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field data type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 dataTypeCode 为空字符串")
    void shouldThrowExceptionWhenDataTypeCodeIsEmpty() {
      // Given: dataTypeCode 为空字符串
      String dataTypeCode = "";

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, "ti", null, null, dataTypeCode, "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field data type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 dataTypeCode 仅包含空白字符")
    void shouldThrowExceptionWhenDataTypeCodeIsBlank() {
      // Given: dataTypeCode 仅包含空白字符
      String dataTypeCode = "   ";

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, "ti", null, null, dataTypeCode, "SINGLE", true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field data type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim dataTypeCode 字段")
    void shouldTrimDataTypeCode() {
      // Given: dataTypeCode 包含首尾空白
      String dataTypeCode = "  TEXT  ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", null, null, dataTypeCode, "SINGLE", true, false);

      // Then: 验证 dataTypeCode 已被 trim
      assertThat(field.dataTypeCode()).isEqualTo("TEXT");
    }
  }

  // ========== CardinalityCode 验证测试 ==========

  @Nested
  @DisplayName("CardinalityCode 非空白验证")
  class CardinalityCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 cardinalityCode 为 null")
    void shouldThrowExceptionWhenCardinalityCodeIsNull() {
      // Given: cardinalityCode 为 null
      String cardinalityCode = null;

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, "ti", null, null, "TEXT", cardinalityCode, true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field cardinality code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 cardinalityCode 为空字符串")
    void shouldThrowExceptionWhenCardinalityCodeIsEmpty() {
      // Given: cardinalityCode 为空字符串
      String cardinalityCode = "";

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, "ti", null, null, "TEXT", cardinalityCode, true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field cardinality code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 cardinalityCode 仅包含空白字符")
    void shouldThrowExceptionWhenCardinalityCodeIsBlank() {
      // Given: cardinalityCode 仅包含空白字符
      String cardinalityCode = "   ";

      // When & Then: 创建字段应该失败
      assertThatThrownBy(
              () -> new ExprField(1001L, "ti", null, null, "TEXT", cardinalityCode, true, false))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Expr field cardinality code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim cardinalityCode 字段")
    void shouldTrimCardinalityCode() {
      // Given: cardinalityCode 包含首尾空白
      String cardinalityCode = "  SINGLE  ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", null, null, "TEXT", cardinalityCode, true, false);

      // Then: 验证 cardinalityCode 已被 trim
      assertThat(field.cardinalityCode()).isEqualTo("SINGLE");
    }
  }

  // ========== 字符串 Trim 处理测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该自动 trim displayName 字段")
    void shouldTrimDisplayName() {
      // Given: displayName 包含首尾空白
      String displayName = "  标题  ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", displayName, null, "TEXT", "SINGLE", true, false);

      // Then: 验证 displayName 已被 trim
      assertThat(field.displayName()).isEqualTo("标题");
    }

    @Test
    @DisplayName("应该自动 trim description 字段")
    void shouldTrimDescription() {
      // Given: description 包含首尾空白
      String description = "  文献标题字段  ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", null, description, "TEXT", "SINGLE", true, false);

      // Then: 验证 description 已被 trim
      assertThat(field.description()).isEqualTo("文献标题字段");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String fieldKey = "  ti  ";
      String displayName = "  标题  ";
      String description = "  文献标题字段  ";
      String dataTypeCode = "  TEXT  ";
      String cardinalityCode = "  SINGLE  ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, fieldKey, displayName, description, dataTypeCode, cardinalityCode,
              true, false);

      // Then: 验证所有字段都已被 trim
      assertThat(field.fieldKey()).isEqualTo("ti");
      assertThat(field.displayName()).isEqualTo("标题");
      assertThat(field.description()).isEqualTo("文献标题字段");
      assertThat(field.dataTypeCode()).isEqualTo("TEXT");
      assertThat(field.cardinalityCode()).isEqualTo("SINGLE");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String fieldKey = "\t\n  ti  \t\n";
      String displayName = " \t 标题 \n ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, fieldKey, displayName, null, "TEXT", "SINGLE", true, false);

      // Then: 验证空白字符都被 trim
      assertThat(field.fieldKey()).isEqualTo("ti");
      assertThat(field.displayName()).isEqualTo("标题");
    }
  }

  // ========== Null 处理测试 ==========

  @Nested
  @DisplayName("可选字段 Null 处理")
  class NullHandlingTests {

    @Test
    @DisplayName("displayName 为 null 时应转为空字符串")
    void displayNameNullShouldBeEmptyString() {
      // Given: displayName 为 null
      String displayName = null;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", displayName, null, "TEXT", "SINGLE", true, false);

      // Then: 验证 displayName 为空字符串
      assertThat(field.displayName()).isEmpty();
    }

    @Test
    @DisplayName("description 为 null 时应转为空字符串")
    void descriptionNullShouldBeEmptyString() {
      // Given: description 为 null
      String description = null;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", null, description, "TEXT", "SINGLE", true, false);

      // Then: 验证 description 为空字符串
      assertThat(field.description()).isEmpty();
    }

    @Test
    @DisplayName("应该处理 displayName 和 description 都为 null 的情况")
    void shouldHandleBothOptionalFieldsBeingNull() {
      // Given: displayName 和 description 都为 null
      ExprField field = new ExprField(1001L, "ti", null, null, "TEXT", "SINGLE", true, false);

      // Then: 验证都为空字符串
      assertThat(field.displayName()).isEmpty();
      assertThat(field.description()).isEmpty();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该基于 fieldKey 实现 equals（相同 fieldKey 相等）")
    void shouldImplementEqualsBasedOnFieldKey() {
      // Given: 两个不同的字段，但 fieldKey 相同
      ExprField field1 =
          new ExprField(1001L, "ti", "标题", "文献标题", "TEXT", "SINGLE", true, false);

      ExprField field2 =
          new ExprField(2001L, "ti", "Title", "Different description", "KEYWORD", "MULTI", false,
              true);

      // When & Then: 应该相等（因为 fieldKey 相同）
      assertThat(field1).isEqualTo(field2);
      assertThat(field1).hasSameHashCodeAs(field2);
    }

    @Test
    @DisplayName("应该基于 fieldKey 实现 equals（不同 fieldKey 不相等）")
    void shouldImplementEqualsBasedOnFieldKeyForDifferentKeys() {
      // Given: 两个不同 fieldKey 的字段
      ExprField field1 = new ExprField(1001L, "ti", null, null, "TEXT", "SINGLE", true, false);

      ExprField field2 = new ExprField(1001L, "ab", null, null, "TEXT", "SINGLE", true, false);

      // When & Then: 不应该相等（因为 fieldKey 不同）
      assertThat(field1).isNotEqualTo(field2);
    }

    @Test
    @DisplayName("应该基于 fieldKey 实现 hashCode")
    void shouldImplementHashCodeBasedOnFieldKey() {
      // Given: 两个不同的字段，但 fieldKey 相同
      ExprField field1 =
          new ExprField(1001L, "ti", "标题", "文献标题", "TEXT", "SINGLE", true, false);

      ExprField field2 =
          new ExprField(2001L, "ti", "Title", "Different description", "KEYWORD", "MULTI", false,
              true);

      // When & Then: hashCode 应该相等
      assertThat(field1.hashCode()).isEqualTo(field2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建字段
      ExprField field =
          new ExprField(1001L, "publish_date", "发布日期", "文献发布日期", "DATE", "SINGLE", true,
              true);

      // When: 调用 toString
      String toString = field.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("ExprField");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("publish_date");
      assertThat(toString).contains("DATE");
      assertThat(toString).contains("SINGLE");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建字段
      ExprField field = new ExprField(1001L, "ti", null, null, "TEXT", "SINGLE", true, false);

      // When & Then: 对象应该等于自身
      assertThat(field).isEqualTo(field);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建字段
      ExprField field = new ExprField(1001L, "ti", null, null, "TEXT", "SINGLE", true, false);

      // When & Then: 与 null 比较应该返回 false
      assertThat(field).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建字段
      ExprField field = new ExprField(1001L, "ti", null, null, "TEXT", "SINGLE", true, false);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(field).isNotEqualTo("Not an ExprField");
      assertThat(field).isNotEqualTo(1001L);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建字段
      Long originalId = 1001L;
      String originalFieldKey = "ti";
      String originalDisplayName = "标题";
      String originalDescription = "文献标题";
      String originalDataTypeCode = "TEXT";
      String originalCardinalityCode = "SINGLE";
      boolean originalExposable = true;
      boolean originalDateField = false;

      ExprField field =
          new ExprField(originalId, originalFieldKey, originalDisplayName, originalDescription,
              originalDataTypeCode, originalCardinalityCode, originalExposable, originalDateField);

      // When: 获取字段值
      Long retrievedId = field.id();
      String retrievedFieldKey = field.fieldKey();
      String retrievedDisplayName = field.displayName();
      String retrievedDescription = field.description();
      String retrievedDataTypeCode = field.dataTypeCode();
      String retrievedCardinalityCode = field.cardinalityCode();
      boolean retrievedExposable = field.exposable();
      boolean retrievedDateField = field.dateField();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedFieldKey).isEqualTo(originalFieldKey);
      assertThat(retrievedDisplayName).isEqualTo(originalDisplayName);
      assertThat(retrievedDescription).isEqualTo(originalDescription);
      assertThat(retrievedDataTypeCode).isEqualTo(originalDataTypeCode);
      assertThat(retrievedCardinalityCode).isEqualTo(originalCardinalityCode);
      assertThat(retrievedExposable).isEqualTo(originalExposable);
      assertThat(retrievedDateField).isEqualTo(originalDateField);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建字段
      ExprField field =
          new ExprField(1001L, "ti", "标题", "文献标题", "TEXT", "SINGLE", true, false);

      // When: 多次获取字段值
      String fieldKey1 = field.fieldKey();
      String fieldKey2 = field.fieldKey();
      String displayName1 = field.displayName();
      String displayName2 = field.displayName();

      // Then: 字段值应该保持一致
      assertThat(fieldKey1).isEqualTo(fieldKey2);
      assertThat(displayName1).isEqualTo(displayName2);
      assertThat(fieldKey1).isSameAs(fieldKey2);
      assertThat(displayName1).isSameAs(displayName2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 DATE 类型的日期字段")
    void shouldCreateDateTypeField() {
      // Given: dataTypeCode 为 DATE，dateField 为 true
      String dataTypeCode = "DATE";
      boolean dateField = true;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "publish_date", "发布日期", "文献发布日期", dataTypeCode, "SINGLE",
              true, dateField);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("DATE");
      assertThat(field.dateField()).isTrue();
      assertThat(field.isDateField()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 DATETIME 类型的日期字段")
    void shouldCreateDateTimeTypeField() {
      // Given: dataTypeCode 为 DATETIME，dateField 为 true
      String dataTypeCode = "DATETIME";
      boolean dateField = true;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "created_at", "创建时间", "文献创建时间", dataTypeCode, "SINGLE",
              true, dateField);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("DATETIME");
      assertThat(field.dateField()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 TEXT 类型的文本字段")
    void shouldCreateTextTypeField() {
      // Given: dataTypeCode 为 TEXT
      String dataTypeCode = "TEXT";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", "标题", "文献标题", dataTypeCode, "SINGLE", true, false);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("TEXT");
      assertThat(field.dateField()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建 KEYWORD 类型的关键词字段")
    void shouldCreateKeywordTypeField() {
      // Given: dataTypeCode 为 KEYWORD
      String dataTypeCode = "KEYWORD";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "keywords", "关键词", "文献关键词", dataTypeCode, "MULTI", true,
              false);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("KEYWORD");
      assertThat(field.cardinalityCode()).isEqualTo("MULTI");
    }

    @Test
    @DisplayName("应该成功创建 NUMBER 类型的数字字段")
    void shouldCreateNumberTypeField() {
      // Given: dataTypeCode 为 NUMBER
      String dataTypeCode = "NUMBER";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "citation_count", "引用次数", "文献被引用次数", dataTypeCode,
              "SINGLE", true, false);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("NUMBER");
    }

    @Test
    @DisplayName("应该成功创建 BOOLEAN 类型的布尔字段")
    void shouldCreateBooleanTypeField() {
      // Given: dataTypeCode 为 BOOLEAN
      String dataTypeCode = "BOOLEAN";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "is_open_access", "开放获取", "是否开放获取", dataTypeCode,
              "SINGLE", true, false);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("BOOLEAN");
    }

    @Test
    @DisplayName("应该成功创建 TOKEN 类型的令牌字段")
    void shouldCreateTokenTypeField() {
      // Given: dataTypeCode 为 TOKEN
      String dataTypeCode = "TOKEN";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "author_id", "作者ID", "作者标识符", dataTypeCode, "SINGLE", false,
              false);

      // Then: 验证成功创建
      assertThat(field.dataTypeCode()).isEqualTo("TOKEN");
      assertThat(field.exposable()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建多值基数的字段")
    void shouldCreateMultiCardinalityField() {
      // Given: cardinalityCode 为 MULTI
      String cardinalityCode = "MULTI";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "authors", "作者", "文献作者列表", "TEXT", cardinalityCode, true,
              false);

      // Then: 验证成功创建
      assertThat(field.cardinalityCode()).isEqualTo("MULTI");
    }

    @Test
    @DisplayName("应该成功创建单值基数的字段")
    void shouldCreateSingleCardinalityField() {
      // Given: cardinalityCode 为 SINGLE
      String cardinalityCode = "SINGLE";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "doi", "DOI", "数字对象标识符", "KEYWORD", cardinalityCode, true,
              false);

      // Then: 验证成功创建
      assertThat(field.cardinalityCode()).isEqualTo("SINGLE");
    }

    @Test
    @DisplayName("应该成功创建可暴露的字段")
    void shouldCreateExposableField() {
      // Given: exposable 为 true
      boolean exposable = true;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", "标题", "文献标题", "TEXT", "SINGLE", exposable, false);

      // Then: 验证成功创建
      assertThat(field.exposable()).isTrue();
      assertThat(field.isExposable()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建不可暴露的内部字段")
    void shouldCreateNonExposableInternalField() {
      // Given: exposable 为 false（内部字段）
      boolean exposable = false;

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "internal_score", "内部评分", "系统内部评分字段", "NUMBER", "SINGLE",
              exposable, false);

      // Then: 验证成功创建
      assertThat(field.exposable()).isFalse();
      assertThat(field.isExposable()).isFalse();
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      ExprField field1 =
          new ExprField(1001L, "ti", "标题", "描述", "TEXT", "SINGLE", true, false);

      ExprField field2 =
          new ExprField(1001L, "  ti  ", "  标题  ", "  描述  ", "  TEXT  ", "  SINGLE  ", true,
              false);

      // When & Then: trim 后应该相等（基于 fieldKey）
      assertThat(field1).isEqualTo(field2);
    }

    @Test
    @DisplayName("应该处理极短的字段键")
    void shouldHandleMinimalFieldKey() {
      // Given: fieldKey 为单字符
      String fieldKey = "a";

      // When: 创建 ExprField
      ExprField field = new ExprField(1001L, fieldKey, null, null, "TEXT", "SINGLE", true, false);

      // Then: 应该成功创建
      assertThat(field.fieldKey()).isEqualTo("a");
    }

    @Test
    @DisplayName("应该处理极长的字段键")
    void shouldHandleVeryLongFieldKey() {
      // Given: fieldKey 为很长的字符串
      String fieldKey = "very_long_field_key_with_many_underscores_and_characters";

      // When: 创建 ExprField
      ExprField field = new ExprField(1001L, fieldKey, null, null, "TEXT", "SINGLE", true, false);

      // Then: 应该成功创建
      assertThat(field.fieldKey()).isEqualTo(fieldKey);
    }

    @Test
    @DisplayName("应该处理包含特殊字符的字段键")
    void shouldHandleFieldKeyWithSpecialCharacters() {
      // Given: fieldKey 包含下划线和数字
      String fieldKey = "field_key_123";

      // When: 创建 ExprField
      ExprField field = new ExprField(1001L, fieldKey, null, null, "TEXT", "SINGLE", true, false);

      // Then: 应该成功创建
      assertThat(field.fieldKey()).isEqualTo(fieldKey);
    }

    @Test
    @DisplayName("应该处理空白的 displayName")
    void shouldHandleBlankDisplayName() {
      // Given: displayName 为空白字符串（trim 后为空）
      String displayName = "   ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", displayName, null, "TEXT", "SINGLE", true, false);

      // Then: 应该转为空字符串
      assertThat(field.displayName()).isEmpty();
    }

    @Test
    @DisplayName("应该处理空白的 description")
    void shouldHandleBlankDescription() {
      // Given: description 为空白字符串（trim 后为空）
      String description = "   ";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "ti", null, description, "TEXT", "SINGLE", true, false);

      // Then: 应该转为空字符串
      assertThat(field.description()).isEmpty();
    }

    @Test
    @DisplayName("应该处理中文字段内容")
    void shouldHandleChineseFieldContent() {
      // Given: 包含中文的字段
      String displayName = "标题摘要关键词";
      String description = "用于全文搜索的复合字段，包含标题、摘要和关键词";

      // When: 创建 ExprField
      ExprField field =
          new ExprField(1001L, "tiab", displayName, description, "TEXT", "SINGLE", true, false);

      // Then: 应该成功创建
      assertThat(field.displayName()).isEqualTo(displayName);
      assertThat(field.description()).isEqualTo(description);
    }
  }
}
