package com.patra.registry.domain.support;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("应该返回 trim 后并转大写的字符串（输入为小写）")
    void shouldReturnTrimmedAndUppercaseStringForLowercase() {
      // Given: 输入包含前后空白的小写字符串
      String operationType = "  harvest  ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 返回 trim 后并转大写的字符串
      assertThat(result).isEqualTo("HARVEST");
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
    @DisplayName("应该将混合大小写转为大写")
    void shouldConvertMixedCaseToUppercase() {
      // Given: 输入为混合大小写
      String operationType = "  HaRvEsT  ";

      // When: 调用 toOperationKeyOrAll
      String result = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);

      // Then: 转为大写
      assertThat(result).isEqualTo("HARVEST");
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

  // ========== 参数化测试 - 更全面的场景覆盖 ==========

  @Nested
  @DisplayName("参数化测试 - 全面场景覆盖")
  class ParameterizedTests {

    // toOperationKeyOrAll 参数化测试
    @ParameterizedTest
    @CsvSource({
      "'harvest', 'HARVEST'",
      "'HARVEST', 'HARVEST'",
      "'HaRvEsT', 'HARVEST'",
      "'update', 'UPDATE'",
      "'DELETE', 'DELETE'",
      "'  search  ', 'SEARCH'",
      "'\t\tsync\n\n', 'SYNC'",
      "'export import', 'EXPORT IMPORT'",
      "'a', 'A'",
      "'1', '1'",
      "'harvest-v2', 'HARVEST-V2'",
      "'harvest_update', 'HARVEST_UPDATE'",
      "'harvest.sync', 'HARVEST.SYNC'"
    })
    @DisplayName("toOperationKeyOrAll 应该正确处理各种输入并转大写")
    void toOperationKeyOrAll_shouldHandleVariousInputs(String input, String expected) {
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n", "\r\n", "   \t\n  "})
    @DisplayName("toOperationKeyOrAll 应该对 null/空白字符串返回 ALL")
    void toOperationKeyOrAll_shouldReturnAllForNullOrBlank(String input) {
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll(input))
          .isEqualTo(RegistryKeyPlaceholders.ALL);
    }

    // toUppercaseCode 参数化测试
    @ParameterizedTest
    @CsvSource({
      "'active', 'ACTIVE'",
      "'ACTIVE', 'ACTIVE'",
      "'AcTiVe', 'ACTIVE'",
      "'pending', 'PENDING'",
      "'  completed  ', 'COMPLETED'",
      "'\t\nfailed\r\n', 'FAILED'",
      "'status-code', 'STATUS-CODE'",
      "'status_code', 'STATUS_CODE'",
      "'status.code', 'STATUS.CODE'",
      "'123', '123'",
      "'code123', 'CODE123'",
      "'a', 'A'",
      "'café', 'CAFÉ'",
      "'naïve', 'NAÏVE'"
    })
    @DisplayName("toUppercaseCode 应该正确转换各种输入为大写")
    void toUppercaseCode_shouldConvertToUppercase(String input, String expected) {
      assertThat(RegistryKeyStandardizer.toUppercaseCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t\n", "   \t\r\n  "})
    @DisplayName("toUppercaseCode 应该处理空白字符串（返回空字符串）")
    void toUppercaseCode_shouldHandleBlankStrings(String input) {
      assertThat(RegistryKeyStandardizer.toUppercaseCode(input)).isEmpty();
    }

    // toTrimmedFieldKey 参数化测试
    @ParameterizedTest
    @CsvSource({
      "'fieldName', 'fieldName'",
      "'FIELDNAME', 'FIELDNAME'",
      "'field_name', 'field_name'",
      "'field-name', 'field-name'",
      "'field.name', 'field.name'",
      "'  fieldName  ', 'fieldName'",
      "'\t\nfieldName\r\n', 'fieldName'",
      "'field Name', 'field Name'",
      "'a', 'a'",
      "'123', '123'",
      "'field123', 'field123'",
      "'字段名', '字段名'",
      "'  字段名  ', '字段名'"
    })
    @DisplayName("toTrimmedFieldKey 应该正确 trim 并保留大小写")
    void toTrimmedFieldKey_shouldTrimAndPreserveCase(String input, String expected) {
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t\n", "   \t\r\n  "})
    @DisplayName("toTrimmedFieldKey 应该处理空白字符串（返回空字符串）")
    void toTrimmedFieldKey_shouldHandleBlankStrings(String input) {
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey(input)).isEmpty();
    }

    // toMatchTypeKeyOrAny 参数化测试
    @ParameterizedTest
    @CsvSource({
      "'exact', 'EXACT'",
      "'EXACT', 'EXACT'",
      "'regex', 'REGEX'",
      "'contains', 'CONTAINS'",
      "'  prefix  ', 'PREFIX'",
      "'SUFFIX', 'SUFFIX'",
      "'\t\npattern\r\n', 'PATTERN'",
      "'a', 'A'",
      "'match type', 'MATCH TYPE'"
    })
    @DisplayName("toMatchTypeKeyOrAny 应该正确转换为大写")
    void toMatchTypeKeyOrAny_shouldConvertToUppercase(String input, String expected) {
      assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n", "   \t\r\n  "})
    @DisplayName("toMatchTypeKeyOrAny 应该对 null/空白字符串返回 ANY")
    void toMatchTypeKeyOrAny_shouldReturnAnyForNullOrBlank(String input) {
      assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny(input))
          .isEqualTo(RegistryKeyPlaceholders.ANY);
    }

    // toValueTypeKeyOrAny 参数化测试
    @ParameterizedTest
    @CsvSource({
      "'string', 'STRING'",
      "'STRING', 'STRING'",
      "'number', 'NUMBER'",
      "'boolean', 'BOOLEAN'",
      "'  array  ', 'ARRAY'",
      "'OBJECT', 'OBJECT'",
      "'\t\ndate\r\n', 'DATE'",
      "'a', 'A'",
      "'value type', 'VALUE TYPE'"
    })
    @DisplayName("toValueTypeKeyOrAny 应该正确转换为大写")
    void toValueTypeKeyOrAny_shouldConvertToUppercase(String input, String expected) {
      assertThat(RegistryKeyStandardizer.toValueTypeKeyOrAny(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n", "   \t\r\n  "})
    @DisplayName("toValueTypeKeyOrAny 应该对 null/空白字符串返回 ANY")
    void toValueTypeKeyOrAny_shouldReturnAnyForNullOrBlank(String input) {
      assertThat(RegistryKeyStandardizer.toValueTypeKeyOrAny(input))
          .isEqualTo(RegistryKeyPlaceholders.ANY);
    }
  }

  // ========== 特殊字符和国际化测试 ==========

  @Nested
  @DisplayName("特殊字符和国际化场景")
  class SpecialCharactersAndI18nTests {

    @Test
    @DisplayName("应该正确处理 Unicode 字符（中文）")
    void shouldHandleChineseCharacters() {
      // toUppercaseCode
      assertThat(RegistryKeyStandardizer.toUppercaseCode("  状态  ")).isEqualTo("状态");

      // toTrimmedFieldKey
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey("  字段名  ")).isEqualTo("字段名");

      // toMatchTypeKeyOrAny
      assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny("  匹配  ")).isEqualTo("匹配");

      // toValueTypeKeyOrAny
      assertThat(RegistryKeyStandardizer.toValueTypeKeyOrAny("  类型  ")).isEqualTo("类型");
    }

    @Test
    @DisplayName("应该正确处理带音标的拉丁字符")
    void shouldHandleAccentedLatinCharacters() {
      assertThat(RegistryKeyStandardizer.toUppercaseCode("café")).isEqualTo("CAFÉ");
      assertThat(RegistryKeyStandardizer.toUppercaseCode("naïve")).isEqualTo("NAÏVE");
      assertThat(RegistryKeyStandardizer.toUppercaseCode("résumé")).isEqualTo("RÉSUMÉ");
      assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny("ñoño")).isEqualTo("ÑOÑO");
    }

    @Test
    @DisplayName("应该正确处理特殊符号")
    void shouldHandleSpecialSymbols() {
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll("harvest-v2"))
          .isEqualTo("HARVEST-V2");
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll("harvest_update"))
          .isEqualTo("HARVEST_UPDATE");
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll("harvest.sync"))
          .isEqualTo("HARVEST.SYNC");
      assertThat(RegistryKeyStandardizer.toUppercaseCode("status@123")).isEqualTo("STATUS@123");
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey("field#name"))
          .isEqualTo("field#name");
    }

    @Test
    @DisplayName("应该正确处理数字和字母混合")
    void shouldHandleAlphanumeric() {
      assertThat(RegistryKeyStandardizer.toUppercaseCode("code123")).isEqualTo("CODE123");
      assertThat(RegistryKeyStandardizer.toUppercaseCode("v2.0.1")).isEqualTo("V2.0.1");
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey("field1")).isEqualTo("field1");
      assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny("regex2")).isEqualTo("REGEX2");
    }

    @Test
    @DisplayName("应该正确处理 Emoji 字符")
    void shouldHandleEmojiCharacters() {
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey("  field🔥name  "))
          .isEqualTo("field🔥name");
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll("  harvest✅  "))
          .isEqualTo("HARVEST✅");
    }

    @Test
    @DisplayName("应该使用 Locale.ROOT 避免土耳其语 locale 问题")
    void shouldUseLocaleRootToAvoidTurkishLocaleProblem() {
      // 在土耳其语 locale 中，小写 'i' 转大写为 'İ'（带点的大写I）
      // 使用 Locale.ROOT 应该转换为标准的 'I'
      assertThat(RegistryKeyStandardizer.toUppercaseCode("info")).isEqualTo("INFO");
      assertThat(RegistryKeyStandardizer.toUppercaseCode("index")).isEqualTo("INDEX");
      assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny("filter")).isEqualTo("FILTER");
    }
  }

  // ========== 性能和压力测试 ==========

  @Nested
  @DisplayName("性能和压力测试")
  class PerformanceTests {

    @Test
    @DisplayName("应该高效处理极长字符串（10000 字符）")
    void shouldHandleVeryLongStringsEfficiently() {
      // Given: 10000 字符的字符串
      String longString = "a".repeat(10000);

      // When: 调用各个方法
      long startTime = System.nanoTime();
      String result1 = RegistryKeyStandardizer.toOperationKeyOrAll(longString);
      String result2 = RegistryKeyStandardizer.toUppercaseCode(longString);
      String result3 = RegistryKeyStandardizer.toTrimmedFieldKey(longString);
      String result4 = RegistryKeyStandardizer.toMatchTypeKeyOrAny(longString);
      String result5 = RegistryKeyStandardizer.toValueTypeKeyOrAny(longString);
      long endTime = System.nanoTime();

      // Then: 应该快速完成（< 10ms）
      long durationMs = (endTime - startTime) / 1_000_000;
      assertThat(durationMs).isLessThan(10);

      // 验证结果正确
      assertThat(result1).hasSize(10000).isEqualTo("A".repeat(10000));
      assertThat(result2).hasSize(10000);
      assertThat(result3).hasSize(10000).isEqualTo(longString);
      assertThat(result4).hasSize(10000);
      assertThat(result5).hasSize(10000);
    }

    @Test
    @DisplayName("应该高效处理带空白的极长字符串")
    void shouldHandleVeryLongStringsWithWhitespaceEfficiently() {
      // Given: 前后有大量空白的长字符串
      String longString = " ".repeat(1000) + "test" + " ".repeat(1000);

      // When & Then: 应该快速完成
      assertThat(RegistryKeyStandardizer.toOperationKeyOrAll(longString)).isEqualTo("TEST");
      assertThat(RegistryKeyStandardizer.toUppercaseCode(longString)).isEqualTo("TEST");
      assertThat(RegistryKeyStandardizer.toTrimmedFieldKey(longString)).isEqualTo("test");
    }
  }

  // ========== 交叉场景和组合测试 ==========

  @Nested
  @DisplayName("交叉场景和组合测试")
  class CrossScenarioTests {

    @Test
    @DisplayName("所有转大写的方法应该产生一致的结果")
    void uppercaseMethodsShouldProduceConsistentResults() {
      // Given: 相同的输入
      String input = "  test  ";

      // When: 调用所有转大写的方法
      String result1 = RegistryKeyStandardizer.toOperationKeyOrAll(input);
      String result2 = RegistryKeyStandardizer.toUppercaseCode(input);
      String result3 = RegistryKeyStandardizer.toMatchTypeKeyOrAny(input);
      String result4 = RegistryKeyStandardizer.toValueTypeKeyOrAny(input);

      // Then: 所有结果应该相同
      assertThat(result1).isEqualTo("TEST");
      assertThat(result2).isEqualTo("TEST");
      assertThat(result3).isEqualTo("TEST");
      assertThat(result4).isEqualTo("TEST");
    }

    @Test
    @DisplayName("幂等性：连续调用应该产生相同结果")
    void methodsShouldBeIdempotent() {
      // Given: 各种输入
      String op = "  harvest  ";
      String code = "  active  ";
      String field = "  fieldName  ";
      String match = "  exact  ";
      String valueType = "  string  ";

      // When: 连续调用多次
      String opResult1 = RegistryKeyStandardizer.toOperationKeyOrAll(op);
      String opResult2 = RegistryKeyStandardizer.toOperationKeyOrAll(opResult1);
      String opResult3 = RegistryKeyStandardizer.toOperationKeyOrAll(opResult2);

      String codeResult1 = RegistryKeyStandardizer.toUppercaseCode(code);
      String codeResult2 = RegistryKeyStandardizer.toUppercaseCode(codeResult1);

      String fieldResult1 = RegistryKeyStandardizer.toTrimmedFieldKey(field);
      String fieldResult2 = RegistryKeyStandardizer.toTrimmedFieldKey(fieldResult1);

      // Then: 幂等性（结果应该相同）
      assertThat(opResult1).isEqualTo(opResult2).isEqualTo(opResult3);
      assertThat(codeResult1).isEqualTo(codeResult2);
      assertThat(fieldResult1).isEqualTo(fieldResult2);
    }

    @Test
    @DisplayName("不同占位符常量应该是唯一的")
    void placeholderConstantsShouldBeUnique() {
      // Given & When: 获取所有占位符常量
      String all = RegistryKeyPlaceholders.ALL;
      String any = RegistryKeyPlaceholders.ANY;
      String negatedTrue = RegistryKeyPlaceholders.NEGATED_TRUE;
      String negatedFalse = RegistryKeyPlaceholders.NEGATED_FALSE;

      // Then: 应该互不相同
      assertThat(all).isNotEqualTo(any);
      assertThat(negatedTrue).isNotEqualTo(negatedFalse);
      assertThat(negatedTrue).isNotEqualTo(any);
      assertThat(negatedFalse).isNotEqualTo(any);

      // 验证具体值
      assertThat(all).isEqualTo("ALL");
      assertThat(any).isEqualTo("ANY");
      assertThat(negatedTrue).isEqualTo("T");
      assertThat(negatedFalse).isEqualTo("F");
    }

    @Test
    @DisplayName("空白字符串处理的一致性")
    void blankStringHandlingShouldBeConsistent() {
      // Given: 各种空白字符串
      String[] blankStrings = {"", "  ", "\t", "\n", "\r\n", "   \t\n  "};

      for (String blank : blankStrings) {
        // When & Then: 需要返回占位符的方法应该一致
        assertThat(RegistryKeyStandardizer.toOperationKeyOrAll(blank))
            .as("toOperationKeyOrAll with '%s'", blank)
            .isEqualTo("ALL");

        assertThat(RegistryKeyStandardizer.toMatchTypeKeyOrAny(blank))
            .as("toMatchTypeKeyOrAny with '%s'", blank)
            .isEqualTo("ANY");

        assertThat(RegistryKeyStandardizer.toValueTypeKeyOrAny(blank))
            .as("toValueTypeKeyOrAny with '%s'", blank)
            .isEqualTo("ANY");

        // 不接受空白的方法应该返回空字符串
        assertThat(RegistryKeyStandardizer.toUppercaseCode(blank))
            .as("toUppercaseCode with '%s'", blank)
            .isEmpty();

        assertThat(RegistryKeyStandardizer.toTrimmedFieldKey(blank))
            .as("toTrimmedFieldKey with '%s'", blank)
            .isEmpty();
      }
    }
  }
}
