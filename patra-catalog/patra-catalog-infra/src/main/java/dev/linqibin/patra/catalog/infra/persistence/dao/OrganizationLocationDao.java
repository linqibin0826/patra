package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationLocationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 机构地理位置 JPA Repository。
///
/// **职责**：管理 OrganizationLocationEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationLocationDao extends JpaRepository<OrganizationLocationEntity, Long> {

  /// 按机构 ID 查询所有地理位置。
  ///
  /// @param orgId 机构 ID
  /// @return 地理位置实体列表
  List<OrganizationLocationEntity> findAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量查询地理位置。
  ///
  /// @param orgIds 机构 ID 列表
  /// @return 地理位置实体列表
  List<OrganizationLocationEntity> findAllByOrgIdIn(Collection<Long> orgIds);

  /// 按机构 ID 删除所有地理位置。
  ///
  /// @param orgId 机构 ID
  void deleteAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量删除地理位置。
  ///
  /// @param orgIds 机构 ID 列表
  void deleteAllByOrgIdIn(Collection<Long> orgIds);
}
