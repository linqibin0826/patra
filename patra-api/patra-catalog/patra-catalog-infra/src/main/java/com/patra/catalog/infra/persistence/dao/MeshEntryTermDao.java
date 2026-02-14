package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.MeshEntryTermEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// MeSH 入口术语 JPA Repository。
///
/// **职责**：管理 MeshEntryTermEntity 的 CRUD 操作，支持 Descriptor 和 SCR 两种记录类型。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshEntryTermDao extends JpaRepository<MeshEntryTermEntity, Long> {

  /// 按所有者 UI 查询所有入口术语。
  ///
  /// @param ownerUi 所有者 UI（Descriptor: D开头，SCR: C开头）
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByOwnerUi(String ownerUi);

  /// 按所有者 UI 列表批量查询入口术语。
  ///
  /// @param ownerUis 所有者 UI 列表
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByOwnerUiIn(Collection<String> ownerUis);

  /// 按概念 UI 查询所有入口术语。
  ///
  /// @param conceptUi 概念 UI
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByConceptUi(String conceptUi);

  /// 按所有者 UI 删除所有入口术语。
  ///
  /// @param ownerUi 所有者 UI
  void deleteAllByOwnerUi(String ownerUi);

  /// 按所有者 UI 列表批量删除入口术语。
  ///
  /// @param ownerUis 所有者 UI 列表
  void deleteAllByOwnerUiIn(Collection<String> ownerUis);

  /// 按记录类型和所有者 UI 查询入口术语。
  ///
  /// @param recordType 记录类型（DESCRIPTOR/SCR）
  /// @param ownerUi 所有者 UI
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByRecordTypeAndOwnerUi(String recordType, String ownerUi);

  /// 按记录类型和所有者 UI 列表批量查询入口术语。
  ///
  /// @param recordType 记录类型（DESCRIPTOR/SCR）
  /// @param ownerUis 所有者 UI 列表
  /// @return 入口术语实体列表
  List<MeshEntryTermEntity> findAllByRecordTypeAndOwnerUiIn(
      String recordType, Collection<String> ownerUis);
}
