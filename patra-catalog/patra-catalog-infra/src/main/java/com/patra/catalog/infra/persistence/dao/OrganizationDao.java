package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.OrganizationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 机构 JPA Repository。
///
/// **职责**：
///
/// - 提供 OrganizationEntity 的 CRUD 操作
/// - 支持 ROR ID 查询和批量导入
///
/// @author linqibin
/// @since 0.1.0
public interface OrganizationDao extends JpaRepository<OrganizationEntity, Long> {

  /// 检查是否存在任何数据。
  ///
  /// 用于导入前检查表是否为空。
  ///
  /// @return 如果存在数据则返回 true
  @Query("SELECT COUNT(o) > 0 FROM OrganizationEntity o")
  boolean hasAnyData();

  /// 根据 ROR ID 查找机构。
  ///
  /// @param rorId ROR ID（不含 URL 前缀）
  /// @return 机构实体
  Optional<OrganizationEntity> findByRorId(String rorId);

  /// 检查 ROR ID 是否已存在。
  ///
  /// @param rorId ROR ID
  /// @return 如果存在返回 true
  boolean existsByRorId(String rorId);

  /// 根据 ROR ID 列表批量查找机构。
  ///
  /// @param rorIds ROR ID 列表
  /// @return 机构实体列表
  List<OrganizationEntity> findByRorIdIn(Collection<String> rorIds);

  /// 批量查询已存在的 ROR ID。
  ///
  /// 用于导入前的去重检查。
  ///
  /// @param rorIds ROR ID 列表
  /// @return 数据库中已存在的 ROR ID 列表
  @Query("SELECT o.rorId FROM OrganizationEntity o WHERE o.rorId IN :rorIds")
  List<String> findExistingRorIds(@Param("rorIds") Collection<String> rorIds);

  /// 根据 ID 列表批量查找机构。
  ///
  /// @param ids 机构 ID 列表
  /// @return 机构实体列表
  List<OrganizationEntity> findByIdIn(Collection<Long> ids);

  /// 按状态统计机构数量。
  ///
  /// @return 状态代码到数量的列表（每行：[status, count]）
  @Query("SELECT o.status, COUNT(o) FROM OrganizationEntity o GROUP BY o.status")
  List<Object[]> countByStatus();

  /// 根据显示名称精确查找机构。
  ///
  /// 用于资助机构名称匹配场景。
  ///
  /// @param displayName 机构显示名称
  /// @return 机构实体
  Optional<OrganizationEntity> findByDisplayName(String displayName);
}
