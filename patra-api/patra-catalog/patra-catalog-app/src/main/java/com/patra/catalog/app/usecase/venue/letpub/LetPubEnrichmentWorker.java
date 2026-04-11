package com.patra.catalog.app.usecase.venue.letpub;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import com.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 处理单个 venue 的 LetPub 富化事务单元。
///
/// **为什么与 [LetPubEnrichmentRunner] 分离**：
///
/// Spring AOP 自调用不触发代理——`Runner.run()` 内直接 `this.processVenue()`
/// 不会激活 `@Transactional`。拆成两个 Spring bean 通过依赖注入跨类调用，
/// 才能让每个 venue 真正运行在独立事务里。
///
/// **返回值语义**：三种 [Outcome] 用于 Runner 统计，不是失败标志。
/// 任何 `Exception` 都会向上传播，由 Runner 的 try/catch 计入 `failed`。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class LetPubEnrichmentWorker {

  private final LetPubEnrichmentPort scraperPort;
  private final LetPubEnrichmentPersistPort persistPort;
  private final VenueCoverImageDownloadPort coverImageDownloadPort;

  /// 单 venue 处理结果的三种正常分支。
  public enum Outcome {
    /// 成功完成持久化（至少调用一次 persistPort.persist）
    PROCESSED,
    /// venue 在 LetPub 未检索到数据（scraperPort 返回 empty）
    NOT_FOUND_IN_SOURCE,
    /// venue 的 ISSN-L 为空或空白，无法查询
    MISSING_ISSN
  }

  /// 处理单个 venue 的 LetPub 富化。
  ///
  /// 事务边界：每次调用独占一个 REQUIRES_NEW 事务，`Exception` 触发回滚。
  ///
  /// 流程：检查 ISSN → 爬取 → 按需下载封面 → 调 persistPort 持久化。
  ///
  /// @param venue 待处理 venue 快照，`id` 不为 null；`issnL` 允许为 null（返回 MISSING_ISSN）
  /// @return 三种 [Outcome] 之一：PROCESSED / NOT_FOUND_IN_SOURCE / MISSING_ISSN
  /// @throws RuntimeException 任何下游 port 抛出的运行时异常都会向上传播
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public Outcome processVenue(VenueSnapshot venue) {
    if (venue.issnL() == null || venue.issnL().isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 LetPub 富化", venue.id());
      return Outcome.MISSING_ISSN;
    }

    Optional<LetPubVenueData> result = scraperPort.findByIssn(venue.issnL());
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 LetPub 找到数据", venue.id(), venue.issnL());
      return Outcome.NOT_FOUND_IN_SOURCE;
    }

    LetPubVenueData data = result.get();
    String coverObjectKey = downloadCoverIfNeeded(venue, data);
    LetPubEnrichmentPersistPort.PersistStats stats =
        persistPort.persist(venue.id(), data, coverObjectKey);

    log.info(
        "Venue [id={}, issn={}] LetPub 富化成功 JCR={} CAS={} Warning={} cover={}",
        venue.id(),
        venue.issnL(),
        stats.jcrInserted(),
        stats.casInserted(),
        stats.warningInserted(),
        stats.coverUpdated());
    return Outcome.PROCESSED;
  }

  /// 按需下载封面图到对象存储，失败时宽容处理不影响主流程。
  ///
  /// 跳过条件：venue 已存在封面键 / LetPub 未返回 URL / URL 格式非法 / 下载时抛异常。
  /// "已存在封面键"的判断直接读 snapshot 里的 projection 字段，避免额外 PK 查询。
  ///
  /// @param venue 待处理 venue 快照，`existingCoverKey` 非 null 时跳过下载
  /// @param data LetPub 爬取数据，从中提取 coverImageSourceUrl
  /// @return 新下载的对象键；任何跳过或失败路径返回 null（调用方传 null 给 persistPort）
  private String downloadCoverIfNeeded(VenueSnapshot venue, LetPubVenueData data) {
    if (venue.existingCoverKey() != null) {
      log.debug("Venue [id={}] 已存在封面对象键，跳过下载", venue.id());
      return null;
    }
    String sourceUrl = data.basicInfo().coverImageSourceUrl();
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return null;
    }

    String stableKey = "catalog/venue-cover/" + venue.id() + ".jpg";
    URI sourceUri;
    try {
      sourceUri = URI.create(sourceUrl);
    } catch (IllegalArgumentException e) {
      log.warn("venue 封面 URL 格式非法（继续）: venueId={} sourceUrl={}", venue.id(), sourceUrl);
      return null;
    }
    try {
      return coverImageDownloadPort.downloadAndStore(sourceUri, stableKey);
    } catch (FileDownloadException e) {
      log.warn(
          "venue 封面下载失败（继续）: venueId={} trait={} reason={}",
          venue.id(),
          e.getErrorTraits(),
          e.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("venue 封面下载意外异常（继续）: venueId={}", venue.id(), e);
      return null;
    }
  }
}
