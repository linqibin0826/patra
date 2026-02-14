package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.OrganizationRelationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 机构关系 JPA Repository。
///
/// **职责**：管理 OrganizationRelationEntity 的 CRUD 操作。
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationRelationDao extends JpaRepository<OrganizationRelationEntity, Long> {

  /// 按机构 ID 查询所有关系。
  ///
  /// @param orgId 机构 ID
  /// @return 关系实体列表
  List<OrganizationRelationEntity> findAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量查询关系。
  ///
  /// @param orgIds 机构 ID 列表
  /// @return 关系实体列表
  List<OrganizationRelationEntity> findAllByOrgIdIn(Collection<Long> orgIds);

  /// 按机构 ID 删除所有关系。
  ///
  /// @param orgId 机构 ID
  void deleteAllByOrgId(Long orgId);

  /// 按机构 ID 列表批量删除关系。
  ///
  /// @param orgIds 机构 ID 列表
  void deleteAllByOrgIdIn(Collection<Long> orgIds);

  /// 查询所有未关联内部 ID 的关系。
  ///
  /// 用于导入后的延迟关联填充。
  ///
  /// @return 未关联的关系实体列表
  @Query("SELECT r FROM OrganizationRelationEntity r WHERE r.relatedOrgId IS NULL")
  List<OrganizationRelationEntity> findAllUnlinked();

  /// 批量更新关系的内部关联 ID。
  ///
  /// 根据 ROR ID 填充 related_org_id 字段。
  ///
  /// @param relatedRorId 关联机构的 ROR ID
  /// @param relatedOrgId 关联机构的内部 ID
  /// @return 更新的记录数
  @Modifying
  @Query(
      "UPDATE OrganizationRelationEntity r SET r.relatedOrgId = :relatedOrgId WHERE r.relatedRorId = :relatedRorId")
  int linkByRorId(
      @Param("relatedRorId") String relatedRorId, @Param("relatedOrgId") Long relatedOrgId);
}
