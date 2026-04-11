package com.patra.catalog.infra.adapter.enrichment.scopus;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// [ScopusEnrichmentPersistPort] 的 JPA 实现。
///
/// **职责边界**：单一持久化单元——Mapper 展开 [ScopusVenueData] 为
/// `ScopusRatingEntity` 多行，按 `(venue_id, year)` 剔除重复，最后通过
/// `ScopusRatingDao` 批量写入。与 LetPub 版本相比：
///
/// - 只有一张目标表 `cat_venue_scopus_rating`（vs LetPub 3 张表）
/// - 去重键仅为 `year`（vs LetPub `year:edition`）
/// - 无封面下载逻辑（Scopus API 不提供封面）
///
/// **历史数据填充**：Scopus API 的 `yearlyMetrics` 通常包含近 3-5 年的数据。
/// 最新年份填充完整指标（SJR/SNIP 等），
/// 历史年份仅填充 CiteScore 基础字段。Adapter 按年份去重保证幂等，
/// 跨次调用不会重复插入同一年份。
///
/// **事务**：本类**不加** `@Transactional`，由调用方（App 层 Worker）的
/// `REQUIRES_NEW` 事务边界包裹。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopusEnrichmentPersistAdapter implements ScopusEnrichmentPersistPort {

  private final ScopusDataMapper dataMapper;
  private final ScopusRatingDao scopusRatingDao;

  @Override
  public PersistStats persist(long venueId, ScopusVenueData data) {
    List<ScopusRatingEntity> all = dataMapper.mapToScopusRatings(data, venueId);
    int inserted = writeRatings(venueId, all);
    return PersistStats.of(inserted);
  }

  /// 将 Scopus 评级行按"年份已存在"去重后批量插入。
  ///
  /// 先从 `cat_venue_scopus_rating` 捞已有年份集合，再剔除 Mapper 生成的
  /// 重复年份行。空集合直接跳过写库（避免 no-op SQL 往返）。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 Scopus 评级行列表，不为 null
  /// @return 实际插入的行数
  private int writeRatings(long venueId, List<ScopusRatingEntity> all) {
    if (all.isEmpty()) return 0;
    Set<Short> existing = scopusRatingDao.findYearsByVenueId(venueId);
    List<ScopusRatingEntity> toInsert =
        existing.isEmpty()
            ? all
            : all.stream().filter(r -> !existing.contains(r.getYear())).toList();
    if (toInsert.isEmpty()) return 0;
    scopusRatingDao.saveAll(toInsert);
    log.debug(
        "Venue [id={}] 插入 {} 条 Scopus 评级（跳过 {} 条已存在年份）",
        venueId,
        toInsert.size(),
        all.size() - toInsert.size());
    return toInsert.size();
  }
}
