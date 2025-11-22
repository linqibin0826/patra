package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/// 注册中心"未找到"语义的异常基类。
///
/// 表示请求的资源(命名空间、目录、类型等)不存在, 或由于业务规则而不可访问。所有"未找到"异常都应扩展此类, 以确保一致的错误特征分类和处理。
///
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryNotFound extends RegistryException implements HasErrorTraits {

  /// 使用消息创建异常。
  ///
  /// @param message 详细消息
  protected RegistryNotFound(String message) {
    super(message);
  }

  /// 使用消息和根本原因创建异常。
  ///
  /// @param message 详细消息
  /// @param cause 根本原因
  protected RegistryNotFound(String message, Throwable cause) {
    super(message, cause);
  }

  /// 返回此异常的错误特征(始终为 NOT_FOUND)。
  ///
  /// @return 包含 NOT_FOUND 特征的集合
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.NOT_FOUND);
  }
}
