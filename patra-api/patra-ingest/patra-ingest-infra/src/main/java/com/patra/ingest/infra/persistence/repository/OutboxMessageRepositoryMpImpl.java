package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.OutboxRelayStore;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Outbox 消息持久化实现（MyBatis-Plus）。
 * <p>职责：</p>
 * <ul>
 *   <li>消息初始写入（PENDING 状态）与幂等去重（基于 channel + dedupKey 上层控制）。</li>
 *   <li>按通道+可用时间窗口拉取待发布消息（不加锁，仅过滤状态= PENDING 且 availableAt &lt;= now）。</li>
 *   <li>通过乐观锁 + 租约字段（leaseOwner / leaseExpireAt / version）竞争获取分布式“发布权”。</li>
 *   <li>根据发布结果推进状态：PUBLISHED / DEFERRED（重试回退到 PENDING）/ FAILED（终止）。</li>
 * </ul>
 * <p>状态机（简化）：</p>
 * <pre>
 *   PENDING --(acquireLease 成功)--> LEASED --(markPublished)--> PUBLISHED (终态)
 *             |                                
 *             |--(发布失败且可重试 markDeferred)--> PENDING (retryCount+1，等待 nextRetryAt)
 *             |--(发布失败且超限 markFailed)------> FAILED   (终态)
 * 
 * 说明：本实现不显式存储 “LEASED” 中间状态（由 leaseOwner!=null & leaseExpireAt 未过期 + version 条件隐式表示）。
 *       fetchPending 仅返回未被“有效租约”占用的记录（SQL 中过滤租约已过期或空）。
 * </pre>
 * <p>并发控制：</p>
 * <ul>
 *   <li>版本号 version 通过条件更新防止并发覆盖；acquireLease / mark* 均以 version 作为乐观锁。</li>
 *   <li>租约二要素：leaseOwner / leaseExpireAt，获取成功后在过期前其他消费者拉取不到该消息。</li>
 *   <li>当发布进程崩溃或超时，租约过期后消息再次可见，允许新的消费者接手。</li>
 * </ul>
 * <p>幂等性：</p>
 * <ul>
 *   <li>上游业务在入箱阶段通过 (channel, dedupKey) 保证不重复写入；本仓储提供查询方法以支撑。</li>
 *   <li>publish 成功后 markPublished 只在 version 匹配时生效；重复调用（版本已变化）将触发异常，交由上层处理为幂等确认。</li>
 * </ul>
 * <p>错误处理策略：</p>
 * <ul>
 *   <li>影响行数 != 1 说明版本冲突或记录缺失：抛出 {@link OutboxPersistenceException}，由上层判定是否忽略/告警。</li>
 *   <li>不对正常高频路径打印 INFO；仅在 DEBUG 下输出调试信息。</li>
 * </ul>
 * <p>日志策略：DEBUG 级别记录关键状态转换（acquire 成功、publish/defer/fail），避免批量循环内高噪声；无业务 WARN 输出。</p>
 * <p>线程安全：无共享可变状态（Mapper / Converter 为无状态或线程安全），实例可被多线程复用。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl implements OutboxMessageRepository, OutboxRelayStore {

    private final OutboxMessageMapper mapper;
    private final OutboxMessageConverter converter;

    /**
     * 批量保存（仅插入）PENDING 状态 Outbox 消息。
     * <p>不做去重：调用方需在写入前完成 (channel, dedupKey) 幂等控制。</p>
     * <p>日志：为降低噪声，仅在 DEBUG 记录批次大小，不逐条记录。</p>
     * @param messages 消息集合（允许 null/empty 忽略）
     */
    @Override
    public void saveAll(List<OutboxMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] outbox batch insert size={} firstChannel={}", messages.size(), messages.get(0).getChannel());
        }
        for (OutboxMessage message : messages) {
            OutboxMessageDO entity = converter.toEntity(message);
            mapper.insert(entity);
        }
    }

    /**
     * 插入或更新单条 Outbox 消息。
     * <p>使用场景：补偿写入或需要更新非状态字段（极少）。常规状态推进请使用专用 mark* 方法保证版本语义。</p>
     * @param message 消息（null 忽略）
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
                log.debug("[INGEST][INFRA] outbox insert channel={} dedupKey={} id={}", message.getChannel(), message.getDedupKey(), entity.getId());
            }
        } else {
            mapper.updateById(entity);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] outbox update id={} channel={} version={} (non-state fields)", entity.getId(), message.getChannel(), message.getVersion());
            }
        }
    }

    /**
     * 根据 (channel, dedupKey) 精确定位一条消息用于幂等判断。
     * @param channel 通道（不能为空）
     * @param dedupKey 去重键（不能为空）
     * @return 消息 Optional（不存在返回 empty）
     */
    @Override
    public Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey) {
        OutboxMessageDO entity = mapper.findByChannelAndDedup(channel, dedupKey);
        return Optional.ofNullable(entity).map(converter::toDomain);
    }

    // ===== OutboxRelayStore =====

    /**
     * 拉取待发布消息。
     * <p>过滤条件示意：state=PENDING AND available_at &lt;= :availableTime AND (lease_owner IS NULL OR lease_expire_at &lt; NOW)。</p>
     * <p>不加排序保证：由 Mapper 层定义（建议按 available_at, id ASC）。</p>
     * @param channel 通道
     * @param availableTime 可用时间上限（<=），通常为当前时间
     * @param limit 最大条数（<=0 返回空）
     * @return 待处理消息集合（可能为空）
     */
    @Override
    public List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<OutboxMessageDO> entities = mapper.fetchPending(channel, availableTime, limit);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(converter::toDomain).toList();
    }

    /**
     * 通过乐观锁获取租约。
     * <p>条件：id = ? AND version = :expectedVersion AND (lease_owner IS NULL OR lease_expire_at &lt; NOW)。</p>
     * <p>成功：更新 leaseOwner / leaseExpireAt / version=version+1，返回 true；失败：返回 false（可能已被他人获取或版本冲突）。</p>
     * <p>日志：仅在成功时 DEBUG 记录，失败属常态竞争不记录。</p>
     * @param id 消息 ID
     * @param expectedVersion 期望版本（调用前需读取当前值）
     * @param leaseOwner 租约所有者（建议使用实例 ID）
     * @param leaseExpireAt 过期时间点
     * @return 是否成功获取
     */
    @Override
    public boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
        int updated = mapper.acquireLease(id, expectedVersion, leaseOwner, leaseExpireAt);
        boolean success = updated == 1;
        if (success && log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] outbox lease acquire id={} owner={} expireAt={}", id, leaseOwner, leaseExpireAt);
        }
        return success;
    }

    /**
     * 标记发布成功。
     * <p>要求当前版本 == expectedVersion；更新字段：state=PUBLISHED, published_at, broker_message_id, version=version+1。</p>
     * @param id 消息ID
     * @param expectedVersion 期望版本（含租约后版本）
     * @param messageId Broker 返回的消息 ID（可用于幂等确认）
     * @throws OutboxPersistenceException 版本冲突或行不存在
     */
    @Override
    public void markPublished(Long id, Long expectedVersion, String messageId) {
        int updated = mapper.markPublished(id, expectedVersion, messageId);
        if (updated != 1) {
            throw new OutboxPersistenceException(OutboxPersistenceException.Stage.MARK_PUBLISHED,
                    "更新 Outbox 状态失败，id=" + id);
        }
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] outbox published id={} brokerMsgId={}", id, messageId);
        }
    }

    /**
     * 标记延迟重试：状态回退为 PENDING（或逻辑上仍为 PENDING），写入 nextRetryAt 及错误信息并 version+1。
     * <p>上层需已决定仍可重试（retryCount 未超阈值）。</p>
     * @param id 消息ID
     * @param expectedVersion 期望版本
     * @param retryCount 新的重试次数
     * @param nextRetryAt 下次重试可用时间
     * @param errorCode 错误代码（可选）
     * @param errorMessage 错误消息（可截断）
     * @throws OutboxPersistenceException 版本冲突或写入失败
     */
    @Override
    public void markDeferred(Long id, Long expectedVersion, int retryCount, Instant nextRetryAt, String errorCode, String errorMessage) {
        int updated = mapper.markDeferred(id, expectedVersion, retryCount, nextRetryAt, errorCode, errorMessage);
        if (updated != 1) {
            throw new OutboxPersistenceException(OutboxPersistenceException.Stage.MARK_RETRY,
                    "写回 Outbox 重试失败，id=" + id);
        }
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] outbox deferred id={} retryCount={} nextRetryAt={} errCode={}", id, retryCount, nextRetryAt, errorCode);
        }
    }

    /**
     * 标记最终失败（FAILED / DEAD）。
     * <p>终态：不再被拉取；上层可选择后续人工补偿或搬运到死信库。</p>
     * @param id 消息ID
     * @param expectedVersion 期望版本
     * @param retryCount 最终重试次数（记录审计）
     * @param errorCode 错误代码
     * @param errorMessage 错误消息
     * @throws OutboxPersistenceException 版本冲突或行不存在
     */
    @Override
    public void markFailed(Long id, Long expectedVersion, int retryCount, String errorCode, String errorMessage) {
        int updated = mapper.markFailed(id, expectedVersion, retryCount, errorCode, errorMessage);
        if (updated != 1) {
            throw new OutboxPersistenceException(OutboxPersistenceException.Stage.MARK_DEAD,
                    "标记 Outbox DEAD 失败，id=" + id);
        }
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] outbox failed id={} retryCount={} errCode={} errMsgLen={}", id, retryCount, errorCode,
                errorMessage == null ? 0 : errorMessage.length());
        }
    }
}
