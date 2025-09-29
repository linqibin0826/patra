package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 计划编排前置验证异常。
 *
 * <p>产生阶段：计划进入装配/切片之前的 <strong>前置校验链</strong>（窗口有效性校验、窗口跨度边界、队列背压、能力匹配等）。
 * 一旦抛出说明当前请求不满足业务前提，<strong>无需重试</strong>，应通过调整调度参数 / 时间窗口 / 系统配置后重新提交。</p>
 *
 * <p>异常处理建议：
 * <ul>
 *   <li>应用层：捕获后记录 INFO 或 WARN（视可预期程度），向上游返回可读提示；不做重试。</li>
 *   <li>监控层：统计 {@link Reason} 分布，辅助发现调度策略与配置缺陷。</li>
 *   <li>排查指引：结合窗口起止时间、计划 key 与背压指标定位。</li>
 * </ul>
 * </p>
 */
public class PlanValidationException extends IngestException implements HasErrorTraits {

    /**
     * 验证失败原因分类。
     * <p>所有枚举值均属于“请求侧可修复”范畴，出现后不应进行自动重试。</p>
     */
    public enum Reason {
        /** 调度未提供窗口，无法继续。 */
        WINDOW_MISSING,
        /** 窗口起止非法（起 >= 止 / 与基准对齐失败 / 超出允许边界）。 */
        WINDOW_INVALID,
        /** 窗口跨度超过最大限制（需拆分或缩小）。 */
        WINDOW_TOO_LARGE,
        /** 窗口跨度过小，无法形成有效切片或浪费资源。 */
        WINDOW_TOO_SMALL,
        /** 下游（任务/消息）队列存在背压，暂不接受新计划。 */
        QUEUE_BACKPRESSURE,
        /** 能力/来源/端点组合与平台能力矩阵不匹配。 */
        CAPABILITY_MISMATCH
    }

    /** 验证失败原因，可能为 null（表示未细分或通用失败）。 */
    private final Reason reason;

    /**
     * 使用消息构造（未细分原因）。
     *
     * @param message 人类可读的失败描述
     */
    public PlanValidationException(String message) {
        this(message, null, null);
    }

    /**
     * 使用消息与原因构造。
     *
     * @param message 失败描述
     * @param reason  失败细分原因
     */
    public PlanValidationException(String message, Reason reason) {
        this(message, reason, null);
    }

    /**
     * 使用消息、原因与底层异常构造。
     *
     * @param message 失败描述
     * @param reason  失败细分原因
     * @param cause   底层触发异常（可为空）
     */
    public PlanValidationException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * 使用消息与底层异常构造（未细分原因）。
     *
     * @param message 失败描述
     * @param cause   底层异常
     */
    public PlanValidationException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * 获取验证失败原因。
     *
     * @return 失败原因；可能为 null
     */
    public Reason getReason() {
        return reason;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
