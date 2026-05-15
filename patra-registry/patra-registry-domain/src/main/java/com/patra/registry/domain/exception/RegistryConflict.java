package com.patra.registry.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 注册中心资源冲突异常基类。
///
/// 表示由于与现有资源或规则冲突而无法完成的操作（例如，重复名称、版本冲突）。
///
/// **自动语义**: 所有此类异常自动携带 {@link StandardErrorTrait#CONFLICT} 特征。
///
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryConflict extends RegistryException {

  /// 使用消息创建冲突异常。
  ///
  /// @param message 详细消息
  protected RegistryConflict(String message) {
    super(message, StandardErrorTrait.CONFLICT);
  }

  /// 使用消息和根本原因创建冲突异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  protected RegistryConflict(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.CONFLICT);
  }
}
