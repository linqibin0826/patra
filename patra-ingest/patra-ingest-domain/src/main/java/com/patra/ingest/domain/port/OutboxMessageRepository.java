package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.util.List;
import java.util.Optional;

/**
 * Outbox 消息仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 持久化待发布的 Outbox 消息,实现:
 *
 * <ul>
 *   <li>幂等性保证 - 通过 channel + dedupKey 唯一约束防止重复消息
 *   <li>批量操作 - 支持批量保存、更新、upsert 提升性能
 *   <li>生命周期管理 - 与 {@link OutboxRelayRepository} 协同管理消息状态转换
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxMessageRepository {

  /**
   * 批量持久化 Outbox 消息。
   *
   * <p><b>业务含义</b>: 一次性保存多条消息,用于事务内批量生产消息的场景。
   *
   * @param messages 待保存的消息列表
   */
  void saveAll(List<OutboxMessage> messages);

  /**
   * 创建或更新单条 Outbox 消息。
   *
   * <p><b>业务含义</b>: 保存新消息或更新已有消息的状态、负载、重试次数等。
   *
   * @param message 消息实体,包含幂等键、负载、状态
   */
  void saveOrUpdate(OutboxMessage message);

  /**
   * 根据 channel 和幂等键查询已有消息(用于去重检查)。
   *
   * <p><b>业务含义</b>: 在保存前检查是否已存在相同消息,避免重复生产。
   *
   * @param channel 频道标识符
   * @param dedupKey 幂等键
   * @return 匹配的消息,或 {@link Optional#empty()}
   */
  Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey);

  /**
   * 根据 channel 和幂等键列表批量查询消息。
   *
   * <p><b>业务含义</b>: 支持补偿流程(如 {@code publishRetry})中的批量幂等检查。
   *
   * @param channel 频道标识符
   * @param dedupKeys 幂等键列表(注意:列表不宜过大,避免 {@code IN} 子句性能问题)
   * @return 匹配的消息列表,未找到时返回空列表
   */
  List<OutboxMessage> findByChannelAndDedupIn(String channel, List<String> dedupKeys);

  /**
   * 批量更新 Outbox 消息(用于补偿流程刷新状态)。
   *
   * <p><b>业务含义</b>: 常见场景:将已有消息重置为 {@code PENDING}、刷新负载/头部、重置重试计数。
   *
   * @param messages 待更新的消息列表(必须包含有效的标识符)
   */
  void updateBatch(List<OutboxMessage> messages);

  /**
   * 批量插入或更新(upsert)Outbox 消息。
   *
   * <p><b>业务含义</b>: 通过唯一约束 {@code channel + dedupKey} 实现幂等性:
   *
   * <ul>
   *   <li>不存在时插入新记录
   *   <li>已存在时更新负载/头部/状态并重置重试计数
   * </ul>
   *
   * <p><b>并发安全</b>: 设计用于避免多节点并发重试同一消息时的竞态条件。
   *
   * @param messages 待插入或更新的消息列表
   */
  void upsertBatch(List<OutboxMessage> messages);
}
