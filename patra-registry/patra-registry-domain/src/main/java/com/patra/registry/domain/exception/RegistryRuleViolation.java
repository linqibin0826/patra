package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * 注册中心规则违规语义的异常基类。
 *
 * <p>表示违反业务规则、验证约束或数据完整性的操作 (例如,无效格式、约束冲突)。
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryRuleViolation extends RegistryException implements HasErrorTraits {

  /**
   * 使用消息创建异常。
   *
   * @param message 详细消息
   */
  protected RegistryRuleViolation(String message) {
    super(message);
  }

  /**
   * 使用消息和根本原因创建异常。
   *
   * @param message 详细消息
   * @param cause 根本原因
   */
  protected RegistryRuleViolation(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 返回此异常的错误特征(始终为 RULE_VIOLATION)。
   *
   * @return 包含 RULE_VIOLATION 特征的集合
   */
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.RULE_VIOLATION);
  }
}
