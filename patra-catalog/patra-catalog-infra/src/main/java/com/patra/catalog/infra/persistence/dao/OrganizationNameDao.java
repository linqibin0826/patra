package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.OrganizationNameEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 机构名称 JPA Repository。
///
/// **职责**：管理 OrganizationNameEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationNameDao extends JpaRepository<OrganizationNameEntity, Long> {

  /// 按机构 ID 查询所有名称。
  ///
  /// @param orgId 机构 ID
  /// @return 名称实体列表
  List<OrganizationNameEntity> findAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量查询名称。
  ///
  /// @param orgIds 机构 ID 列表
  /// @return 名称实体列表
  List<OrganizationNameEntity> findAllByOrgIdIn(Collection<Long> orgIds);

  /// 按机构 ID 删除所有名称。
  ///
  /// @param orgId 机构 ID
  void deleteAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量删除名称。
  ///
  /// @param orgIds 机构 ID 列表
  void deleteAllByOrgIdIn(Collection<Long> orgIds);
}
