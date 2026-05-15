package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.CursorEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 游标事件 JPA Repository。
///
/// **职责**：
///
/// - 提供 CursorEventEntity 的 CRUD 操作
/// - 记录游标推进事件时间线，用于审计和故障排查
///
/// @author linqibin
/// @since 0.1.0
public interface CursorEventDao extends JpaRepository<CursorEventEntity, Long> {

  /// 根据幂等键查找事件。
  ///
  /// @param idempotentKey 幂等键
  /// @return 事件实体
  Optional<CursorEventEntity> findByIdempotentKey(String idempotentKey);

  /// 检查幂等键是否存在。
  ///
  /// @param idempotentKey 幂等键
  /// @return 存在返回 true
  boolean existsByIdempotentKey(String idempotentKey);

  /// 根据任务 ID 查找事件列表。
  ///
  /// @param taskId 任务 ID
  /// @return 事件列表
  List<CursorEventEntity> findByTaskId(Long taskId);

  /// 根据数据源和操作和游标键查找事件列表。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作类型代码
  /// @param cursorKey 游标键
  /// @return 事件列表
  List<CursorEventEntity> findByProvenanceCodeAndOperationCodeAndCursorKey(
      String provenanceCode, String operationCode, String cursorKey);
}
