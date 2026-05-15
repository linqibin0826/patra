package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrPharmacologicalActionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// SCR 药理作用 JPA Repository。
///
/// **职责**：管理 MeshScrPharmacologicalActionEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrPharmacologicalActionDao
    extends JpaRepository<MeshScrPharmacologicalActionEntity, Long> {

  /// 按 SCR UI 查询所有药理作用。
  ///
  /// @param scrUi SCR UI
  /// @return 药理作用实体列表
  List<MeshScrPharmacologicalActionEntity> findAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量查询药理作用。
  ///
  /// @param scrUis SCR UI 列表
  /// @return 药理作用实体列表
  List<MeshScrPharmacologicalActionEntity> findAllByScrUiIn(Collection<String> scrUis);

  /// 按 Descriptor UI 查询所有药理作用（反向查询）。
  ///
  /// @param descriptorUi Descriptor UI
  /// @return 药理作用实体列表
  List<MeshScrPharmacologicalActionEntity> findAllByDescriptorUi(String descriptorUi);

  /// 按 SCR UI 删除所有药理作用。
  ///
  /// @param scrUi SCR UI
  void deleteAllByScrUi(String scrUi);

  /// 按 SCR UI 列表批量删除药理作用。
  ///
  /// @param scrUis SCR UI 列表
  void deleteAllByScrUiIn(Collection<String> scrUis);
}
