package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.CursorEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 游标 JPA Repository。
///
/// **职责**：
///
/// - 提供 CursorEntity 的 CRUD 操作
/// - 支持按组合唯一键查询游标
/// - 维护数据源增量同步位置
///
/// @author linqibin
/// @since 0.1.0
public interface CursorDao extends JpaRepository<CursorEntity, Long> {

  /// 根据组合唯一键查找游标。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作类型代码
  /// @param cursorKey 游标键
  /// @param namespaceScopeCode 命名空间范围代码
  /// @param namespaceKey 命名空间键
  /// @return 游标实体
  Optional<CursorEntity>
      findByProvenanceCodeAndOperationCodeAndCursorKeyAndNamespaceScopeCodeAndNamespaceKey(
          String provenanceCode,
          String operationCode,
          String cursorKey,
          String namespaceScopeCode,
          String namespaceKey);

  /// 根据数据源和操作查找游标列表。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationCode 操作类型代码
  /// @return 游标列表
  List<CursorEntity> findByProvenanceCodeAndOperationCode(
      String provenanceCode, String operationCode);

  /// 根据数据源查找游标列表。
  ///
  /// @param provenanceCode 数据源代码
  /// @return 游标列表
  List<CursorEntity> findByProvenanceCode(String provenanceCode);
}
