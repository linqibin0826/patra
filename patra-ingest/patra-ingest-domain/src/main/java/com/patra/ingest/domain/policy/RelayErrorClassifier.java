package com.patra.ingest.domain.policy;

/// Relay 错误分类器。
/// 
/// 将 Relay 失败分类为可重试或致命类别,用于决定:
/// 
/// - 是否应重试(TRANSIENT - 临时性错误)
///   - 是否应路由到死信队列(FATAL - 致命错误)
/// 
/// 分类依据:
/// 
/// - **TRANSIENT**: 网络抖动、连接超时、数据库临时不可用、乐观锁冲突等
///   - **FATAL**: 序列化错误、配置错误、业务规则违反、不支持的操作等
/// 
/// @author linqibin
/// @since 0.1.0
public interface RelayErrorClassifier {

  /// 分类错误类型。
/// 
/// @param cause 异常
/// @return 错误类型(TRANSIENT 或 FATAL)
  RelayErrorKind classify(Throwable cause);

  /// Relay 错误类型枚举。
  enum RelayErrorKind {
    /// 临时性错误,可重试。
    TRANSIENT,
    /// 致命错误,不可重试,应路由到死信队列。
    FATAL
  }
}
