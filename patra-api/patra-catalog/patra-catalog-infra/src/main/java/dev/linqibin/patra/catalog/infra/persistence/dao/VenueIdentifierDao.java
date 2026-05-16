package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.VenueIdentifierEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 载体标识符 JPA Repository。
///
/// **职责**：
///
/// - 提供 VenueIdentifierEntity 的 CRUD 操作
/// - 支持按 venueId 批量查询和删除
/// - 支持按标识符类型和值反查载体
///
/// @author linqibin
/// @since 0.1.0
public interface VenueIdentifierDao extends JpaRepository<VenueIdentifierEntity, Long> {

  /// 根据载体 ID 查找所有标识符。
  ///
  /// @param venueId 载体 ID
  /// @return 标识符实体列表
  List<VenueIdentifierEntity> findByVenueId(Long venueId);

  /// 根据载体 ID 列表批量查找标识符。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 标识符实体列表
  List<VenueIdentifierEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 根据标识符类型和值查找标识符实体。
  ///
  /// 用于按 ISSN 反查载体。
  ///
  /// @param identifierType 标识符类型（如 ISSN、ISSN_L）
  /// @param identifierValues 标识符值列表
  /// @return 匹配的标识符实体列表
  List<VenueIdentifierEntity> findByIdentifierTypeAndIdentifierValueIn(
      String identifierType, Collection<String> identifierValues);

  /// 按多种类型和值查找标识符。
  ///
  /// 用于按 ISSN（包括 ISSN_L 和普通 ISSN）反查载体。
  ///
  /// @param types 标识符类型列表
  /// @param values 标识符值列表
  /// @return 匹配的标识符实体列表
  @Query(
      """
      SELECT e FROM VenueIdentifierEntity e
      WHERE e.identifierType IN :types
        AND e.identifierValue IN :values
      """)
  List<VenueIdentifierEntity> findByTypesAndValues(
      @Param("types") Collection<String> types, @Param("values") Collection<String> values);

  /// 根据载体 ID 删除所有标识符。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的记录数
  @Modifying
  int deleteByVenueId(Long venueId);

  /// 根据载体 ID 列表批量删除标识符。
  ///
  /// @param venueIds 载体 ID 列表
  /// @return 删除的记录数
  @Modifying
  @Query("DELETE FROM VenueIdentifierEntity e WHERE e.venueId IN :venueIds")
  int deleteByVenueIdIn(@Param("venueIds") Collection<Long> venueIds);
}
