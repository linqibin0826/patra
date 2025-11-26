package com.patra.catalog.domain.exception;

import com.patra.common.error.DomainException;
import com.patra.common.error.trait.ErrorTrait;

/// Catalog 领域异常基类。
///
/// 封装 Catalog 服务的领域特定失败以及与领域紧密耦合的持久化或依赖问题。
///
/// **强制语义化**: 所有 Catalog 异常必须携带至少一个 {@link ErrorTrait}，明确表达业务语义。
///
/// **语义特征的作用**:
///
/// - 区分可重试和不可重试的错误（如 TIMEOUT vs RULE_VIOLATION）
/// - 在批处理流或调度器回调中生成细粒度指标
/// - 自动映射到适当的 HTTP 状态码和错误响应
///
/// @author linqibin
/// @since 0.1.0
public abstract class CatalogException extends DomainException {

  /// 使用消息和语义特征构造 Catalog 异常。
  ///
  /// @param message 详细消息
  /// @param traits 语义特征（至少提供一个）
  protected CatalogException(String message, ErrorTrait... traits) {
    super(message, traits);
  }

  /// 使用消息、根本原因和语义特征构造 Catalog 异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  /// @param traits 语义特征（至少提供一个）
  protected CatalogException(String message, Throwable cause, ErrorTrait... traits) {
    super(message, cause, traits);
  }
}
