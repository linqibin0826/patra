package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 计划编排前置验证异常。
 *
 * <p>在窗口校验、背压检查或能力匹配阶段产生，归类为业务规则违例。</p>
 */
public class PlanValidationException extends IngestException implements HasErrorTraits {

    /** 验证失败原因分类。 */
    public enum Reason {
        WINDOW_MISSING,
        WINDOW_INVALID,
        WINDOW_TOO_LARGE,
        WINDOW_TOO_SMALL,
        QUEUE_BACKPRESSURE,
        CAPABILITY_MISMATCH
    }

    private final Reason reason;

    public PlanValidationException(String message) {
        this(message, null, null);
    }

    public PlanValidationException(String message, Reason reason) {
        this(message, reason, null);
    }

    public PlanValidationException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public PlanValidationException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
