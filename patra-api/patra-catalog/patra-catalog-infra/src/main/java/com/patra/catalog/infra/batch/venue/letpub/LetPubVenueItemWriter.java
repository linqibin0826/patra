package com.patra.catalog.infra.batch.venue.letpub;

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
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// LetPub 期刊富化 Writer。
///
/// **四步写入**：
///
/// 1. 通过 {@link JcrRatingDao} 保存 JCR 评级行（→ `cat_venue_jcr_rating`），
///    **过滤已存在的年份**，仅插入新年份数据
/// 2. 通过 {@link CasRatingDao} 保存 CAS 评级行（→ `cat_venue_cas_rating`），
///    **过滤已存在的 `(年份, 版本)` 组合**，仅插入新版本数据
/// 3. 通过 {@link CasWarningDao} 保存 CAS 预警记录（→ `cat_venue_cas_warning`），
///    **过滤已存在的 `(发布年份, 版本标签)` 组合**，仅插入新记录
/// 4. 通过 {@link VenueDao#updateImageObjectKey} 更新封面对象键
///    （仅当 `result.imageObjectKey()` 非空时）
///
/// **断点续传**：不再依赖 `letpub_fetched_at` 标记字段，
/// 改由 Reader 的 `NOT EXISTS` 子查询（基于目标年份）实现。
///
/// **封面更新为何不用 dirty check**：
///
/// Reader 是 `JpaPagingItemReader`，读出的 `VenueEntity` 处于 detached 状态，
/// 无法通过 dirty check 自动持久化字段变更。因此由 Writer 显式 UPDATE。
///
/// **失败语义**：
///
/// 三步写入共享 Step 事务。若封面 UPDATE 异常（例如瞬时锁冲突），
/// 同一 chunk 的 JCR/CAS 插入也会随之回滚。因 `chunk=1`，影响范围
/// 限于当前 venue；后续重跑时 Reader 的 `NOT EXISTS` 守卫会重新捞出
/// 该 venue，封面侧有幂等跳过保护。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemWriter implements ItemWriter<LetPubEnrichResult> {

  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final CasWarningDao casWarningDao;
  private final VenueDao venueDao;

  /// 将 LetPub 富化数据写入数据库。
  @Override
  public void write(Chunk<? extends LetPubEnrichResult> results) throws Exception {
    for (LetPubEnrichResult result : results) {
      List<JcrRatingEntity> jcrRatings = result.jcr().ratings();
      if (!jcrRatings.isEmpty()) {
        List<JcrRatingEntity> newJcrRatings = filterNewJcrRatings(result.venueId(), jcrRatings);
        if (!newJcrRatings.isEmpty()) {
          jcrRatingDao.saveAll(newJcrRatings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 JCR 评级（跳过 {} 条已存在年份）",
              result.venueId(),
              newJcrRatings.size(),
              jcrRatings.size() - newJcrRatings.size());
        }
      }

      List<CasRatingEntity> casRatings = result.cas().ratings();
      if (!casRatings.isEmpty()) {
        List<CasRatingEntity> newCasRatings = filterNewCasRatings(result.venueId(), casRatings);
        if (!newCasRatings.isEmpty()) {
          casRatingDao.saveAll(newCasRatings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 CAS 评级（跳过 {} 条已存在版本）",
              result.venueId(),
              newCasRatings.size(),
              casRatings.size() - newCasRatings.size());
        }
      }

      List<CasWarningEntity> casWarnings = result.cas().warnings();
      if (!casWarnings.isEmpty()) {
        List<CasWarningEntity> newCasWarnings = filterNewCasWarnings(result.venueId(), casWarnings);
        if (!newCasWarnings.isEmpty()) {
          casWarningDao.saveAll(newCasWarnings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 CAS 预警（跳过 {} 条已存在版本）",
              result.venueId(),
              newCasWarnings.size(),
              casWarnings.size() - newCasWarnings.size());
        }
      }

      // 仅在非 null 时 UPDATE：避免把已有对象键清空。
      // 当前无显式清空需求；若未来需要清空，应在 LetPubEnrichResult
      // 增加独立的 clearImageObjectKey 标志位，而非用 null 同时表达
      // "未下载"和"主动清空"两种意图。
      if (result.imageObjectKey() != null) {
        int updated = venueDao.updateImageObjectKey(result.venueId(), result.imageObjectKey());
        if (updated == 0) {
          log.warn("Venue [id={}] 封面对象键 UPDATE 影响 0 行（可能已被删除或不存在）", result.venueId());
        } else {
          log.debug("Venue [id={}] 已更新封面对象键: {}", result.venueId(), result.imageObjectKey());
        }
      }
    }
  }

  /// 过滤掉数据库中已存在的年份，只返回新年份的 JCR 评级。
  private List<JcrRatingEntity> filterNewJcrRatings(
      Long venueId, List<JcrRatingEntity> jcrRatings) {
    Set<Short> existingYears = jcrRatingDao.findYearsByVenueId(venueId);

    if (existingYears.isEmpty()) {
      return jcrRatings;
    }

    return jcrRatings.stream().filter(r -> !existingYears.contains(r.getYear())).toList();
  }

  /// 过滤掉数据库中已存在的 `(年份, 版本)` 组合，只返回新版本的 CAS 评级。
  ///
  /// 通过投影查询仅载入 `(year:edition)` 键集合，避免拉取完整 CAS 实体字段。
  private List<CasRatingEntity> filterNewCasRatings(
      Long venueId, List<CasRatingEntity> casRatings) {
    Set<String> existingKeys = casRatingDao.findKeysByVenueId(venueId);
    if (existingKeys.isEmpty()) {
      return casRatings;
    }

    return casRatings.stream()
        .filter(r -> !existingKeys.contains(r.getYear() + ":" + r.getEdition()))
        .toList();
  }

  /// 过滤掉数据库中已存在的 `(发布年份, 版本标签)` 组合，只返回新的 CAS 预警记录。
  ///
  /// 通过投影查询仅载入 `(publishedYear:editionLabel)` 键集合，
  /// 避免拉取完整预警实体字段。
  private List<CasWarningEntity> filterNewCasWarnings(
      Long venueId, List<CasWarningEntity> casWarnings) {
    Set<String> existingKeys = casWarningDao.findKeysByVenueId(venueId);
    if (existingKeys.isEmpty()) {
      return casWarnings;
    }

    return casWarnings.stream()
        .filter(w -> !existingKeys.contains(w.getPublishedYear() + ":" + w.getEditionLabel()))
        .toList();
  }
}
