package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 计划装配异常。
 *
 * <p>当计划、切片或任务组装失败时抛出，提示输入条件与策略无法生成有效执行单元。</p>
 */
public class PlanAssemblyException extends IngestException implements HasErrorTraits {

    /** 装配失败原因。 */
    public enum Reason {
        EMPTY_RESULT,
        SLICE_GENERATION_FAILED,
        TASK_GENERATION_FAILED
    }

    private final Reason reason;

    public PlanAssemblyException(String message) {
        this(message, null, null);
    }

    public PlanAssemblyException(String message, Reason reason) {
        this(message, reason, null);
    }

    public PlanAssemblyException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public PlanAssemblyException(String message, Throwable cause) {
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
