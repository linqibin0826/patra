package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.OutboxRelayRepository;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 发件箱消息持久化的 MyBatis-Plus 实现。
 *
 * <h3>职责</h3>
 *
 * <ul>
 *   <li>初始消息写入(PENDING 状态),通过 (channel, dedupKey) 唯一性保证幂等性
 *   <li>按通道和可用时间窗口获取可发布消息(无锁,过滤条件: status=PENDING 且 availableAt &lt;= now)
 *   <li>通过乐观锁 + 租约字段(leaseOwner/leaseExpireAt/version)竞争分布式"发布权"
 *   <li>根据发布结果推进状态: PUBLISHED / DEFERRED(重试回到 PENDING) / FAILED(终态)
 * </ul>
 *
 * <h3>状态机(简化版)</h3>
 *
 * <pre>
 *   PENDING --(acquireLease 成功)--> LEASED --(markPublished)--> PUBLISHED (终态)
 *             |
 *             |--(发布失败,可重试 → markDeferred)--> PENDING (retryCount+1, 等待到 nextRetryAt)
 *             |--(发布失败,耗尽 → markFailed)-----> FAILED (终态)
 *
 * 注意: "LEASED" 中间状态未显式存储(由 leaseOwner!=null & leaseExpireAt 未过期 + version 条件隐式表示)。
 *       fetchPending 仅返回未被"活跃租约"持有的记录(SQL 过滤已过期或 null 租约)。
 * </pre>
 *
 * <h3>并发控制</h3>
 *
 * <ul>
 *   <li>版本号通过条件更新防止并发覆盖; acquireLease/mark* 方法使用 version 作为乐观锁
 *   <li>租约由两个元素组成: leaseOwner/leaseExpireAt; 一旦获取,消息对其他消费者不可见直到过期
 *   <li>如果发布进程崩溃或超时,过期租约允许新消费者接管
 * </ul>
 *
 * <h3>幂等性</h3>
 *
 * <ul>
 *   <li>上游在 Outbox 插入时通过 (channel, dedupKey) 确保无重复写入; 本仓储提供查询支持
 *   <li>成功发布后,markPublished 仅在 version 匹配时生效; 重复调用(version 已变)触发异常供上游幂等确认
 * </ul>
 *
 * <h3>错误处理策略</h3>
 *
 * <ul>
 *   <li>受影响行数 != 1 表示版本冲突或记录缺失: 抛出 {@link OutboxPersistenceException} 供上游决策(忽略/告警)
 *   <li>高频路径无 INFO 日志; 仅关键状态转换使用 DEBUG
 * </ul>
 *
 * <h3>日志策略</h3>
 *
 * <p>DEBUG 级别记录关键状态转换(获取成功, 发布/延迟/失败),避免批处理循环中的噪音; 无业务 WARN 输出。
 *
 * <h3>线程安全</h3>
 *
 * <p>无共享可变状态(Mapper/Converter 无状态或线程安全); 实例可跨线程重用。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl
    implements OutboxMessageRepository, OutboxRelayRepository {

  private final OutboxMessageMapper mapper;
  private final OutboxMessageConverter converter;

  /**
   * 批量保存(仅插入)PENDING 状态的发件箱消息。
   *
   * <p>不执行去重: 调用方必须在插入前处理 (channel, dedupKey) 幂等性。
   *
   * <p>日志记录: DEBUG 级别记录批次大小以减少噪音; 无单条消息日志。
   *
   * @param messages 消息集合(null/空被忽略)
   */
  @Override
  public void saveAll(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox batch insert size={} firstChannel={}",
          messages.size(),
          messages.get(0).getChannel());
    }
    for (OutboxMessage message : messages) {
      // Convert to data object and insert via MyBatis-Plus
      OutboxMessageDO entity = converter.toEntity(message);
      mapper.insert(entity);
    }
  }

  /**
   * 插入或更新单个发件箱消息。
   *
   * <p>使用场景: 补偿写入或更新非状态字段(罕见)。对于常规状态转换,使用专用 mark* 方法以确保版本语义。
   *
   * @param message 消息(null 被忽略)
   */
  @Override
  public void saveOrUpdate(OutboxMessage message) {
    if (message == null) {
      return;
    }
    OutboxMessageDO entity = converter.toEntity(message);
    if (entity.getId() == null) {
      mapper.insert(entity);
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbox insert channel={} dedupKey={} id={}",
            message.getChannel(),
            message.getDedupKey(),
            entity.getId());
      }
    } else {
      // Update non-state fields only (e.g., payload/headers); state transitions handled by mark*
      // methods
      mapper.updateById(entity);
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbox update id={} channel={} version={} (non-state fields)",
            entity.getId(),
            message.getChannel(),
            message.getVersion());
      }
    }
  }

  /**
   * 通过 (channel, dedupKey) 查找消息以进行幂等性检查。
   *
   * @param channel 通道标识符(不能为 null)
   * @param dedupKey 去重键(不能为 null)
   * @return 如果找到则返回包含消息的 Optional,否则为空
   */
  @Override
  public Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey) {
    OutboxMessageDO entity = mapper.findByChannelAndDedup(channel, dedupKey);
    return Optional.ofNullable(entity).map(converter::toDomain);
  }

  // ==================== OutboxRelayRepository 实现 ====================

  /**
   * 获取准备发布的待处理消息。
   *
   * <p>支持通道过滤或从所有通道获取:
   *
   * <ul>
   *   <li>当 channel 非 null 时,仅从指定通道获取消息
   *   <li>当 channel 为 null 时,从所有通道获取消息
   * </ul>
   *
   * <p>过滤条件: state=PENDING AND available_at &lt;= :availableTime AND (lease_owner IS NULL OR
   * lease_expire_at &lt; NOW)。
   *
   * <p>此处不强制排序保证; 由 Mapper 层定义(推荐: available_at, id ASC)。
   *
   * @param channel 通道标识符,null 表示从所有通道获取
   * @param availableTime 可用时间上界(&lt;=),通常为当前时间
   * @param limit 最大消息数(&lt;=0 返回空列表)
   * @return 待处理消息列表(可能为空)
   */
  @Override
  public List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit) {
    if (limit <= 0) {
      return Collections.emptyList();
    }
    List<OutboxMessageDO> entities = mapper.fetchPending(channel, availableTime, limit);
    if (entities == null || entities.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug(
            "fetch pending Outbox messages channel={} limit={}, found 0 results", channel, limit);
      }
      return Collections.emptyList();
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "fetch pending Outbox messages channel={} limit={}, found {} results",
          channel,
          limit,
          entities.size());
    }
    // Map to domain objects for upstream Relay processing
    return entities.stream().map(converter::toDomain).toList();
  }

  /**
   * 通过乐观锁获取租约。
   *
   * <p>条件: id = ? AND version = :expectedVersion AND (lease_owner IS NULL OR lease_expire_at &lt;
   * NOW)。
   *
   * <p>成功: 更新 leaseOwner/leaseExpireAt/version=version+1,返回 true; 失败: 返回 false(可能被其他人获取或版本冲突)。
   *
   * <p>日志记录: 仅在成功时记录 DEBUG; 失败是正常竞争,不记录。
   *
   * @param id 消息 ID
   * @param expectedVersion 期望版本(调用前必须读取当前值)
   * @param leaseOwner 租约所有者标识符(推荐: 实例 ID)
   * @param leaseExpireAt 租约过期时间戳
   * @return 如果成功获取租约则返回 true,否则返回 false
   */
  @Override
  public boolean acquireLease(
      Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
    int affectedRows = mapper.acquireLease(id, expectedVersion, leaseOwner, leaseExpireAt);
    boolean isSuccess = affectedRows == 1;
    if (isSuccess && log.isDebugEnabled()) {
      log.debug("Outbox lease acquired id={} owner={} expireAt={}", id, leaseOwner, leaseExpireAt);
    }
    return isSuccess;
  }

  /**
   * 标记消息为已成功发布。
   *
   * <p>要求当前版本 == expectedVersion; 更新字段: state=PUBLISHED, published_at, version=version+1。
   *
   * @param id 消息 ID
   * @param expectedVersion 期望版本(包括租约后版本)
   * @throws OutboxPersistenceException 如果版本冲突或未找到行
   */
  @Override
  public void markPublished(Long id, Long expectedVersion) {
    int affectedRows = mapper.markPublished(id, expectedVersion);
    if (affectedRows != 1) {
      throw new OutboxPersistenceException(
          OutboxPersistenceException.Stage.MARK_PUBLISHED,
          "Failed to update Outbox state to PUBLISHED, id=" + id);
    }
    if (log.isDebugEnabled()) {
      log.debug("Outbox published id={}", id);
    }
  }

  /**
   * 标记消息为延迟重试: 状态恢复到 PENDING(或逻辑上保持 PENDING), 记录 nextRetryAt 和错误信息,版本递增。
   *
   * <p>上游必须已决定允许重试(retryCount 未超过阈值)。
   *
   * @param id 消息 ID
   * @param expectedVersion 期望版本
   * @param retryCount 新的重试计数
   * @param nextRetryAt 下次重试可用时间
   * @param errorCode 错误代码(可选)
   * @param errorMessage 错误消息(可能被截断)
   * @throws OutboxPersistenceException 如果版本冲突或写入失败
   */
  @Override
  public void markDeferred(
      Long id,
      Long expectedVersion,
      int retryCount,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage) {
    // Use optimistic lock conditional update to revert state and record next retry plan
    int affectedRows =
        mapper.markDeferred(id, expectedVersion, retryCount, nextRetryAt, errorCode, errorMessage);
    if (affectedRows != 1) {
      throw new OutboxPersistenceException(
          OutboxPersistenceException.Stage.MARK_RETRY, "Failed to mark Outbox for retry, id=" + id);
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox deferred id={} retryCount={} nextRetryAt={} errCode={}",
          id,
          retryCount,
          nextRetryAt,
          errorCode);
    }
  }

  /**
   * 标记消息为永久失败(FAILED/DEAD 状态)。
   *
   * <p>终态: 不再被获取; 上游可选择手动补偿或移至死信存储。
   *
   * @param id 消息 ID
   * @param expectedVersion 期望版本
   * @param retryCount 最终重试计数(用于审计)
   * @param errorCode 错误代码
   * @param errorMessage 错误消息
   * @throws OutboxPersistenceException 如果版本冲突或未找到行
   */
  @Override
  public void markFailed(
      Long id, Long expectedVersion, int retryCount, String errorCode, String errorMessage) {
    int affectedRows = mapper.markFailed(id, expectedVersion, retryCount, errorCode, errorMessage);
    if (affectedRows != 1) {
      throw new OutboxPersistenceException(
          OutboxPersistenceException.Stage.MARK_DEAD, "Failed to mark Outbox as DEAD, id=" + id);
    }
    if (log.isDebugEnabled()) {
      int errorMsgLength = errorMessage == null ? 0 : errorMessage.length();
      log.debug(
          "Outbox failed id={} retryCount={} errCode={} errMsgLen={}",
          id,
          retryCount,
          errorCode,
          errorMsgLength);
    }
  }

  // ==================== OutboxMessageRepository: 批量操作 ====================

  /**
   * 按通道和去重键批量查询发件箱消息。
   *
   * <p>用于 publishRetry 场景中的批量幂等性检查。
   *
   * @param channel 通道标识符
   * @param dedupKeys 去重键集合(推荐 &lt;=500 以避免 IN 子句性能问题)
   * @return 匹配消息列表,无匹配则返回空列表
   */
  @Override
  public List<OutboxMessage> findByChannelAndDedupIn(String channel, List<String> dedupKeys) {
    if (dedupKeys == null || dedupKeys.isEmpty()) {
      return Collections.emptyList();
    }

    if (log.isDebugEnabled()) {
      log.debug("Outbox batch query channel={} dedupKeyCount={}", channel, dedupKeys.size());
    }

    List<OutboxMessageDO> entities = mapper.findByChannelAndDedupIn(channel, dedupKeys);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }

    return entities.stream().map(converter::toDomain).toList();
  }

  /**
   * 批量更新发件箱消息(用于带状态刷新的补偿发布场景)。
   *
   * <p>典型场景: 重试将现有消息状态重置为 PENDING,更新 payload/headers,重置重试计数。
   *
   * <p>注意: 使用 MyBatis-Plus updateById 逐条更新; 适用于小批量(&lt;100 条消息)。
   *
   * @param messages 要更新的消息集合(必须包含有效 ID)
   */
  @Override
  public void updateBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug("Outbox batch update size={}", messages.size());
    }

    for (OutboxMessage message : messages) {
      OutboxMessageDO entity = converter.toEntity(message);
      if (entity.getId() == null) {
        throw new IllegalArgumentException(
            "Cannot update Outbox message without ID, dedupKey=" + message.getDedupKey());
      }
      mapper.updateById(entity);
    }
  }

  /**
   * 批量插入或更新发件箱消息(UPSERT 语义)。
   *
   * <p>通过唯一约束(channel + dedupKey)实现幂等性:
   *
   * <ul>
   *   <li>如果消息不存在,插入新记录
   *   <li>如果消息存在(dedupKey 冲突),更新 payload/headers/status 并重置 retryCount
   * </ul>
   *
   * <p>解决 publishRetry 并发竞争条件(两个实例同时重试同一消息)。
   *
   * @param messages 要插入或更新的消息集合
   */
  @Override
  public void upsertBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox upsert batch size={} firstChannel={}",
          messages.size(),
          messages.get(0).getChannel());
    }

    List<OutboxMessageDO> entities = messages.stream().map(converter::toEntity).toList();

    int affectedRows = mapper.upsertBatch(entities);

    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox upsert batch completed size={} affectedRows={}", messages.size(), affectedRows);
    }
  }
}
