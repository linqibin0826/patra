package dev.linqibin.patra.catalog.app.usecase.venue.letpub;

import dev.linqibin.patra.catalog.domain.exception.FileDownloadException;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPersistPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubEnrichmentPort;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import dev.linqibin.patra.catalog.domain.port.enrichment.VenueSnapshot;
import dev.linqibin.patra.catalog.domain.port.storage.VenueCoverImageDownloadPort;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 处理单个 venue 的 LetPub 富化编排单元（非事务）。
///
/// **架构分层**：
///
/// - **本类（Worker）**：非事务，负责编排顺序——检查 ISSN → HTTP 爬取 → 按需
///   下载封面 → 委托 [LetPubEnrichmentPersister] 持久化。HTTP 调用和
///   `Thread.sleep` backoff 发生在事务外，不占用 DB 连接。
/// - **[LetPubEnrichmentPersister]**：事务门面，`@Transactional(REQUIRES_NEW)`
///   包一层 `PersistPort.persist` 调用，把 DB 连接占用收缩到最紧凑的 DB-only 窗口。
/// - **[LetPubEnrichmentRunner]**：外循环协调器，keyset pagination 喂 venue。
///
/// **为什么与 [LetPubEnrichmentRunner] 是两个 bean**：Spring AOP 自调用不触发代理，
/// 而 [LetPubEnrichmentPersister] 需要通过跨 bean 调用才能激活 `@Transactional`。
/// Runner → Worker → Persister 是三层独立 bean，AOP 代理在每一跳都生效。
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
  private final LetPubEnrichmentPersister persister;
  private final VenueCoverImageDownloadPort coverImageDownloadPort;

  /// 单 venue 处理结果的三种正常分支。
  public enum Outcome {
    /// 成功完成持久化（至少调用一次 persister.persist）
    PROCESSED,
    /// venue 在 LetPub 未检索到数据（scraperPort 返回 empty）
    NOT_FOUND_IN_SOURCE,
    /// venue 的 ISSN-L 为空或空白，无法查询
    MISSING_ISSN
  }

  /// 处理单个 venue 的 LetPub 富化编排。
  ///
  /// **本方法本身不是事务**——事务边界在 [LetPubEnrichmentPersister#persist] 里。
  /// HTTP 爬取和封面下载在事务外执行，只有最后一步 `persister.persist(...)` 会
  /// 进入独立 `REQUIRES_NEW` 事务。
  ///
  /// 流程：检查 ISSN → 爬取 → 按需下载封面 → 调 Persister 持久化。
  ///
  /// @param venue 待处理 venue 快照，`id` 不为 null；`issnL` 允许为 null（返回 MISSING_ISSN）
  /// @return 三种 [Outcome] 之一：PROCESSED / NOT_FOUND_IN_SOURCE / MISSING_ISSN
  /// @throws RuntimeException 任何下游 port 抛出的运行时异常都会向上传播
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
        persister.persist(venue.id(), data, coverObjectKey);

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
  /// 跳过条件：venue 已有封面键（读 `venue.existingCoverKey()` 得到非 null）/
  /// LetPub 未返回 URL / URL 格式非法 / 下载时抛异常。
  ///
  /// @param venue 目标 venue 快照
  /// @param data LetPub 爬取数据，从中提取 coverImageSourceUrl
  /// @return 新下载的对象键；任何跳过或失败路径返回 null（调用方传 null 给 persister）
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
