package com.patra.registry.domain.exception;

import com.patra.common.error.trait.StandardErrorTrait;

/// 注册中心"未找到"语义的异常基类。
///
/// 表示请求的资源（命名空间、目录、类型等）不存在，或由于业务规则而不可访问。
///
/// **自动语义**: 所有此类异常自动携带 {@link StandardErrorTrait#NOT_FOUND} 特征。
///
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryNotFound extends RegistryException {

  /// 使用消息创建"未找到"异常。
  ///
  /// @param message 详细消息
  protected RegistryNotFound(String message) {
    super(message, StandardErrorTrait.NOT_FOUND);
  }

  /// 使用消息和根本原因创建"未找到"异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  protected RegistryNotFound(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.NOT_FOUND);
  }
}
