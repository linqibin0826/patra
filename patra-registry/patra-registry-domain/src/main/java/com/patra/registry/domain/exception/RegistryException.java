package com.patra.registry.domain.exception;

import com.patra.common.error.DomainException;

/// 注册中心领域异常基类。
/// 
/// 表示注册中心领域内的业务规则违规,旨在由应用层统一处理。 所有注册中心领域异常都应扩展此类以保持一致性。
/// 
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryException extends DomainException {

  /// 使用消息创建异常。
/// 
/// @param message 详细消息
  protected RegistryException(String message) {
    super(message);
  }

  /// 使用消息和根本原因创建异常。
/// 
/// @param message 详细消息
/// @param cause 根本原因
  protected RegistryException(String message, Throwable cause) {
    super(message, cause);
  }
}
