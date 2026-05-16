package dev.linqibin.patra.ingest.domain.port;

import dev.linqibin.patra.ingest.domain.model.entity.TaskRunBatch;
import java.util.List;
import java.util.Optional;

/// 任务执行批次(TaskRunBatch)仓储端口(六边形架构 - Domain → Infrastructure)。
///
/// **职责**: 持久化和查询任务执行批次状态,支持:
///
/// - 批次持久化 - 保存任务执行过程中的分页/分批状态
///   - 批次查询 - 按 runId 查询批次列表
///   - Cursor 血缘 - 记录触发 Cursor 推进的批次 ID
///
/// **端口语义**: 此接口是六边形架构中的 **仓储端口(Repository Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
///
/// @author linqibin
/// @since 0.1.0
public interface TaskRunBatchRepository {

  /// 持久化单个任务执行批次并返回持久化后的实体。
  ///
  /// **业务含义**: 保存单个批次实体,包括批次状态和指标。
  ///
  /// **返回值说明**: 返回持久化后的批次实体副本,包含数据库生成的 ID。 如果批次 ID 为 null,则执行 INSERT 并回填 ID;否则执行 UPDATE。
  ///
  /// @param batch 批次实体
  /// @return 持久化后的批次实体（包含数据库生成的 ID）
  TaskRunBatch save(TaskRunBatch batch);

  /// 批量持久化多个任务执行批次。
  ///
  /// **业务含义**: 一次性保存多个批次,用于批量执行场景。
  ///
  /// @param batches 批次实体列表,包括状态和指标
  void saveAll(List<TaskRunBatch> batches);

  /// 根据 run 标识符查询批次列表。
  ///
  /// **业务含义**: 获取指定 run 的所有批次。
  ///
  /// @param runId run 标识符
  /// @return 属于该 run 的批次列表
  List<TaskRunBatch> findByRunId(Long runId);

  /// 查询指定 run 的最后成功批次 ID。
  ///
  /// **业务含义**: 用于 Cursor 血缘跟踪,记录触发 Cursor 推进的批次 ID。
  ///
  /// @param runId run 标识符
  /// @return 批次 ID(按 ID 顺序的最新 SUCCEEDED 批次),或 {@link Optional#empty()}
  Optional<Long> findLastSucceededBatchId(Long runId);
}
