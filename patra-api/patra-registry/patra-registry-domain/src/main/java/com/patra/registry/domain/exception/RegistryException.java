package com.patra.registry.domain.exception;

import com.patra.common.error.DomainException;
import com.patra.common.error.trait.ErrorTrait;

/// 注册中心领域异常基类。
///
/// 表示注册中心领域内的业务规则违规，旨在由应用层统一处理。
///
/// **强制语义化**: 所有注册中心异常必须携带至少一个 {@link ErrorTrait}，明确表达业务语义。
///
/// **使用示例**:
///
/// ```java
/// public class ProvenanceNotFoundException extends RegistryException {
///     public ProvenanceNotFoundException(String provenanceCode) {
///         super("Provenance 未找到: " + provenanceCode, StandardErrorTrait.NOT_FOUND);
///     }
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryException extends DomainException {

  /// 使用消息和语义特征创建注册中心异常。
  ///
  /// @param message 详细消息
  /// @param traits 语义特征（至少提供一个）
  protected RegistryException(String message, ErrorTrait... traits) {
    super(message, traits);
  }

  /// 使用消息、根本原因和语义特征创建注册中心异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  /// @param traits 语义特征（至少提供一个）
  protected RegistryException(String message, Throwable cause, ErrorTrait... traits) {
    super(message, cause, traits);
  }
}
