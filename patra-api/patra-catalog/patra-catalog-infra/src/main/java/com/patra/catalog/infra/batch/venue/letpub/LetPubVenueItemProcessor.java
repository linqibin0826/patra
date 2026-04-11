package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// LetPub 期刊富化 Processor。
///
/// **职责**：
///
/// 1. 按 ISSN-L 从 LetPub 查询详情页数据
/// 2. 通过 `LetPubDataMapper` 拆解为 JCR + CAS 评级实体
/// 3. 若尚未存储封面且 LetPub 返回了封面 URL，则通过
///    {@link VenueCoverImageDownloadPort} 下载并上传到对象存储
///
/// **失败隔离**：
///
/// 封面下载失败（`FileDownloadException`）**不阻断**主流程，
/// 仅记录 WARN 日志，`LetPubEnrichResult.imageObjectKey` 保持 null，
/// 下一轮批次命中同一 Venue 时可再次尝试。
///
/// **幂等**：
///
/// 若 `VenueEntity.imageObjectKey` 已非空，则跳过下载（不重复上传）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemProcessor implements ItemProcessor<VenueEntity, LetPubEnrichResult> {

  private final LetPubEnrichmentPort enrichmentPort;
  private final LetPubDataMapper dataMapper;
  private final VenueCoverImageDownloadPort coverImageDownloadPort;

  /// 处理单个 VenueEntity，通过 ISSN-L 查询 LetPub 数据并拆解为评级行。
  @Override
  public LetPubEnrichResult process(VenueEntity item) throws Exception {
    String issnL = item.getIssnL();
    if (issnL == null || issnL.isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 LetPub 富化", item.getId());
      return null;
    }

    Optional<LetPubVenueData> result = enrichmentPort.findByIssn(issnL);
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 LetPub 找到数据", item.getId(), issnL);
      return null;
    }

    LetPubVenueData data = result.get();
    Long venueId = item.getId();
    String sourceUrl = buildSourceUrl(data.letPubJournalId());

    List<JcrRatingEntity> jcrRatings = dataMapper.mapToJcrRatings(data, venueId, sourceUrl);
    List<CasRatingEntity> casRatings = dataMapper.mapToCasRatings(data, venueId, sourceUrl);

    String imageObjectKey = downloadCoverIfNeeded(item, data);

    int totalCount = jcrRatings.size() + casRatings.size();
    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功，生成 {} 条评级（JCR:{}, CAS:{}）",
        venueId,
        issnL,
        totalCount,
        jcrRatings.size(),
        casRatings.size());

    return LetPubEnrichResult.of(venueId, imageObjectKey, jcrRatings, casRatings);
  }

  /// 按需下载封面到对象存储。
  ///
  /// **跳过条件**：
  ///
  /// - 已存在 `imageObjectKey`（幂等，不重复下载）
  /// - LetPub 未返回封面 URL（数据缺失）
  ///
  /// **失败处理**：
  ///
  /// - `FileDownloadException` → WARN 日志，返回 null，不阻断主流程
  ///
  /// @return 新下载的对象键；若跳过或失败返回 null
  private String downloadCoverIfNeeded(VenueEntity item, LetPubVenueData data) {
    if (item.getImageObjectKey() != null) {
      return null;
    }
    if (data.coverImageSourceUrl() == null) {
      return null;
    }
    String stableKey = "catalog/venue-cover/" + item.getId() + ".jpg";
    try {
      URI sourceUri = URI.create(data.coverImageSourceUrl());
      return coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    } catch (FileDownloadException e) {
      log.warn(
          "venue 封面下载失败（主流程继续）: venueId={} sourceUrl={} trait={} reason={}",
          item.getId(),
          data.coverImageSourceUrl(),
          e.getErrorTraits(),
          e.getMessage());
      return null;
    }
  }

  /// 构建 LetPub 详情页 URL，用于数据溯源。
  private String buildSourceUrl(String journalId) {
    if (journalId == null || journalId.isBlank()) {
      return null;
    }
    return "https://www.letpub.com.cn/index.php?journalid="
        + journalId
        + "&page=journalapp&view=detail";
  }
}
