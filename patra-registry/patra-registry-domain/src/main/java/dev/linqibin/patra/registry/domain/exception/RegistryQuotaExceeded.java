package dev.linqibin.patra.registry.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 注册中心配额/速率限制/容量超限异常基类。
///
/// 表示由于超出配额、速率限制或容量约束而失败的操作（例如，计数或大小限制）。
///
/// **自动语义**: 所有此类异常自动携带 {@link StandardErrorTrait#QUOTA_EXCEEDED} 特征。
///
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryQuotaExceeded extends RegistryException {

  /// 使用消息创建配额超限异常。
  ///
  /// @param message 详细消息
  protected RegistryQuotaExceeded(String message) {
    super(message, StandardErrorTrait.QUOTA_EXCEEDED);
  }

  /// 使用消息和根本原因创建配额超限异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  protected RegistryQuotaExceeded(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.QUOTA_EXCEEDED);
  }
}
