package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.MeshConceptEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 概念 JPA Repository。
///
/// **职责**：管理 MeshConceptEntity 的 CRUD 操作，支持 Descriptor 和 SCR 两种记录类型。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshConceptDao extends JpaRepository<MeshConceptEntity, Long> {

  /// 按所有者 UI 查询所有概念。
  ///
  /// @param ownerUi 所有者 UI（Descriptor: D开头，SCR: C开头）
  /// @return 概念实体列表
  List<MeshConceptEntity> findAllByOwnerUi(String ownerUi);

  /// 按所有者 UI 列表批量查询概念。
  ///
  /// @param ownerUis 所有者 UI 列表
  /// @return 概念实体列表
  List<MeshConceptEntity> findAllByOwnerUiIn(Collection<String> ownerUis);

  /// 按所有者 UI 删除所有概念。
  ///
  /// @param ownerUi 所有者 UI
  void deleteAllByOwnerUi(String ownerUi);

  /// 按所有者 UI 列表批量删除概念。
  ///
  /// @param ownerUis 所有者 UI 列表
  void deleteAllByOwnerUiIn(Collection<String> ownerUis);

  /// 按记录类型和所有者 UI 查询概念。
  ///
  /// @param recordType 记录类型（DESCRIPTOR/SCR）
  /// @param ownerUi 所有者 UI
  /// @return 概念实体列表
  List<MeshConceptEntity> findAllByRecordTypeAndOwnerUi(String recordType, String ownerUi);

  /// 按记录类型和所有者 UI 列表批量查询概念。
  ///
  /// @param recordType 记录类型（DESCRIPTOR/SCR）
  /// @param ownerUis 所有者 UI 列表
  /// @return 概念实体列表
  List<MeshConceptEntity> findAllByRecordTypeAndOwnerUiIn(
      String recordType, Collection<String> ownerUis);
}
