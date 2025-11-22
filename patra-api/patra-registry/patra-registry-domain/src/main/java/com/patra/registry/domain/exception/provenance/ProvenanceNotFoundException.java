package com.patra.registry.domain.exception.provenance;

import com.patra.registry.domain.exception.RegistryNotFound;

/// 来源未找到时抛出的异常。
///
/// 当请求的来源不存在或在当前上下文中不可用时抛出。 平台错误处理将其映射到 HTTP 404 (REG-0404)。
///
/// @author linqibin
/// @since 0.1.0
public class ProvenanceNotFoundException extends RegistryNotFound {

  /// 使用详细消息创建异常。
  ///
  /// @param message 详细消息
  public ProvenanceNotFoundException(String message) {
    super(message);
  }

  /// 使用详细消息和根本原因创建异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  public ProvenanceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
