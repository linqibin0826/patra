package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务(Task)聚合根仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 持久化任务聚合根及其计划/切片配置,并提供:
 *
 * <ul>
 *   <li>任务持久化 - 保存任务元数据、执行策略、初始状态
 *   <li>计划范围查询 - 查询计划的所有任务
 *   <li>队列统计 - 支持调度和容量管理
 *   <li>租约管理 - 支持分布式任务锁和租约续期
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRepository {

  /**
   * 持久化或更新单个任务聚合根。
   *
   * <p><b>业务含义</b>: 保存任务聚合根,包括元数据、执行策略、初始状态。
   *
   * @param task 任务聚合根,包含元数据、执行策略、初始状态
   * @return 已持久化的聚合根(标识符已填充)
   */
  TaskAggregate save(TaskAggregate task);

  /**
   * 批量持久化多个任务聚合根(通常在切片后)。
   *
   * <p><b>业务含义</b>: 一次性保存多个任务,用于计划切片后的批量任务创建。
   *
   * @param tasks 任务聚合根列表
   * @return 已持久化的聚合根列表(保持输入顺序)
   */
  List<TaskAggregate> saveAll(List<TaskAggregate> tasks);

  /**
   * 查询指定计划的所有任务。
   *
   * <p><b>业务含义</b>: 获取计划的完整任务列表。
   *
   * @param planId 计划标识符
   * @return 计划的任务列表
   */
  List<TaskAggregate> findByPlanId(Long planId);

  /**
   * 查询指定切片关联的任务(强制 1:1 关系)。
   *
   * <p><b>注意</b>: 重构后,Slice:Task 为 1:1 关系,由数据库唯一约束 {@code uk_task_slice} 保护。此方法最多返回一个任务。
   *
   * @param sliceId 切片标识符
   * @return 任务聚合根,或 {@link Optional#empty()}
   */
  Optional<TaskAggregate> findBySliceId(Long sliceId);

  /**
   * 根据标识符查询任务聚合根。
   *
   * <p><b>业务含义</b>: 通过技术主键(ID)检索任务。
   *
   * @param taskId 任务标识符
   * @return 聚合根,或 {@link Optional#empty()}
   */
  Optional<TaskAggregate> findById(Long taskId);

  /**
   * 统计 {@code QUEUED} 状态的任务数量。
   *
   * <p><b>业务含义</b>: 获取队列中待执行的任务数量,用于调度决策。
   *
   * @param provenanceCode Provenance 过滤条件(可为 null)
   * @param operationCode 操作代码过滤条件(可为 null)
   * @return 排队任务数量
   */
  long countQueuedTasks(ProvenanceCode provenanceCode, String operationCode);

  /**
   * 尝试通过 CAS(Compare-And-Set)获取租约(步骤 0)。
   *
   * <p><b>适用条件</b>: {@code QUEUED} 状态的任务,满足调度和租约接管条件:
   *
   * <ul>
   *   <li>{@code status_code='QUEUED' AND idempotent_key=#{idem}}
   *   <li>{@code scheduled_at IS NULL OR scheduled_at <= #{now}}
   *   <li>{@code leased_until IS NULL OR leased_until <= #{now} OR lease_owner=#{owner}}
   * </ul>
   *
   * <p><b>更新字段</b>: {@code lease_owner}, {@code leased_until}, {@code lease_count}+1。
   *
   * @param taskId 任务标识符
   * @param owner 租约拥有者(workerId:execId 或 execId)
   * @param now 当前时间戳(UTC)
   * @param ttlSeconds 租约 TTL(秒)
   * @param idempotentKey 幂等键(防御性检查)
   * @return {@code true} 表示获取成功;{@code false} 表示失败
   */
  boolean tryAcquireLease(
      Long taskId, String owner, Instant now, int ttlSeconds, String idempotentKey);

  /**
   * 将任务标记为 {@code RUNNING} 并刷新租约(步骤 1)。
   *
   * <p><b>前置条件</b>: {@code WHERE lease_owner=#{owner}}。更新 {@code status_code='RUNNING'}, {@code
   * started_at}, {@code last_heartbeat_at}, {@code leased_until}。
   *
   * @param taskId 任务标识符
   * @param owner 租约拥有者
   * @param now 当前时间戳
   * @param ttlSeconds 租约 TTL(秒)
   * @return {@code true} 表示更新成功;{@code false} 表示租约已丢失
   */
  boolean markRunningWithLease(Long taskId, String owner, Instant now, int ttlSeconds);

  /**
   * 通过心跳续期租约。
   *
   * <p><b>前置条件</b>: {@code WHERE lease_owner=#{owner}}。更新 {@code leased_until}, {@code
   * last_heartbeat_at}, {@code lease_count}+1。
   *
   * @param taskId 任务标识符
   * @param owner 租约拥有者
   * @param now 当前时间戳
   * @param ttlSeconds 租约 TTL(秒)
   * @return {@code true} 表示续期成功;{@code false} 表示租约已丢失
   */
  boolean renewLease(Long taskId, String owner, Instant now, int ttlSeconds);

  /**
   * 批量心跳续期(性能优化)。
   *
   * <p><b>前置条件</b>: {@code WHERE id IN (taskIds) AND lease_owner=#{owner}}。更新 {@code leased_until},
   * {@code last_heartbeat_at}, {@code lease_count}+1。
   *
   * @param taskIds 任务标识符列表
   * @param owner 租约拥有者
   * @param now 当前时间戳
   * @param ttlSeconds 租约 TTL(秒)
   * @return 成功续期的任务数量
   */
  int batchRenewLeases(List<Long> taskIds, String owner, Instant now, int ttlSeconds);
}
