package com.patra.ingest.domain.exception;

import com.patra.common.error.DomainException;

/// 采集领域异常基类。
/// 
/// 目的:封装显式的领域失败以及与领域紧密耦合的持久化或依赖问题。这使得应用层和适配器层能够:
/// 
/// - 区分可重试和不可重试的错误(结合子类的 `ErrorTrait`)。
///   - 通过继承层次结构匹配来强制执行一致的日志记录并减少告警噪音。
///   - 在 Outbox 流或调度器回调中生成细粒度指标。
/// 
/// 使用指南:
/// 
/// - 所有新的采集领域异常都应扩展此类,并在暴露错误特征时实现 `HasErrorTraits`。
///   - 避免抛出通用的 `RuntimeException`;改为转换为有意义的子类。
///   - 在日志中包含关键上下文(计划键、数据源、操作、窗口)以加快调查。
/// 
/// @author linqibin
/// @since 0.1.0
public abstract class IngestException extends DomainException {

  /// 使用消息构造异常。
/// 
/// @param message 详细消息
  protected IngestException(String message) {
    super(message);
  }

  /// 使用消息和原因构造异常。
/// 
/// @param message 详细消息
/// @param cause 根本原因
  protected IngestException(String message, Throwable cause) {
    super(message, cause);
  }
}
