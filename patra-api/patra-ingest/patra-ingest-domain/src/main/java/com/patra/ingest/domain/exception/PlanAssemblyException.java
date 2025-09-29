package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 计划装配异常。
 *
 * <p>发生于完成前置校验后，进入“窗口切片 / 任务分解”阶段，若策略无法生成有效的切片集合或任务集合，则抛出此异常。
 * 与 {@link PlanValidationException} 区别：本异常表示输入参数已合法，但装配算法或组合规则未产出可执行结果。</p>
 * <p>处理策略：
 * <ul>
 *   <li>{@link Reason#EMPTY_RESULT}：可判定为无数据窗口，通常记录 INFO 级别并终止创建流程。</li>
 *   <li>{@link Reason#SLICE_GENERATION_FAILED} / {@link Reason#TASK_GENERATION_FAILED}：记录 ERROR 并关注策略实现/数据基线。</li>
 * </ul>
 * </p>
 */
public class PlanAssemblyException extends IngestException implements HasErrorTraits {

    /**
     * 装配失败原因。
     * <p>用于细化定位，指导上层采取不同的日志级别与告警策略。</p>
     */
    public enum Reason {
        /** 算法执行成功但结果集合为空（窗口内无数据/无匹配）。 */
        EMPTY_RESULT,
        /** 切片生成阶段失败（窗口划分逻辑异常或边界溢出）。 */
        SLICE_GENERATION_FAILED,
        /** 任务生成阶段失败（切片转任务映射 / 参数装配失败）。 */
        TASK_GENERATION_FAILED
    }

    /** 失败原因，可能为空表示未细分。 */
    private final Reason reason;

    /**
     * 使用消息构造（未细分原因）。
     *
     * @param message 描述消息
     */
    public PlanAssemblyException(String message) {
        this(message, null, null);
    }

    /**
     * 使用消息与原因构造。
     *
     * @param message 描述消息
     * @param reason  失败原因
     */
    public PlanAssemblyException(String message, Reason reason) {
        this(message, reason, null);
    }

    /**
     * 使用消息、原因、底层异常构造。
     *
     * @param message 描述消息
     * @param reason  失败原因
     * @param cause   底层异常
     */
    public PlanAssemblyException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * 使用消息与底层异常构造（未细分原因）。
     *
     * @param message 描述消息
     * @param cause   底层异常
     */
    public PlanAssemblyException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * 获取失败原因。
     *
     * @return 失败原因，可能为 null
     */
    public Reason getReason() {
        return reason;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
