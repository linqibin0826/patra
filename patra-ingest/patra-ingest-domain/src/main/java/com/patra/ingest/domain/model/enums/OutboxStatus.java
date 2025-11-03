package com.patra.ingest.domain.model.enums;

/**
 * Outbox 消息状态,表示可靠交付的状态机。
 *
 * <p>状态机语义:
 *
 * <ul>
 *   <li>PENDING → 待发布;准备被扫描并发布
 *   <li>PUBLISHING → 发布中;当前由租约持有,正在发布过程中
 *   <li>PUBLISHED → 已发布;成功交付到下游(记录了 broker id)
 *   <li>FAILED → 失败;发布失败但仍可重试或最终进入 DEAD 状态
 *   <li>DEAD → 死信;重试次数耗尽或手动隔离
 * </ul>
 */
public enum OutboxStatus {
  /** 待发布;准备被扫描并发布。 */
  PENDING,
  /** 发布中;租约持有,防止并发发布。 */
  PUBLISHING,
  /** 已发布;成功交付到下游。 */
  PUBLISHED,
  /** 失败;发布失败但仍在重试策略范围内。 */
  FAILED,
  /** 死信;不再重试的最终失败状态。 */
  DEAD
}
