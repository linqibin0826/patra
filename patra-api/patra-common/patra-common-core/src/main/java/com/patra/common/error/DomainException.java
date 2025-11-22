package com.patra.common.error;

/// 领域层异常的基类型。
/// 
/// 为领域特定故障提供框架无关的抽象,使领域层与 Spring 和其他基础设施关注点解耦。
/// 
/// 领域异常应扩展此类,以保持业务规则与技术实现细节清晰分离。
/// 
/// @author linqibin
/// @since 0.1.0
public abstract class DomainException extends RuntimeException {

  /// 使用提供的消息创建领域异常。
/// 
/// @param message 异常消息
  protected DomainException(String message) {
    super(message);
  }

  /// 使用提供的消息和根本原因创建领域异常。
/// 
/// @param message 异常消息
/// @param cause 根本原因
  protected DomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
