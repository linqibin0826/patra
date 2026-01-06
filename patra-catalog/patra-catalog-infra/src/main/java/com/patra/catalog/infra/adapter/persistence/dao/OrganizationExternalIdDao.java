package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.OrganizationExternalIdEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 机构外部标识符 JPA Repository。
///
/// **职责**：管理 OrganizationExternalIdEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationExternalIdDao
    extends JpaRepository<OrganizationExternalIdEntity, Long> {

  /// 按机构 ID 查询所有外部标识符。
  ///
  /// @param orgId 机构 ID
  /// @return 外部标识符实体列表
  List<OrganizationExternalIdEntity> findAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量查询外部标识符。
  ///
  /// @param orgIds 机构 ID 列表
  /// @return 外部标识符实体列表
  List<OrganizationExternalIdEntity> findAllByOrgIdIn(Collection<Long> orgIds);

  /// 按机构 ID 删除所有外部标识符。
  ///
  /// @param orgId 机构 ID
  void deleteAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量删除外部标识符。
  ///
  /// @param orgIds 机构 ID 列表
  void deleteAllByOrgIdIn(Collection<Long> orgIds);
}
