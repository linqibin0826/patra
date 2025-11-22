package com.patra.ingest.domain.exception;

/// 基础设施层异常,用于表示数据访问、数据转换等基础设施层的运行时错误。
/// 
/// 典型场景包括:
/// 
/// - 数据库实体转换失败
///   - 无效的数据库枚举代码
///   - 序列化/反序列化错误
/// 
/// @author linqibin
/// @since 0.1.0
public class InfrastructureException extends RuntimeException {

  /// 构造基础设施异常。
/// 
/// @param message 异常消息
  public InfrastructureException(String message) {
    super(message);
  }

  /// 构造基础设施异常并附加根因。
/// 
/// @param message 异常消息
/// @param cause 根因异常
  public InfrastructureException(String message, Throwable cause) {
    super(message, cause);
  }
}
