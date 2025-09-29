package com.patra.ingest.domain.model.enums;

/**
 * Outbox 消息状态：统一消息可靠投递状态机。
 * <ul>
 *   <li>PENDING：待发布（可被扫描）</li>
 *   <li>PUBLISHING：发布中（持有租约）</li>
 *   <li>PUBLISHED：已成功发布到下游（记录外部消息 ID）</li>
 *   <li>FAILED：发布失败（可进入重试或最终转 DEAD）</li>
 *   <li>DEAD：死亡消息（超过最大重试或人为冻结）</li>
 * </ul>
 */
public enum OutboxStatus {
    /**
     * 待发布
     */
    PENDING,
    /**
     * 发布中（租约持有期间避免并发）
     */
    PUBLISHING,
    /**
     * 已发布成功
     */
    PUBLISHED,
    /**
     * 失败（仍在策略管控周期内）
     */
    FAILED,
    /**
     * 死亡（不再尝试）
     */
    DEAD
}
