package com.patra.registry.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 注册中心规则违规语义的异常基类。
///
/// 表示违反业务规则、验证约束或数据完整性的操作（例如，无效格式、约束冲突）。
///
/// **自动语义**: 所有此类异常自动携带 {@link StandardErrorTrait#RULE_VIOLATION} 特征。
///
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryRuleViolation extends RegistryException {

  /// 使用消息创建规则违规异常。
  ///
  /// @param message 详细消息
  protected RegistryRuleViolation(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 使用消息和根本原因创建规则违规异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  protected RegistryRuleViolation(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
  }
}
