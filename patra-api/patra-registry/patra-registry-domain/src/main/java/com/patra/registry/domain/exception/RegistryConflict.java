package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/// 注册中心资源冲突异常基类。
/// 
/// 表示由于与现有资源或规则冲突而无法完成的操作 (例如,重复名称、版本冲突)。
/// 
/// @author linqibin
/// @since 0.1.0
public abstract class RegistryConflict extends RegistryException implements HasErrorTraits {

  /// 使用消息创建异常。
/// 
/// @param message 详细消息
  protected RegistryConflict(String message) {
    super(message);
  }

  /// 使用消息和根本原因创建异常。
/// 
/// @param message 详细消息
/// @param cause 根本原因
  protected RegistryConflict(String message, Throwable cause) {
    super(message, cause);
  }

  /// 返回此异常的错误特征(始终为 CONFLICT)。
/// 
/// @return 包含 CONFLICT 特征的集合
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.CONFLICT);
  }
}
