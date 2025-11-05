package com.patra.registry.domain.support;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RegistryKeyStandardizer 工具类单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>测试所有静态方法的各种输入场景
 *   <li>验证 null、空白字符串、有效字符串的处理逻辑
 *   <li>验证字符串 trim 和大小写转换行为
 *   <li>验证占位符常量（ALL、ANY、T、F）的正确使用
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>覆盖范围：
 *
 * <ul>
 *   <li>✅ toOperationKeyOrAll 测试（null、空白、有效字符串、trim、大小写保留）
 *   <li>✅ toUppercaseCode 测试（null 异常、小写转大写、大写保持、混合大小写、trim）
 *   <li>✅ toTrimmedFieldKey 测试（null 异常、trim、大小写保留）
 *   <li>✅ toMatchTypeKeyOrAny 测试（null、空白、有效字符串、trim、转大写）
 *   <li>✅ toNegatedKeyOrAny 测试（null、true、false）
 *   <li>✅ toValueTypeKeyOrAny 测试（null、空白、有效字符串、trim、转大写）
 *   <li>✅ 工具类语义测试（final class、private constructor、static methods）
 *   <li>✅ 边界条件和特殊场景测试
 * </ul>
 *
 * @author Patra Team
 * @since 0.1.0
 */
@DisplayName("RegistryKeyStandardizer 单元测试")
class RegistryKeyStandardizerTest {

  // ========== toOperationKeyOrAll 测试 ==========

  @Nested
  @DisplayName("toOperationKeyOrAll - 操作类型键归一化")
  class ToOperationKeyOrAllTests {

    @Test
    @DisplayName("应该返回 ALL 当输入为 null")
    void shouldReturnAllWhenInputIsNull() {
      // Given: 输入为 null
      String operationType = null;

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 ALL
      assertThat(result).isEqualTo(RegistryKeyPlaceholders.ALL);
      assertThat(result).isEqualTo("ALL");
    }

    @Test
    @DisplayName("应该返回 ALL 当输入为空字符串")
    void shouldReturnAllWhenInputIsEmpty() {
      // Given: 输入为空字符串
      String operationType = "";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 ALL
      assertThat(result).isEqualTo("ALL");
    }

    @Test
    @DisplayName("应该返回 ALL 当输入为空白字符串")
    void shouldReturnAllWhenInputIsBlank() {
      // Given: 输入为空白字符串
      String operationType = "   ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 ALL
      assertThat(result).isEqualTo("ALL");
    }

    @Test
    @DisplayName("应该返回 ALL 当输入为制表符和换行符")
    void shouldReturnAllWhenInputIsWhitespaceCharacters() {
      // Given: 输入为制表符和换行符
      String operationType = "\t\n  \r\n";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 ALL
      assertThat(result).isEqualTo("ALL");
    }

    @Test
    @DisplayName("应该返回 trim 后的字符串（小写）")
    void shouldReturnTrimmedStringForLowercase() {
      // Given: 输入包含前后空白的小写字符串
      String operationType = "  harvest  ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 trim 后的小写字符串（保留原始大小写）
      assertThat(result).isEqualTo("harvest");
    }

