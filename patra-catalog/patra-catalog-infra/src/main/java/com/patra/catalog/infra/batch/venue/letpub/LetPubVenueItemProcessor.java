package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
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
    List<CasWarningEntity> casWarnings = dataMapper.mapToCasWarnings(data, venueId, sourceUrl);

    String imageObjectKey = downloadCoverIfNeeded(item, data);

    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功，生成 JCR:{} CAS 评级:{} CAS 预警:{}",
        venueId,
        issnL,
        jcrRatings.size(),
        casRatings.size(),
        casWarnings.size());

    return LetPubEnrichResult.of(
        venueId,
        imageObjectKey,
        LetPubEnrichResult.JcrBatch.of(jcrRatings),
        LetPubEnrichResult.CasBatch.of(casRatings, casWarnings));
  }

  /// 按需下载封面到对象存储。
  ///
  /// **跳过条件**：
  ///
  /// - 已存在 `imageObjectKey`（幂等，不重复下载）
  /// - LetPub 未返回封面 URL，或 URL 为空白字符串（数据缺失）
  ///
  /// **失败处理**（均不阻断主流程，对齐"封面下载是可选增强"契约）：
  ///
  /// - `IllegalArgumentException`（URL 格式非法）→ WARN 日志，返回 null
  /// - `FileDownloadException`（Adapter 契约内异常）→ WARN 日志 + trait，返回 null
  /// - 其它 `RuntimeException`（Adapter 契约外异常，例如对象存储客户端
  ///   未初始化、传输层 NPE 等）→ WARN 日志 + 完整堆栈，返回 null。
  ///   **不兜底会触发 Spring Batch chunk 回滚，连带丢弃同一 venue 的
  ///   JCR/CAS ratings**。
  ///
  /// @return 新下载的对象键；若跳过或失败返回 null
  private String downloadCoverIfNeeded(VenueEntity item, LetPubVenueData data) {
    if (item.getImageObjectKey() != null) {
      log.debug("Venue [id={}] 已存在封面对象键，跳过下载（幂等）", item.getId());
      return null;
    }
    String sourceUrl = data.coverImageSourceUrl();
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return null;
    }
    String stableKey = buildCoverObjectKey(item.getId());
    URI sourceUri;
    try {
      sourceUri = URI.create(sourceUrl);
    } catch (IllegalArgumentException e) {
      log.warn("venue 封面 URL 格式非法（主流程继续）: venueId={} sourceUrl={}", item.getId(), sourceUrl);
      return null;
    }
    try {
      return coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    } catch (FileDownloadException e) {
      log.warn(
          "venue 封面下载失败（主流程继续）: venueId={} sourceUrl={} trait={} reason={}",
          item.getId(),
          sourceUrl,
          e.getErrorTraits(),
          e.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("venue 封面下载意外异常（主流程继续）: venueId={} sourceUrl={}", item.getId(), sourceUrl, e);
      return null;
    }
  }

  /// 构建封面对象键：`catalog/venue-cover/{venueId}.jpg`。
  ///
  /// 稳定键（key by venueId）同一 venue 每次都解析为相同的对象路径，
  /// 天然支持"覆盖同一张图"和"幂等跳过"两种语义。
  private static String buildCoverObjectKey(Long venueId) {
    return "catalog/venue-cover/" + venueId + ".jpg";
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
