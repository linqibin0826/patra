package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.TaskRunBatchEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 任务执行批次 JPA Repository。
///
/// **职责**：
///
/// - 提供 TaskRunBatchEntity 的 CRUD 操作
/// - 将一组 TaskRun 记录分组为逻辑批次
///
/// @author linqibin
/// @since 0.1.0
public interface TaskRunBatchDao extends JpaRepository<TaskRunBatchEntity, Long> {

  /// 根据运行记录 ID 查找批次。
  ///
  /// @param runId 运行记录 ID
  /// @return 批次列表
  List<TaskRunBatchEntity> findByRunId(Long runId);

  /// 根据任务 ID 查找所有批次。
  ///
  /// @param taskId 任务 ID
  /// @return 批次列表
  List<TaskRunBatchEntity> findByTaskId(Long taskId);
}
