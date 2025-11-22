package com.patra.registry.domain.support;

import com.patra.registry.domain.exception.DomainValidationException;
import java.util.Locale;

/// 注册中心维度/条件键的标准化工具。
///
/// 确保维度键(operation_type/field_key/code等)满足跨层约束, 实现稳定的哈希/查找,避免 NULL 歧义,支持架构版本兼容性。
///
/// @author linqibin
/// @since 0.1.0
public final class RegistryKeyStandardizer {

  private RegistryKeyStandardizer() {}

  // -----------------------------
  // 描述性 API(推荐使用)
  // -----------------------------

  /// 返回归一化的操作键,当输入为 null/空白时返回 `ALL`。
  ///
  /// 统一转换为大写以确保标准化和一致性。
  ///
  /// @param operationType 原始操作类型
  /// @return 修剪并转为大写的操作键或 `ALL`
  public static String toOperationKeyOrAll(String operationType) {
    if (operationType == null || operationType.isBlank()) {
      return RegistryKeyPlaceholders.ALL;
    }
    return operationType.trim().toUpperCase(Locale.ROOT);
  }

  /// 返回修剪后的大写代码,输入为 null 时抛出异常。
  ///
  /// 用于需要不区分大小写比较的字典/状态代码。
  ///
  /// @param value 原始代码值
  /// @return 大写代码(已修剪)
  /// @throws DomainValidationException 当 value 为 null 时
  public static String toUppercaseCode(String value) {
    if (value == null) {
      throw new DomainValidationException("值不能为 null");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  /// 返回修剪后的字段键,输入为 null 时抛出异常。保留大小写。
  ///
  /// @param value 原始字段键
  /// @return 修剪后的字段键
  /// @throws DomainValidationException 当 value 为 null 时
  public static String toTrimmedFieldKey(String value) {
    if (value == null) {
      throw new DomainValidationException("值不能为 null");
    }
    return value.trim();
  }

  /// 返回归一化的匹配类型键,当输入为 null/空白时返回 `ANY`。
  ///
  /// @param matchTypeCode 原始匹配类型代码
  /// @return 大写匹配类型键或 `ANY`
  public static String toMatchTypeKeyOrAny(String matchTypeCode) {
    if (matchTypeCode == null || matchTypeCode.isBlank()) {
      return RegistryKeyPlaceholders.ANY;
    }
    return matchTypeCode.trim().toUpperCase(Locale.ROOT);
  }

  /// 返回归一化的取反键: null 返回 `ANY`,true 返回 `T`,false 返回 `F`。
  ///
  /// @param negated 原始取反标志
  /// @return `ANY`/`T`/`F`
  public static String toNegatedKeyOrAny(Boolean negated) {
    if (negated == null) {
      return RegistryKeyPlaceholders.ANY;
    }
    return Boolean.TRUE.equals(negated)
        ? RegistryKeyPlaceholders.NEGATED_TRUE
        : RegistryKeyPlaceholders.NEGATED_FALSE;
  }

  /// 返回归一化的值类型键,当输入为 null/空白时返回 `ANY`。
  ///
  /// @param valueTypeCode 原始值类型代码
  /// @return 大写值类型键或 `ANY`
  public static String toValueTypeKeyOrAny(String valueTypeCode) {
    if (valueTypeCode == null || valueTypeCode.isBlank()) {
      return RegistryKeyPlaceholders.ANY;
    }
    return valueTypeCode.trim().toUpperCase(Locale.ROOT);
  }
}
