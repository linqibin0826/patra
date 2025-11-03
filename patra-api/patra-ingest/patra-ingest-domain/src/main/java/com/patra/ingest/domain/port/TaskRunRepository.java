package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 任务执行尝试(TaskRun)仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: 持久化任务执行尝试记录,并提供:
 *
 * <ul>
 *   <li>执行历史 - 记录每次任务执行的完整状态、指标、心跳
 *   <li>重试补偿 - 支持应用层实现重试逻辑
 *   <li>监控追溯 - 提供任务执行的完整审计轨迹
 *   <li>检查点管理 - 支持断点续传和状态恢复
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunRepository {

  /**
   * 持久化或更新任务执行尝试。
   *
   * <p><b>业务含义</b>: 保存任务执行尝试,包括状态、指标、心跳信息。
   *
   * @param run 任务执行实体,包含状态、指标、心跳信息
   * @return 已持久化的实体(通常包含自动生成的标识符)
   */
  TaskRun save(TaskRun run);

  /**
   * 查询指定任务的最新执行尝试。
   *
   * <p><b>业务含义</b>: 获取任务的最近一次执行记录。
   *
   * @param taskId 任务标识符
   * @return 最新的执行尝试(按尝试编号降序),或 {@link Optional#empty()}
   */
  Optional<TaskRun> findLatest(Long taskId);

  /**
   * 查询指定任务的完整执行历史。
   *
   * <p><b>业务含义</b>: 获取任务的所有执行尝试记录。
   *
   * @param taskId 任务标识符
   * @return 执行尝试列表(按实现排序,通常为尝试编号升序)
   */
  List<TaskRun> findAll(Long taskId);

  /**
   * 获取任务的最大尝试编号(用于派生下一个尝试 ID)。
   *
   * <p><b>业务含义</b>: 计算下一次重试的尝试编号。
   *
   * @param taskId 任务标识符
   * @return 最大尝试编号,或 {@code 0}(无执行记录时)
   */
  int getLatestAttemptNo(Long taskId);

  /**
   * 根据标识符查询执行尝试。
   *
   * <p><b>业务含义</b>: 通过技术主键(ID)检索执行尝试。
   *
   * @param runId run 标识符
   * @return 执行尝试,或 {@link Optional#empty()}
   */
  Optional<TaskRun> findById(Long runId);

  /**
   * 覆盖检查点并刷新心跳。
   *
   * <p><b>业务含义</b>: 更新执行尝试的检查点快照,同时刷新心跳时间。
   *
   * @param runId run 标识符
   * @param checkpointJson 检查点负载(null/空表示清除)
   * @param now 当前时间戳
   * @return {@code true} 表示更新成功
   */
  boolean updateCheckpointAndHeartbeat(Long runId, String checkpointJson, Instant now);

  /**
   * 刷新心跳(不修改检查点)。
   *
   * <p><b>业务含义</b>: 仅更新心跳时间,表示执行尝试仍在运行。
   *
   * @param runId run 标识符
   * @param now 当前时间戳
   * @return {@code true} 表示更新成功
   */
  boolean touchHeartbeat(Long runId, Instant now);

  /**
   * 标记执行尝试为失败并持久化错误上下文。
   *
   * <p><b>业务含义</b>: 将执行尝试标记为 {@code FAILED},保存错误信息。
   *
   * @param runId run 标识符
   * @param errorMessage 错误描述
   * @param now 当前时间戳
   * @return {@code true} 表示更新成功
   */
  boolean markFailed(Long runId, String errorMessage, Instant now);

  /**
   * 检查任务是否已有成功执行记录(幂等辅助方法)。
   *
   * <p><b>业务含义</b>: 判断任务是否已成功执行过,用于幂等性检查。
   *
   * @param taskId 任务标识符
   * @return {@code true} 表示存在 {@code SUCCEEDED} 执行记录
   */
  boolean hasSucceededRun(Long taskId);
}
