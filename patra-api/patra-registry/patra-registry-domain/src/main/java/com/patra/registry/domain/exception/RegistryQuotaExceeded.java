package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * 注册中心配额/速率限制/容量超限异常基类。
 *
 * <p>表示由于超出配额、速率限制或容量约束而失败的操作 (例如,计数或大小限制)。
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryQuotaExceeded extends RegistryException implements HasErrorTraits {

  /**
   * 使用消息创建异常。
   *
   * @param message 详细消息
   */
  protected RegistryQuotaExceeded(String message) {
    super(message);
  }

  /**
   * 使用消息和根本原因创建异常。
   *
   * @param message 详细消息
   * @param cause 根本原因
   */
  protected RegistryQuotaExceeded(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 返回此异常的错误特征(始终为 QUOTA_EXCEEDED)。
   *
   * @return 包含 QUOTA_EXCEEDED 特征的集合
   */
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.QUOTA_EXCEEDED);
  }
}
