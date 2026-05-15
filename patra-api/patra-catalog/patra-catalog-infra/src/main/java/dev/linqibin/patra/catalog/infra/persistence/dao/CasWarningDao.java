package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.CasWarningEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// CAS 中科院期刊预警名单 JPA Repository。
///
/// **职责**：
///
/// - 提供 CasWarningEntity 的 CRUD 操作
/// - 支持按业务唯一键 `(venueId, publishedYear, editionLabel)` 查询（upsert 查重）
/// - 支持按 venueId 批量查询（ReadAdapter 批量组装用）
/// - 支持投影查询 `(year:label)` 键集合（Writer 过滤用）
///
/// @author linqibin
/// @since 0.1.0
public interface CasWarningDao extends JpaRepository<CasWarningEntity, Long> {

  /// 按业务唯一键查找预警记录。
  ///
  /// @param venueId 期刊 ID
  /// @param publishedYear 预警发布年份
  /// @param editionLabel 预警版本标签
  /// @return 预警记录实体
  Optional<CasWarningEntity> findByVenueIdAndPublishedYearAndEditionLabel(
      Long venueId, Short publishedYear, String editionLabel);

  /// 查询指定期刊的最新预警记录。
  ///
  /// @param venueId 期刊 ID
  /// @return 最新预警记录，不存在时返回 Optional.empty()
  @Query(
      value =
          """
      SELECT * FROM cat_venue_cas_warning w
      WHERE w.venue_id = :venueId
      ORDER BY w.published_year DESC, w.id DESC
      LIMIT 1
      """,
      nativeQuery = true)
  Optional<CasWarningEntity> findLatestByVenueId(@Param("venueId") Long venueId);

  /// 查找某期刊的所有预警记录。
  ///
  /// @param venueId 期刊 ID
  /// @return 预警记录列表
  List<CasWarningEntity> findByVenueId(Long venueId);

  /// 批量查找多个期刊的预警记录（ReadAdapter 分页组装用）。
  ///
  /// @param venueIds 期刊 ID 列表
  /// @return 预警记录列表
  List<CasWarningEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 查找某期刊已有的 `(year:label)` 键集合（投影，Writer 过滤用）。
  ///
  /// 返回格式为 `"year:label"` 的字符串集合（如 `"2025:2025版"`），
  /// 避免载入完整实体字段仅用于重复判断。
  ///
  /// @param venueId 期刊 ID
  /// @return 已有预警记录的 `(year:label)` 键集合
  @Query(
      "SELECT CONCAT(w.publishedYear, ':', w.editionLabel) FROM CasWarningEntity w WHERE w.venueId = :venueId")
  Set<String> findKeysByVenueId(@Param("venueId") Long venueId);
}
