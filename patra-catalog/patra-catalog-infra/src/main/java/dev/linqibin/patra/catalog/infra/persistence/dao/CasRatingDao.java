package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.CasRatingEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// CAS 中科院分区 JPA Repository。
///
/// **职责**：
///
/// - 提供 CasRatingEntity 的 CRUD 操作
/// - 支持按业务唯一键 `(venueId, year, edition)` 查询（upsert 查重）
/// - 支持按 venueId 批量查询（ReadAdapter 批量组装用）
/// - 支持查找最新年份评级
/// - 支持投影查询 `(年份:版本)` 键集合（Writer 过滤用）
///
/// @author linqibin
/// @since 0.1.0
public interface CasRatingDao extends JpaRepository<CasRatingEntity, Long> {

  /// 按业务唯一键查找 CAS 评级。
  ///
  /// @param venueId 期刊 ID
  /// @param year 分区年份
  /// @param edition CAS 版本名称（升级版/新锐版/基础版）
  /// @return CAS 评级实体
  Optional<CasRatingEntity> findByVenueIdAndYearAndEdition(
      Long venueId, Short year, String edition);

  /// 查找某期刊的所有 CAS 评级。
  ///
  /// @param venueId 期刊 ID
  /// @return CAS 评级列表
  List<CasRatingEntity> findByVenueId(Long venueId);

  /// 批量查找多个期刊的 CAS 评级（ReadAdapter 分页组装用）。
  ///
  /// @param venueIds 期刊 ID 列表
  /// @return CAS 评级列表
  List<CasRatingEntity> findByVenueIdIn(Collection<Long> venueIds);

  /// 查找某期刊已有的 `(年份:版本)` 键集合（投影，Writer 过滤用）。
  ///
  /// 返回格式为 `"year:edition"` 的字符串集合（如 `"2026:新锐版"`），
  /// 避免载入完整实体字段仅用于重复判断。
  ///
  /// @param venueId 期刊 ID
  /// @return 已有评级的 `(年份:版本)` 键集合
  @Query("SELECT CONCAT(r.year, ':', r.edition) FROM CasRatingEntity r WHERE r.venueId = :venueId")
  Set<String> findKeysByVenueId(@Param("venueId") Long venueId);

  /// 查找某期刊最新年份的 CAS 评级（优先升级版）。
  ///
  /// @param venueId 期刊 ID
  /// @return 最新年份的 CAS 评级
  @Query(
      """
      SELECT r FROM CasRatingEntity r
      WHERE r.venueId = :venueId
      ORDER BY r.year DESC, r.edition ASC
      LIMIT 1
      """)
  Optional<CasRatingEntity> findLatestByVenueId(@Param("venueId") Long venueId);
}
