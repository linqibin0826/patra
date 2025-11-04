package com.patra.ingest.app.usecase.relay.coordinator;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.domain.port.OutboxRelayStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 中继租约协调器 - 管理 Outbox 消息的分布式租约获取
 *
 * <h3>职责</h3>
 *
 * <ul>
 *   <li>使用乐观锁尝试为 Outbox 消息获取租约
 *   <li>防止多个实例之间的并发中继处理
 *   <li>跟踪租约获取成功/失败以进行监控
 * </ul>
 *
 * <h3>并发控制</h3>
 *
 * <p>使用数据库级乐观锁通过版本字段:
 *
 * <ul>
 *   <li>UPDATE 成功 (affectedRows=1) → 租约成功获取
 *   <li>UPDATE 失败 (affectedRows=0) → 另一个实例获取了租约
 * </ul>
 *
 * <h3>日志策略</h3>
 *
 * <ul>
 *   <li>DEBUG: 租约获取详情 (messageId, leaseOwner, 结果)
 *   <li>无 INFO/WARN 日志 (高频操作,避免日志噪音)
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayLeaseCoordinator {

  private final OutboxRelayStore relayStore;

  /**
   * 尝试为单条 Outbox 消息获取租约
   *
   * <p>租约获取确保只有一个实例可以在租约窗口内中继此消息
   *
   * <p>实现: 使用以下内容更新数据库行:
   *
   * <ul>
   *   <li>status_code: 'PENDING' → 'PUBLISHING'
   *   <li>pub_lease_owner: 设置为当前实例标识符
   *   <li>pub_leased_until: 设置为租约过期时间
   *   <li>version: 通过乐观锁递增
   * </ul>
   *
   * @param message 要获取租约的 Outbox 消息
   * @param plan 包含 leaseOwner 和 leaseExpireAt 的中继计划
   * @return 如果租约成功获取返回 true,如果另一个实例拥有租约返回 false
   */
  public boolean tryAcquire(OutboxMessage message, RelayPlan plan) {
    boolean acquired =
        relayStore.acquireLease(
            message.getId(), message.getVersion(), plan.leaseOwner(), plan.leaseExpireAt());

    if (log.isDebugEnabled()) {
      if (acquired) {
        log.debug(
            "租约已获取: messageId={}, channel={}, leaseOwner={}, leaseExpireAt={}",
            message.getId(),
            message.getChannel(),
            plan.leaseOwner(),
            plan.leaseExpireAt());
      } else {
        log.debug(
            "租约丢失: messageId={}, channel={}, existingLeaseOwner={}, requestedBy={}",
            message.getId(),
            message.getChannel(),
            message.getLeaseOwner(),
            plan.leaseOwner());
      }
    }

    return acquired;
  }

  /**
   * 根据计划的触发时间和租约持续时间计算租约过期时间戳
   *
   * <p>租约持续时间通常配置为 30-60 秒,足以满足:
   *
   * <ul>
   *   <li>发布到下游代理 (通常 < 100ms)
   *   <li>数据库中的状态更新 (< 10ms)
   *   <li>暂时性网络问题的重试缓冲
   * </ul>
   *
   * <p>注意: 这是从 RelayPlan 构造委托的无状态计算
   *
   * @param plan 具有 triggeredAt 和 leaseDuration 的中继计划
   * @return 租约过期时刻
   */
  public static java.time.Instant computeLeaseExpireAt(RelayPlan plan) {
    return plan.triggeredAt().plus(plan.leaseDuration());
  }
}
