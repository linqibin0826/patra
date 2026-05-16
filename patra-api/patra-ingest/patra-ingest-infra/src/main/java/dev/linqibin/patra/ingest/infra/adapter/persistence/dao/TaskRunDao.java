package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.TaskRunEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 任务执行记录 JPA Repository。
///
/// **职责**：
///
/// - 提供 TaskRunEntity 的 CRUD 操作
/// - 支持获取最新尝试编号
/// - 提供检查点和心跳更新操作
///
/// @author linqibin
/// @since 0.1.0
public interface TaskRunDao extends JpaRepository<TaskRunEntity, Long> {

  /// 根据任务 ID 查找所有运行记录。
  ///
  /// @param taskId 任务 ID
  /// @return 运行记录列表
  List<TaskRunEntity> findByTaskId(Long taskId);

  /// 根据任务 ID 和尝试编号查找运行记录。
  ///
  /// @param taskId 任务 ID
  /// @param attemptNo 尝试编号
  /// @return 运行记录
  Optional<TaskRunEntity> findByTaskIdAndAttemptNo(Long taskId, Integer attemptNo);

  /// 获取任务的最新尝试编号。
  ///
  /// @param taskId 任务 ID
  /// @return 最大 attemptNo，无记录时返回 null
  @Query("SELECT MAX(r.attemptNo) FROM TaskRunEntity r WHERE r.taskId = :taskId")
  Integer selectLatestAttemptNo(@Param("taskId") Long taskId);

  /// 覆盖检查点并刷新心跳时间戳。
  ///
  /// @param runId 运行记录 ID
  /// @param checkpointJson 检查点 JSON 字符串
  /// @param now 当前时间
  /// @return 受影响行数
  @Modifying
  @Query(
      value =
          """
          UPDATE ing_task_run
          SET checkpoint = CAST(:checkpointJson AS JSON),
              last_heartbeat = :now,
              version = version + 1
          WHERE id = :runId
          """,
      nativeQuery = true)
  int updateCheckpointAndHeartbeat(
      @Param("runId") Long runId,
      @Param("checkpointJson") String checkpointJson,
      @Param("now") Instant now);

  /// 刷新心跳时间戳。
  ///
  /// @param runId 运行记录 ID
  /// @param now 当前时间
  /// @return 受影响行数
  @Modifying
  @Query(
      """
      UPDATE TaskRunEntity r
      SET r.lastHeartbeat = :now, r.version = r.version + 1
      WHERE r.id = :runId
      """)
  int touchHeartbeat(@Param("runId") Long runId, @Param("now") Instant now);

  /// 标记运行记录为失败。
  ///
  /// @param runId 运行记录 ID
  /// @param errorMsg 错误消息
  /// @param now 当前时间
  /// @return 受影响行数
  @Modifying
  @Query(
      """
      UPDATE TaskRunEntity r
      SET r.statusCode = 'FAILED',
          r.error = :errorMsg,
          r.finishedAt = :now,
          r.version = r.version + 1
      WHERE r.id = :runId
      """)
  int markFailed(
      @Param("runId") Long runId, @Param("errorMsg") String errorMsg, @Param("now") Instant now);
}
