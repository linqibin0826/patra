package com.patra.ingest.domain.exception;

import com.patra.common.error.DomainException;

/**
 * Ingest 领域异常基类。
 *
 * <p>语义：统一承载“领域显式失败”与“与领域强相关的持久化/依赖故障包装”，保证应用层（App / Adapter）可以：
 * <ul>
 *   <li>区分可重试与不可重试（结合具体子类的 {@code ErrorTrait}）。</li>
 *   <li>统一日志结构与告警降噪（只需匹配继承层次）。</li>
 *   <li>在 Outbox / 调度回调中进行细粒度统计与指标聚合。</li>
 * </ul>
 * </p>
 * <p>使用约定：
 * <ul>
 *   <li>新增任何 Ingest 领域异常需继承本类并实现 {@code HasErrorTraits}（若需暴露错误特征）。</li>
 *   <li>避免在外层直接抛出通用 RuntimeException，应转换为语义明确的子类。</li>
 *   <li>日志中需包含关键上下文（计划 key / provenance / operation / window），以支持排查。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class IngestException extends DomainException {

    /**
     * 使用消息构造异常。
     *
     * @param message 详情消息
     */
    protected IngestException(String message) {
        super(message);
    }

    /**
     * 使用消息与原因构造异常。
     *
     * @param message 详情消息
     * @param cause   异常原因
     */
    protected IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}