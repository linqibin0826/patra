package com.patra.registry.domain.exception;

/// 领域范围的验证异常（用于替代分散的 IllegalArgumentException 使用）。
///
/// 典型场景：构造领域对象、强制不变性、验证查询视图参数。
/// 这表示调用者提供的无效输入，而非内部系统错误。
///
/// **自动语义**: 所有验证异常自动携带 {@link com.patra.common.error.trait.StandardErrorTrait#RULE_VIOLATION} 特征。
///
/// **指南**:
///
/// - 适配器/网关自动映射为 HTTP 422 (Unprocessable Entity)
/// - 通过日志过滤预期的验证失败，有助于减少告警噪音
/// - 此类提供便捷的静态工厂方法用于常见验证场景
///
/// @author linqibin
/// @since 0.1.0
public class DomainValidationException extends RegistryRuleViolation {

  /// 构造一个包含指定错误消息的领域验证异常。
  ///
  /// @param message 描述验证失败原因的错误消息
  public DomainValidationException(String message) {
    super(message);
  }

  /// 构造一个包含指定错误消息和原因的领域验证异常。
  ///
  /// @param message 描述验证失败原因的错误消息
  /// @param cause 导致此异常的底层原因
  public DomainValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /// 便捷工厂方法，当条件为 false 时抛出异常。
  ///
  /// @param condition 要检查的布尔条件
  /// @param message 条件失败时的错误消息
  /// @throws DomainValidationException 当条件为 false 时
  public static void require(boolean condition, String message) {
    if (!condition) {
      throw new DomainValidationException(message);
    }
  }

  /// 断言字符串非空且非空白，返回修剪后的值。
  ///
  /// @param value 要检查的值
  /// @param field 字段名称（用于组合消息）
  /// @return 验证通过时的修剪值
  /// @throws DomainValidationException 当 value 为 null 或空白时
  public static String notBlank(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new DomainValidationException(field + " 不能为空白");
    }
    return value.trim();
  }

  /// 断言对象非空。
  ///
  /// @param obj 要检查的对象
  /// @param field 错误消息的字段名称
  /// @param <T> 对象类型
  /// @return 验证通过时的对象
  /// @throws DomainValidationException 当对象为 null 时
  public static <T> T nonNull(T obj, String field) {
    if (obj == null) {
      throw new DomainValidationException(field + " 不能为 null");
    }
    return obj;
  }

  /// 断言数字为正数（大于 0）。
  ///
  /// @param number 要检查的数值
  /// @param field 错误消息的字段名称
  /// @return 验证通过时的数字
  /// @throws DomainValidationException 当 number 为 null 或不为正数时
  public static long positive(Long number, String field) {
    if (number == null || number <= 0) {
      throw new DomainValidationException(field + " 必须为正数");
    }
    return number;
  }

  /// 断言整数非负（大于或等于 0）。
  ///
  /// @param number 要检查的数值
  /// @param field 错误消息的字段名称
  /// @return 验证通过时的数字
  /// @throws DomainValidationException 当 number 为 null 或为负数时
  public static int nonNegative(Integer number, String field) {
    if (number == null || number < 0) {
      throw new DomainValidationException(field + " 不能为负数");
    }
    return number;
  }

  /// 断言数组非空（仅检查 null 或 length == 0）。
  ///
  /// @param arr 要检查的数组
  /// @param field 错误消息的字段名称
  /// @param <T> 数组元素类型
  /// @return 验证通过时的数组
  /// @throws DomainValidationException 当数组为 null 或空时
  public static <T> T[] notEmpty(T[] arr, String field) {
    if (arr == null || arr.length == 0) {
      throw new DomainValidationException(field + " 不能为空");
    }
    return arr;
  }

  /// 断言值在包含性范围 [min, max] 内。
  ///
  /// @param value 要检查的值
  /// @param minInclusive 最小允许值（包含）
  /// @param maxInclusive 最大允许值（包含）
  /// @param field 错误消息的字段名称
  /// @return 验证通过时的值
  /// @throws DomainValidationException 当值超出范围时
  public static long withinRange(long value, long minInclusive, long maxInclusive, String field) {
    if (value < minInclusive || value > maxInclusive) {
      throw new DomainValidationException(
          field + " 必须在 " + minInclusive + " 和 " + maxInclusive + " 之间");
    }
    return value;
  }

  /// 返回修剪后的字符串，如果输入为 null 则返回 null。
  ///
  /// 用于归一化可为 null 的字符串字段的实用方法。
  ///
  /// @param value 要修剪的字符串
  /// @return 修剪后的字符串或 null
  public static String trimOrNull(String value) {
    return value != null ? value.trim() : null;
  }
}
