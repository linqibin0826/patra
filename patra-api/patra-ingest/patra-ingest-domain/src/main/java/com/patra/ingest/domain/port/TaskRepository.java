package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务聚合仓储端口定义。
 * <p>用于持久化任务聚合（包含计划、切片、运行配置等上下文），并提供按计划维度的查询能力以及排队任务数量统计，
 * 帮助应用层完成调度决策与容量管理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRepository {

    /**
     * 保存或更新单个任务聚合。
     *
     * @param task 任务聚合，包含任务元数据、执行策略与初始状态
     * @return 持久化后的任务聚合，一般会补齐数据库主键
     */
    TaskAggregate save(TaskAggregate task);

    /**
     * 批量保存任务聚合，常用于计划切片生成后的一次性落库。
     *
     * @param tasks 任务聚合集合
     * @return 持久化后的任务聚集列表，顺序与入参一致
     */
    List<TaskAggregate> saveAll(List<TaskAggregate> tasks);

    /**
     * 按计划 ID 查询全部任务。
     *
     * @param planId 计划 ID
     * @return 归属该计划的任务列表
     */
    List<TaskAggregate> findByPlanId(Long planId);

    /**
     * 按任务 ID 查询任务聚合。
     *
     * @param taskId 任务 ID
     * @return 任务聚合，不存在则返回 empty
     */
    Optional<TaskAggregate> findById(Long taskId);

    /**
     * 统计处于排队状态（状态码为 QUEUED）的任务数量。
     *
     * @param provenanceCode 来源编码，可为空表示不过滤
     * @param operationCode  操作编码，可为空表示不过滤
     * @return 满足条件的排队任务数量
     */
    long countQueuedTasks(String provenanceCode, String operationCode);

    /**
     * CAS 抢占租约（步骤 0）。
     * <p>
     * 仅针对 QUEUED 状态且满足调度时间、租约可接管条件的任务进行 CAS 更新：
     * <ul>
     *   <li>条件：status_code='QUEUED' AND idempotent_key=#{idem}</li>
     *   <li>条件：scheduled_at IS NULL OR scheduled_at <= #{now}（尊重 notBefore）</li>
     *   <li>条件：leased_until IS NULL OR leased_until <= #{now} OR lease_owner=#{owner}（可接管）</li>
     * </ul>
     * 更新字段：lease_owner、leased_until、lease_count+1
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者标识（workerId:execId 或 execId）
     * @param now 当前时间（UTC）
     * @param ttlSeconds 租约 TTL（秒）
     * @param idempotentKey 幂等键（用于防御性校验）
     * @return true 表示抢占成功，false 表示他人持有或条件不满足
     */
    boolean tryAcquireLease(Long taskId, String owner, Instant now, int ttlSeconds, String idempotentKey);

    /**
     * 置任务为 RUNNING 状态并更新租约（步骤 1）。
     * <p>
     * 前置条件：WHERE lease_owner=#{owner}（防止被窃取）
     * 更新字段：status_code='RUNNING'、started_at、last_heartbeat_at、leased_until
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者
     * @param now 当前时间
     * @param ttlSeconds 租约 TTL（秒）
     * @return true 表示更新成功，false 表示租约已丢失
     */
    boolean markRunningWithLease(Long taskId, String owner, Instant now, int ttlSeconds);

    /**
     * 心跳续租。
     * <p>
     * 前置条件：WHERE lease_owner=#{owner}
     * 更新字段：leased_until、last_heartbeat_at、lease_count+1
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者
     * @param now 当前时间
     * @param ttlSeconds 租约 TTL（秒）
     * @return true 表示续租成功，false 表示租约已丢失
     */
    boolean renewLease(Long taskId, String owner, Instant now, int ttlSeconds);
}
