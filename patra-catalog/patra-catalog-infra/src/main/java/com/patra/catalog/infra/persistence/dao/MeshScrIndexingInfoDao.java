package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.MeshScrIndexingInfoEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// SCR 索引信息 JPA Repository。
///
/// **职责**：管理 MeshScrIndexingInfoEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrIndexingInfoDao extends JpaRepository<MeshScrIndexingInfoEntity, Long> {

  /// 按 SCR UI 查询所有索引信息。
  ///
  /// @param scrUi SCR UI
  /// @return 索引信息实体列表
  List<MeshScrIndexingInfoEntity> findAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量查询索引信息。
  ///
  /// @param scrUis SCR UI 列表
  /// @return 索引信息实体列表
  List<MeshScrIndexingInfoEntity> findAllByScrUiIn(Collection<String> scrUis);

  /// 按 SCR UI 删除所有索引信息。
  ///
  /// @param scrUi SCR UI
  void deleteAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量删除索引信息。
  ///
  /// @param scrUis SCR UI 列表
  void deleteAllByScrUiIn(Collection<String> scrUis);
}
