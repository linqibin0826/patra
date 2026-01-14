package com.patra.starter.provenance.common.processor;

import java.util.List;

/// 数据验证结果
///
/// 封装数据验证的结果，包括是否有效和错误列表。
///
/// **设计理念**：
///
/// - 使用Record保证不可变性
///   - 错误列表使用不可变集合
///   - 提供工厂方法简化创建
///
/// @param isValid 是否有效
/// @param errors 错误列表（不可变）
/// @author linqibin
/// @since 0.1.0
public record ValidationResult(boolean isValid, List<String> errors) {

  /// 创建成功的验证结果
  ///
  /// @return 成功的验证结果
  public static ValidationResult success() {
    return new ValidationResult(true, List.of());
  }

  /// 创建失败的验证结果
  ///
  /// @param errors 错误列表
  /// @return 失败的验证结果
  public static ValidationResult failure(List<String> errors) {
    return new ValidationResult(false, List.copyOf(errors));
  }

  /// 创建单个错误的验证结果
  ///
  /// @param error 错误消息
  /// @return 失败的验证结果
  public static ValidationResult failure(String error) {
    return new ValidationResult(false, List.of(error));
  }
}
