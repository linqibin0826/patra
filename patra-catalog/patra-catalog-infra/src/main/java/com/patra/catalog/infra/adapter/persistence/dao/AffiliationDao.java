package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.AffiliationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 机构 JPA Repository。
///
/// **职责**：
///
/// - 提供机构实体的 CRUD 操作
/// - 支持按 ROR ID、GRID ID、去重键查询
/// - 支持按国家、机构类型筛选
/// - 支持批量操作（继承自 JpaRepository）
///
/// @author linqibin
/// @since 0.1.0
public interface AffiliationDao extends JpaRepository<AffiliationEntity, Long> {

  /// 根据 ROR ID 查询机构。
  ///
  /// @param rorId ROR 标识符
  /// @return 机构实体（可选）
  Optional<AffiliationEntity> findByRorId(String rorId);

  /// 根据 GRID ID 查询机构。
  ///
  /// @param gridId GRID 标识符
  /// @return 机构实体（可选）
  Optional<AffiliationEntity> findByGridId(String gridId);

  /// 根据去重键查询机构。
  ///
  /// @param dedupKey 去重键
  /// @return 机构实体列表
  List<AffiliationEntity> findByDedupKey(String dedupKey);

  /// 根据国家查询机构。
  ///
  /// @param country 国家代码
  /// @return 机构实体列表
  List<AffiliationEntity> findByCountry(String country);

  /// 检查 ROR ID 是否已存在。
  ///
  /// @param rorId ROR 标识符
  /// @return true 如果已存在
  boolean existsByRorId(String rorId);

  /// 检查 GRID ID 是否已存在。
  ///
  /// @param gridId GRID 标识符
  /// @return true 如果已存在
  boolean existsByGridId(String gridId);

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  @Query("SELECT COUNT(a) > 0 FROM AffiliationEntity a")
  boolean hasAnyData();

  /// 根据机构名称模糊查询。
  ///
  /// @param name 机构名称（模糊匹配）
  /// @return 机构实体列表
  @Query("SELECT a FROM AffiliationEntity a WHERE a.name LIKE %:name%")
  List<AffiliationEntity> findByNameContaining(@Param("name") String name);
}
