package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.MeshScrSourceEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// SCR 来源 JPA Repository。
///
/// **职责**：管理 MeshScrSourceEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrSourceDao extends JpaRepository<MeshScrSourceEntity, Long> {

  /// 按 SCR UI 查询所有来源。
  ///
  /// @param scrUi SCR UI
  /// @return 来源实体列表
  List<MeshScrSourceEntity> findAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量查询来源。
  ///
  /// @param scrUis SCR UI 列表
  /// @return 来源实体列表
  List<MeshScrSourceEntity> findAllByScrUiIn(Collection<String> scrUis);

  /// 按 SCR UI 删除所有来源。
  ///
  /// @param scrUi SCR UI
  void deleteAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量删除来源。
  ///
  /// @param scrUis SCR UI 列表
  void deleteAllByScrUiIn(Collection<String> scrUis);
}