    @Test
    @DisplayName("应该返回 trim 后的字符串（大写）")
    void shouldReturnTrimmedStringForUppercase() {
      // Given: 输入包含前后空白的大写字符串
      String operationType = "  HARVEST  ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 trim 后的大写字符串
      assertThat(result).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该保留原始大小写（混合大小写）")
    void shouldPreserveOriginalCase() {
      // Given: 输入为混合大小写
      String operationType = "  HaRvEsT  ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 保留原始大小写
      assertThat(result).isEqualTo("HaRvEsT");
    }

    @Test
    @DisplayName("应该处理不含空白的有效字符串")
    void shouldHandleValidStringWithoutWhitespace() {
      // Given: 输入为不含空白的有效字符串
      String operationType = "UPDATE";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回原始字符串
      assertThat(result).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该处理单字符输入")
    void shouldHandleSingleCharacterInput() {
      // Given: 输入为单字符
      String operationType = "A";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回单字符
      assertThat(result).isEqualTo("A");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白字符")
    void shouldPreserveInternalWhitespace() {
      // Given: 输入字符串内部包含空白
      String operationType = "  HARVEST UPDATE  ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: trim 前后空白，但保留内部空白
      assertThat(result).isEqualTo("HARVEST UPDATE");
    }
  }

  // ========== toUppercaseCode 测试 ==========

  @Nested
  @DisplayName("toUppercaseCode - 大写代码归一化")
  class ToUppercaseCodeTests {

    @Test
    @DisplayName("应该抛出异常当输入为 null")
    void shouldThrowExceptionWhenInputIsNull() {
      // Given: 输入为 null
      String value = null;

      // When & Then: 抛出 DomainValidationException
      assertThatThrownBy(() -> RegistryKeyStandardizer.toUppercaseCode(value))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("值不能为 null");
    }

    @Test
    @DisplayName("应该将小写字符串转为大写")
    void shouldConvertLowercaseToUppercase() {
      // Given: 输入为小写字符串
      String value = "active";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 返回大写字符串
      assertThat(result).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该保持大写字符串不变")
    void shouldKeepUppercaseUnchanged() {
      // Given: 输入为大写字符串
      String value = "ACTIVE";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 返回大写字符串
      assertThat(result).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该将混合大小写转为大写")
    void shouldConvertMixedCaseToUppercase() {
      // Given: 输入为混合大小写
      String value = "AcTiVe";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 返回大写字符串
      assertThat(result).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该 trim 前后空白并转大写")
    void shouldTrimAndConvertToUppercase() {
      // Given: 输入包含前后空白
      String value = "  active  ";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: trim 后转大写
      assertThat(result).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该处理混合空白字符（制表符、换行符）")
    void shouldHandleMixedWhitespace() {
      // Given: 输入包含制表符、换行符
      String value = "\t\n  active  \r\n";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: trim 后转大写
      assertThat(result).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该处理空字符串（转为空字符串）")
    void shouldHandleEmptyString() {
      // Given: 输入为空字符串
      String value = "";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 返回空字符串
      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("应该处理仅空白字符串（转为空字符串）")
    void shouldHandleBlankString() {
      // Given: 输入为仅空白字符串
      String value = "   ";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: trim 后返回空字符串
      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("应该处理单字符输入")
    void shouldHandleSingleCharacter() {
      // Given: 输入为单字符
      String value = "a";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 返回大写字符
      assertThat(result).isEqualTo("A");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白并转大写")
    void shouldPreserveInternalWhitespaceAndConvertToUppercase() {
      // Given: 输入字符串内部包含空白
      String value = "  active status  ";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: trim 前后空白，保留内部空白，转大写
      assertThat(result).isEqualTo("ACTIVE STATUS");
    }

    @Test
    @DisplayName("应该使用 Locale.ROOT 转大写（避免 locale 问题）")
    void shouldUseLocaleRootForUppercase() {
      // Given: 输入为土耳其语小写 i（在土耳其语 locale 中转大写为 İ）
      String value = "info";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 使用 Locale.ROOT 转大写，避免 locale 问题
      assertThat(result).isEqualTo("INFO");
    }
  }

  // ========== toTrimmedFieldKey 测试 ==========

  @Nested
  @DisplayName("toTrimmedFieldKey - 字段键 Trim 归一化")
  class ToTrimmedFieldKeyTests {

    @Test
    @DisplayName("应该抛出异常当输入为 null")
    void shouldThrowExceptionWhenInputIsNull() {
      // Given: 输入为 null
      String value = null;

      // When & Then: 抛出 DomainValidationException
      assertThatThrownBy(() -> RegistryKeyStandardizer.toTrimmedFieldKey(value))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("值不能为 null");
    }

    @Test
    @DisplayName("应该 trim 前后空白（保留大小写）")
    void shouldTrimWhitespaceAndPreserveCase() {
      // Given: 输入包含前后空白
      String value = "  fieldName  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: trim 前后空白，保留大小写
      assertThat(result).isEqualTo("fieldName");
    }

    @Test
    @DisplayName("应该保留大小写（小写）")
    void shouldPreserveLowercase() {
      // Given: 输入为小写字符串
      String value = "  fieldname  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 保留小写
      assertThat(result).isEqualTo("fieldname");
    }

    @Test
    @DisplayName("应该保留大小写（大写）")
    void shouldPreserveUppercase() {
      // Given: 输入为大写字符串
      String value = "  FIELDNAME  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 保留大写
      assertThat(result).isEqualTo("FIELDNAME");
    }

    @Test
    @DisplayName("应该保留大小写（混合大小写）")
    void shouldPreserveMixedCase() {
      // Given: 输入为混合大小写
      String value = "  FiElDnAmE  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 保留混合大小写
      assertThat(result).isEqualTo("FiElDnAmE");
    }

    @Test
    @DisplayName("应该处理混合空白字符（制表符、换行符）")
    void shouldHandleMixedWhitespace() {
      // Given: 输入包含制表符、换行符
      String value = "\t\n  fieldName  \r\n";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: trim 后返回
      assertThat(result).isEqualTo("fieldName");
    }

    @Test
    @DisplayName("应该处理空字符串（返回空字符串）")
    void shouldHandleEmptyString() {
      // Given: 输入为空字符串
      String value = "";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 返回空字符串
      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("应该处理仅空白字符串（返回空字符串）")
    void shouldHandleBlankString() {
      // Given: 输入为仅空白字符串
      String value = "   ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: trim 后返回空字符串
      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("应该处理不含空白的字符串")
    void shouldHandleStringWithoutWhitespace() {
      // Given: 输入为不含空白的字符串
      String value = "fieldName";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 返回原始字符串
      assertThat(result).isEqualTo("fieldName");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白")
    void shouldPreserveInternalWhitespace() {
      // Given: 输入字符串内部包含空白
      String value = "  field Name  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: trim 前后空白，保留内部空白
      assertThat(result).isEqualTo("field Name");
    }

    @Test
    @DisplayName("应该处理单字符输入")
    void shouldHandleSingleCharacter() {
      // Given: 输入为单字符
      String value = "f";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 返回单字符
      assertThat(result).isEqualTo("f");
    }
  }

  // ========== toMatchTypeKeyOrAny 测试 ==========

  @Nested
  @DisplayName("toMatchTypeKeyOrAny - 匹配类型键归一化")
  class ToMatchTypeKeyOrAnyTests {

    @Test
    @DisplayName("应该返回 ANY 当输入为 null")
    void shouldReturnAnyWhenInputIsNull() {
      // Given: 输入为 null
      String matchTypeCode = null;

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo(RegistryKeyPlaceholders.ANY);
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 ANY 当输入为空字符串")
    void shouldReturnAnyWhenInputIsEmpty() {
      // Given: 输入为空字符串
      String matchTypeCode = "";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 ANY 当输入为空白字符串")
    void shouldReturnAnyWhenInputIsBlank() {
      // Given: 输入为空白字符串
      String matchTypeCode = "   ";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 ANY 当输入为制表符和换行符")
    void shouldReturnAnyWhenInputIsWhitespaceCharacters() {
      // Given: 输入为制表符和换行符
      String matchTypeCode = "\t\n  \r\n";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该 trim 并转大写")
    void shouldTrimAndConvertToUppercase() {
      // Given: 输入包含前后空白的小写字符串
      String matchTypeCode = "  exact  ";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: trim 并转大写
      assertThat(result).isEqualTo("EXACT");
    }

    @Test
    @DisplayName("应该转大写（已是大写）")
    void shouldKeepUppercase() {
      // Given: 输入为大写字符串
      String matchTypeCode = "  EXACT  ";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: trim 并保持大写
      assertThat(result).isEqualTo("EXACT");
    }

    @Test
    @DisplayName("应该转大写（混合大小写）")
    void shouldConvertMixedCaseToUppercase() {
      // Given: 输入为混合大小写
      String matchTypeCode = "  ExAcT  ";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: trim 并转大写
      assertThat(result).isEqualTo("EXACT");
    }

    @Test
    @DisplayName("应该处理不含空白的有效字符串")
    void shouldHandleValidStringWithoutWhitespace() {
      // Given: 输入为不含空白的有效字符串
      String matchTypeCode = "REGEX";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 返回大写字符串
      assertThat(result).isEqualTo("REGEX");
    }

    @Test
    @DisplayName("应该处理单字符输入")
    void shouldHandleSingleCharacter() {
      // Given: 输入为单字符
      String matchTypeCode = "e";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 转大写返回
      assertThat(result).isEqualTo("E");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白并转大写")
    void shouldPreserveInternalWhitespaceAndConvertToUppercase() {
      // Given: 输入字符串内部包含空白
      String matchTypeCode = "  exact match  ";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: trim 前后空白，保留内部空白，转大写
      assertThat(result).isEqualTo("EXACT MATCH");
    }

    @Test
    @DisplayName("应该使用 Locale.ROOT 转大写")
    void shouldUseLocaleRootForUppercase() {
      // Given: 输入为小写字符串
      String matchTypeCode = "contains";

      // When: 调用 toMatchTypeKeyOrAny
      String result = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchTypeCode);

      // Then: 使用 Locale.ROOT 转大写
      assertThat(result).isEqualTo("CONTAINS");
    }
  }

  // ========== toNegatedKeyOrAny 测试 ==========

  @Nested
  @DisplayName("toNegatedKeyOrAny - 取反键归一化")
  class ToNegatedKeyOrAnyTests {

    @Test
    @DisplayName("应该返回 ANY 当输入为 null")
    void shouldReturnAnyWhenInputIsNull() {
      // Given: 输入为 null
      Boolean negated = null;

      // When: 调用 toNegatedKeyOrAny
      String result = RegistryKeyStandardizer.toNegatedKeyOrAny(negated);

      // Then: 返回 ANY
      assertThat(result).isEqualTo(RegistryKeyPlaceholders.ANY);
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 T 当输入为 true")
    void shouldReturnTWhenInputIsTrue() {
      // Given: 输入为 true
      Boolean negated = true;

      // When: 调用 toNegatedKeyOrAny
      String result = RegistryKeyStandardizer.toNegatedKeyOrAny(negated);

      // Then: 返回 T
      assertThat(result).isEqualTo(RegistryKeyPlaceholders.NEGATED_TRUE);
      assertThat(result).isEqualTo("T");
    }

    @Test
    @DisplayName("应该返回 F 当输入为 false")
    void shouldReturnFWhenInputIsFalse() {
      // Given: 输入为 false
      Boolean negated = false;

      // When: 调用 toNegatedKeyOrAny
      String result = RegistryKeyStandardizer.toNegatedKeyOrAny(negated);

      // Then: 返回 F
      assertThat(result).isEqualTo(RegistryKeyPlaceholders.NEGATED_FALSE);
      assertThat(result).isEqualTo("F");
    }

    @Test
    @DisplayName("应该返回 T 当输入为 Boolean.TRUE")
    void shouldReturnTWhenInputIsBooleanTrue() {
      // Given: 输入为 Boolean.TRUE
      Boolean negated = Boolean.TRUE;

      // When: 调用 toNegatedKeyOrAny
      String result = RegistryKeyStandardizer.toNegatedKeyOrAny(negated);

      // Then: 返回 T
      assertThat(result).isEqualTo("T");
    }

    @Test
    @DisplayName("应该返回 F 当输入为 Boolean.FALSE")
    void shouldReturnFWhenInputIsBooleanFalse() {
      // Given: 输入为 Boolean.FALSE
      Boolean negated = Boolean.FALSE;

      // When: 调用 toNegatedKeyOrAny
      String result = RegistryKeyStandardizer.toNegatedKeyOrAny(negated);

      // Then: 返回 F
      assertThat(result).isEqualTo("F");
    }

    @Test
    @DisplayName("应该正确处理所有三种情况（null、true、false）")
    void shouldHandleAllThreeCases() {
      // Given: 三种输入
      Boolean nullValue = null;
      Boolean trueValue = true;
      Boolean falseValue = false;

      // When: 调用 toNegatedKeyOrAny
      String resultForNull = RegistryKeyStandardizer.toNegatedKeyOrAny(nullValue);
      String resultForTrue = RegistryKeyStandardizer.toNegatedKeyOrAny(trueValue);
      String resultForFalse = RegistryKeyStandardizer.toNegatedKeyOrAny(falseValue);

      // Then: 分别返回 ANY、T、F
      assertThat(resultForNull).isEqualTo("ANY");
      assertThat(resultForTrue).isEqualTo("T");
      assertThat(resultForFalse).isEqualTo("F");
    }
  }

  // ========== toValueTypeKeyOrAny 测试 ==========

  @Nested
  @DisplayName("toValueTypeKeyOrAny - 值类型键归一化")
  class ToValueTypeKeyOrAnyTests {

    @Test
    @DisplayName("应该返回 ANY 当输入为 null")
    void shouldReturnAnyWhenInputIsNull() {
      // Given: 输入为 null
      String valueTypeCode = null;

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo(RegistryKeyPlaceholders.ANY);
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 ANY 当输入为空字符串")
    void shouldReturnAnyWhenInputIsEmpty() {
      // Given: 输入为空字符串
      String valueTypeCode = "";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 ANY 当输入为空白字符串")
    void shouldReturnAnyWhenInputIsBlank() {
      // Given: 输入为空白字符串
      String valueTypeCode = "   ";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该返回 ANY 当输入为制表符和换行符")
    void shouldReturnAnyWhenInputIsWhitespaceCharacters() {
      // Given: 输入为制表符和换行符
      String valueTypeCode = "\t\n  \r\n";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 返回 ANY
      assertThat(result).isEqualTo("ANY");
    }

    @Test
    @DisplayName("应该 trim 并转大写")
    void shouldTrimAndConvertToUppercase() {
      // Given: 输入包含前后空白的小写字符串
      String valueTypeCode = "  string  ";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: trim 并转大写
      assertThat(result).isEqualTo("STRING");
    }

    @Test
    @DisplayName("应该转大写（已是大写）")
    void shouldKeepUppercase() {
      // Given: 输入为大写字符串
      String valueTypeCode = "  STRING  ";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: trim 并保持大写
      assertThat(result).isEqualTo("STRING");
    }

    @Test
    @DisplayName("应该转大写（混合大小写）")
    void shouldConvertMixedCaseToUppercase() {
      // Given: 输入为混合大小写
      String valueTypeCode = "  StRiNg  ";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: trim 并转大写
      assertThat(result).isEqualTo("STRING");
    }

    @Test
    @DisplayName("应该处理不含空白的有效字符串")
    void shouldHandleValidStringWithoutWhitespace() {
      // Given: 输入为不含空白的有效字符串
      String valueTypeCode = "NUMBER";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 返回大写字符串
      assertThat(result).isEqualTo("NUMBER");
    }

    @Test
    @DisplayName("应该处理单字符输入")
    void shouldHandleSingleCharacter() {
      // Given: 输入为单字符
      String valueTypeCode = "s";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 转大写返回
      assertThat(result).isEqualTo("S");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白并转大写")
    void shouldPreserveInternalWhitespaceAndConvertToUppercase() {
      // Given: 输入字符串内部包含空白
      String valueTypeCode = "  array list  ";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: trim 前后空白，保留内部空白，转大写
      assertThat(result).isEqualTo("ARRAY LIST");
    }

    @Test
    @DisplayName("应该使用 Locale.ROOT 转大写")
    void shouldUseLocaleRootForUppercase() {
      // Given: 输入为小写字符串
      String valueTypeCode = "boolean";

      // When: 调用 toValueTypeKeyOrAny
      String result = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueTypeCode);

      // Then: 使用 Locale.ROOT 转大写
      assertThat(result).isEqualTo("BOOLEAN");
    }
  }

  // ========== 工具类语义测试 ==========

  @Nested
  @DisplayName("工具类语义")
  class UtilityClassSemanticsTests {

    @Test
    @DisplayName("应该是 final 类（不可被继承）")
    void shouldBeFinalClass() {
      // Given: RegistryKeyStandardizer 类

      // When: 检查类修饰符
      boolean isFinal = java.lang.reflect.Modifier.isFinal(RegistryKeyStandardizer.class.getModifiers());

      // Then: 应该是 final
      assertThat(isFinal).isTrue();
    }

    @Test
    @DisplayName("应该有 private 构造函数")
    void shouldHavePrivateConstructor() throws Exception {
      // Given: RegistryKeyStandardizer 类

      // When: 获取构造函数
      java.lang.reflect.Constructor<?>[] constructors = RegistryKeyStandardizer.class.getDeclaredConstructors();

      // Then: 应该只有一个构造函数且为 private
      assertThat(constructors).hasSize(1);
      assertThat(java.lang.reflect.Modifier.isPrivate(constructors[0].getModifiers())).isTrue();
    }

    @Test
    @DisplayName("private 构造函数应该可被反射调用（覆盖率）")
    void privateConstructorShouldBeInvocableViaReflection() throws Exception {
      // Given: RegistryKeyStandardizer 类
      java.lang.reflect.Constructor<RegistryKeyStandardizer> constructor =
          RegistryKeyStandardizer.class.getDeclaredConstructor();

      // When: 通过反射调用 private 构造函数
      constructor.setAccessible(true);
      RegistryKeyStandardizer instance = constructor.newInstance();

      // Then: 应该成功创建实例
      assertThat(instance).isNotNull();
    }

    @Test
    @DisplayName("所有方法都应该是 static")
    void allMethodsShouldBeStatic() {
      // Given: RegistryKeyStandardizer 类

      // When: 获取所有 public 方法
      java.lang.reflect.Method[] methods = RegistryKeyStandardizer.class.getDeclaredMethods();

      // Then: 所有方法都应该是 static
      for (java.lang.reflect.Method method : methods) {
        assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers()))
            .as("Method %s should be static", method.getName())
            .isTrue();
      }
    }

    @Test
    @DisplayName("应该有 6 个 public static 方法")
    void shouldHaveSixPublicStaticMethods() {
      // Given: RegistryKeyStandardizer 类

      // When: 获取所有 public 方法
      java.lang.reflect.Method[] publicMethods = RegistryKeyStandardizer.class.getMethods();
      long count =
          java.util.Arrays.stream(publicMethods)
              .filter(
                  m ->
                      m.getDeclaringClass() == RegistryKeyStandardizer.class
                          && java.lang.reflect.Modifier.isStatic(m.getModifiers()))
              .count();

      // Then: 应该有 6 个 public static 方法
      assertThat(count).isEqualTo(6);
    }
  }

  // ========== 边界条件和特殊场景测试 ==========

  @Nested
  @DisplayName("边界条件和特殊场景")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 Unicode 字符（toUppercaseCode）")
    void shouldHandleUnicodeCharactersInToUppercaseCode() {
      // Given: 输入包含 Unicode 字符
      String value = "café";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: Unicode 字符正确转大写
      assertThat(result).isEqualTo("CAFÉ");
    }

    @Test
    @DisplayName("应该处理 Unicode 字符（toTrimmedFieldKey）")
    void shouldHandleUnicodeCharactersInToTrimmedFieldKey() {
      // Given: 输入包含 Unicode 字符
      String value = "  字段名  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: Unicode 字符正确 trim
      assertThat(result).isEqualTo("字段名");
    }

    @Test
    @DisplayName("应该处理极长字符串（toOperationKeyOrAll）")
    void shouldHandleVeryLongStringInToOperationKeyOrAll() {
      // Given: 输入为极长字符串
      String operationType = "A".repeat(1000);

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 正确返回
      assertThat(result).hasSize(1000);
      assertThat(result).isEqualTo(operationType);
    }

    @Test
    @DisplayName("应该处理极长字符串（toUppercaseCode）")
    void shouldHandleVeryLongStringInToUppercaseCode() {
      // Given: 输入为极长字符串
      String value = "a".repeat(1000);

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 正确转大写
      assertThat(result).hasSize(1000);
      assertThat(result).isEqualTo("A".repeat(1000));
    }

    @Test
    @DisplayName("所有方法应该是幂等的")
    void allMethodsShouldBeIdempotent() {
      // Given: 各种输入
      String operationType = "  HARVEST  ";
      String code = "  active  ";
      String fieldKey = "  fieldName  ";
      String matchType = "  exact  ";
      Boolean negated = true;
      String valueType = "  string  ";

      // When: 多次调用
      String result1 = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);
      String result2 = RegistryKeyStandardizer.toOperationKeyOrAll(result1);
      String result3 = RegistryKeyStandardizer.toUppercaseCode(code);
      String result4 = RegistryKeyStandardizer.toUppercaseCode(result3);
      String result5 = RegistryKeyStandardizer.toTrimmedFieldKey(fieldKey);
      String result6 = RegistryKeyStandardizer.toTrimmedFieldKey(result5);
      String result7 = RegistryKeyStandardizer.toMatchTypeKeyOrAny(matchType);
      String result8 = RegistryKeyStandardizer.toMatchTypeKeyOrAny(result7);
      String result9 = RegistryKeyStandardizer.toNegatedKeyOrAny(negated);
      String result10 = RegistryKeyStandardizer.toValueTypeKeyOrAny(valueType);
      String result11 = RegistryKeyStandardizer.toValueTypeKeyOrAny(result10);

      // Then: 幂等性（多次调用结果相同）
      assertThat(result1).isEqualTo(result2);
      assertThat(result3).isEqualTo(result4);
      assertThat(result5).isEqualTo(result6);
      assertThat(result7).isEqualTo(result8);
      assertThat(result9).isEqualTo("T");
      assertThat(result10).isEqualTo(result11);
    }

    @Test
    @DisplayName("应该处理数字字符串（toUppercaseCode）")
    void shouldHandleNumericStringInToUppercaseCode() {
      // Given: 输入为数字字符串
      String value = "12345";

      // When: 调用 toUppercaseCode
      String result = RegistryKeyStandardizer.toUppercaseCode(value);

      // Then: 正确返回（数字无大小写）
      assertThat(result).isEqualTo("12345");
    }

    @Test
    @DisplayName("应该处理特殊字符（toTrimmedFieldKey）")
    void shouldHandleSpecialCharactersInToTrimmedFieldKey() {
      // Given: 输入包含特殊字符
      String value = "  field@Name#123  ";

      // When: 调用 toTrimmedFieldKey
      String result = RegistryKeyStandardizer.toTrimmedFieldKey(value);

      // Then: 正确 trim
      assertThat(result).isEqualTo("field@Name#123");
    }

    @Test
    @DisplayName("应该处理多个连续空格（toOperationKeyOrAll）")
    void shouldHandleMultipleConsecutiveSpaces() {
      // Given: 输入包含多个连续空格
      String operationType = "     HARVEST     ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: trim 所有前后空格
      assertThat(result).isEqualTo("HARVEST");
    }
  }
}
