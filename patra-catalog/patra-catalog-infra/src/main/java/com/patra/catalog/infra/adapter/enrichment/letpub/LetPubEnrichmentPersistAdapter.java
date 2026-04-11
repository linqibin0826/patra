package com.patra.catalog.infra.adapter.enrichment.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.CasWarningDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.dao.VenueDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// [LetPubEnrichmentPersistPort] 的 JPA 实现。
///
/// **职责边界**：单一持久化单元——Mapper 展开 [LetPubVenueData] 为 JCR/CAS/Warning
/// 多行，`writeXxx` 方法按 `(venue_id, year[:edition])` 剔除重复，最后通过
/// DAO 批量写入。封面对象键若非 null，UPDATE `cat_venue.image_object_key`。
///
/// **10 年趋势填充**：LetPub 详情页返回 10 年 IF 趋势 + 多版本 CAS 分区数据，
/// Mapper 会全部展开，Adapter 按键去重——单次调用 `target_year=2025` 可以
/// 机会主义填充 2016-2024 的历史数据（仅插入之前缺失的年份）。
///
/// **事务**：本类**不加** `@Transactional`，由调用方（App 层 Worker）的
/// `REQUIRES_NEW` 事务边界包裹。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class LetPubEnrichmentPersistAdapter implements LetPubEnrichmentPersistPort {

  private final LetPubDataMapper dataMapper;
  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final CasWarningDao casWarningDao;
  private final VenueDao venueDao;

  @Override
  public PersistStats persist(long venueId, LetPubVenueData data, String coverObjectKey) {
    String sourceUrl = buildSourceUrl(data.basicInfo().letPubJournalId());

    int jcrCount = writeJcr(venueId, dataMapper.mapToJcrRatings(data, venueId, sourceUrl));
    int casCount = writeCas(venueId, dataMapper.mapToCasRatings(data, venueId, sourceUrl));
    int warnCount = writeWarnings(venueId, dataMapper.mapToCasWarnings(data, venueId, sourceUrl));

    boolean coverUpdated = false;
    if (coverObjectKey != null) {
      int updated = venueDao.updateImageObjectKey(venueId, coverObjectKey);
      if (updated == 0) {
        log.warn("Venue [id={}] 封面对象键 UPDATE 影响 0 行", venueId);
      } else {
        coverUpdated = true;
        log.debug("Venue [id={}] 已更新封面对象键: {}", venueId, coverObjectKey);
      }
    }
    return PersistStats.of(jcrCount, casCount, warnCount, coverUpdated);
  }

  /// 将 JCR 评级行按"年份已存在"去重后批量插入。
  ///
  /// 先从 `cat_venue_jcr_rating` 捞已有年份集合，再剔除 Mapper 生成的重复年份行。
  /// 空集合直接跳过写库（避免 no-op SQL 往返）。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 JCR 行列表（可能含历史年份），不为 null
  /// @return 实际插入的行数
  private int writeJcr(long venueId, List<JcrRatingEntity> all) {
    if (all.isEmpty()) return 0;
    Set<Short> existing = jcrRatingDao.findYearsByVenueId(venueId);
    List<JcrRatingEntity> toInsert =
        existing.isEmpty()
            ? all
            : all.stream().filter(r -> !existing.contains(r.getYear())).toList();
    if (toInsert.isEmpty()) return 0;
    jcrRatingDao.saveAll(toInsert);
    log.debug(
        "Venue [id={}] 插入 {} 条 JCR 评级（跳过 {} 条已存在年份）",
        venueId,
        toInsert.size(),
        all.size() - toInsert.size());
    return toInsert.size();
  }

  /// 将 CAS 评级行按 `(year, edition)` 组合去重后批量插入。
  ///
  /// CAS 分区每年可发布多个版本（基础版 / 升级版），因此去重键包含版本号。
  /// 已有组合以 `year:edition` 字符串形式从 DAO 投影查询返回。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 CAS 评级行列表，不为 null
  /// @return 实际插入的行数
  private int writeCas(long venueId, List<CasRatingEntity> all) {
    if (all.isEmpty()) return 0;
    Set<String> existing = casRatingDao.findKeysByVenueId(venueId);
    List<CasRatingEntity> toInsert =
        existing.isEmpty()
            ? all
            : all.stream()
                .filter(r -> !existing.contains(r.getYear() + ":" + r.getEdition()))
                .toList();
    if (toInsert.isEmpty()) return 0;
    casRatingDao.saveAll(toInsert);
    log.debug(
        "Venue [id={}] 插入 {} 条 CAS 评级（跳过 {} 条已存在版本）",
        venueId,
        toInsert.size(),
        all.size() - toInsert.size());
    return toInsert.size();
  }

  /// 将 CAS 预警行按 `(publishedYear, editionLabel)` 组合去重后批量插入。
  ///
  /// 预警数据使用"发布年份 + 版本标签"作为唯一键——与 CAS 评级的 `(year, edition)`
  /// 并行但字段名不同。
  ///
  /// @param venueId 目标 venue 主键
  /// @param all Mapper 展开的完整 CAS 预警行列表，不为 null
  /// @return 实际插入的行数
  private int writeWarnings(long venueId, List<CasWarningEntity> all) {
    if (all.isEmpty()) return 0;
    Set<String> existing = casWarningDao.findKeysByVenueId(venueId);
    List<CasWarningEntity> toInsert =
        existing.isEmpty()
            ? all
            : all.stream()
                .filter(w -> !existing.contains(w.getPublishedYear() + ":" + w.getEditionLabel()))
                .toList();
    if (toInsert.isEmpty()) return 0;
    casWarningDao.saveAll(toInsert);
    log.debug(
        "Venue [id={}] 插入 {} 条 CAS 预警（跳过 {} 条已存在）",
        venueId,
        toInsert.size(),
        all.size() - toInsert.size());
    return toInsert.size();
  }

  /// 构建 LetPub 详情页 URL 作为数据溯源，用于写入评级行的 `source_url` 列。
  ///
  /// @param journalId LetPub 站内的期刊 ID；若为 null 或空白返回 null（调用方宽容处理）
  /// @return LetPub 期刊详情页的完整 URL；`journalId` 为空时返回 null
  private static String buildSourceUrl(String journalId) {
    if (journalId == null || journalId.isBlank()) return null;
    return "https://www.letpub.com.cn/index.php?journalid="
        + journalId
        + "&page=journalapp&view=detail";
  }
}
