package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Registry 领域规则违反（Rule Violation）语义的基类异常。
 *
 * <p>表示操作违反业务规则/校验约束/数据完整性等（如格式非法、约束冲突）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryRuleViolation extends RegistryException implements HasErrorTraits {
    
    /**
     * 使用消息构造异常。
     *
     * @param message 详情消息
     */
    protected RegistryRuleViolation(String message) {
        super(message);
    }
    
    /**
     * 使用消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause 异常原因
     */
    protected RegistryRuleViolation(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 返回该异常的错误特征集合（恒为 RULE_VIOLATION）。
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }
}
