package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/// MeSH SCR JPA Repository。
///
/// **职责**：管理 MeshScrEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrDao extends JpaRepository<MeshScrEntity, Long> {

  /// 判断是否存在任何数据。
  ///
  /// @return true 如果存在数据
  @Query("SELECT COUNT(s) > 0 FROM MeshScrEntity s")
  boolean hasAnyData();

  /// 按 UI 查询 SCR。
  ///
  /// @param ui SCR UI
  /// @return SCR 实体（如果存在）
  Optional<MeshScrEntity> findByUi(String ui);

  /// 按 UI 列表批量查询 SCR。
  ///
  /// @param uis UI 列表
  /// @return SCR 实体列表
  List<MeshScrEntity> findAllByUiIn(Collection<String> uis);

  /// 按名称列表批量查询 SCR。
  ///
  /// @param names SCR 名称列表
  /// @return SCR 实体列表
  List<MeshScrEntity> findAllByNameIn(Collection<String> names);
}
