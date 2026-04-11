package com.patra.catalog.app.usecase.venue.scopus;

import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPersistPort;
import com.patra.catalog.domain.port.enrichment.ScopusEnrichmentPort;
import com.patra.catalog.domain.port.enrichment.ScopusVenueData;
import com.patra.catalog.domain.port.enrichment.VenueSnapshot;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 处理单个 venue 的 Scopus 富化事务单元。
///
/// 与 LetPub 版本相比更简单：Scopus API 不提供期刊封面，故无封面下载逻辑，
/// 依赖集减少为 [ScopusEnrichmentPort] 和 [ScopusEnrichmentPersistPort] 两个。
///
/// **为什么与 `ScopusEnrichmentRunner` 分离**：
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
public class ScopusEnrichmentWorker {

  private final ScopusEnrichmentPort scraperPort;
  private final ScopusEnrichmentPersistPort persistPort;

  /// 单 venue 处理结果的三种正常分支。
  public enum Outcome {
    /// 成功完成持久化（至少调用一次 persistPort.persist）
    PROCESSED,
    /// venue 在 Scopus 未检索到数据（scraperPort 返回 empty）
    NOT_FOUND_IN_SOURCE,
    /// venue 的 ISSN-L 为空或空白，无法查询
    MISSING_ISSN
  }

  /// 处理单个 venue 的 Scopus 富化。
  ///
  /// 事务边界：每次调用独占一个 REQUIRES_NEW 事务，`Exception` 触发回滚。
  ///
  /// 流程：检查 ISSN → 爬取 → 调 persistPort 持久化。无封面下载路径。
  ///
  /// @param venue 待处理 venue 快照，`id` 不为 null；`issnL` 允许为 null（返回 MISSING_ISSN）
  /// @return 三种 [Outcome] 之一：PROCESSED / NOT_FOUND_IN_SOURCE / MISSING_ISSN
  /// @throws RuntimeException 任何下游 port 抛出的运行时异常都会向上传播
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public Outcome processVenue(VenueSnapshot venue) {
    if (venue.issnL() == null || venue.issnL().isBlank()) {
      log.debug("Venue [id={}] ISSN-L 为空，跳过 Scopus 富化", venue.id());
      return Outcome.MISSING_ISSN;
    }

    Optional<ScopusVenueData> result = scraperPort.findByIssn(venue.issnL());
    if (result.isEmpty()) {
      log.debug("Venue [id={}, issn={}] 未在 Scopus 找到数据", venue.id(), venue.issnL());
      return Outcome.NOT_FOUND_IN_SOURCE;
    }

    ScopusVenueData data = result.get();
    ScopusEnrichmentPersistPort.PersistStats stats = persistPort.persist(venue.id(), data);

    log.info(
        "Venue [id={}, issn={}] Scopus 富化成功 ratingsInserted={}",
        venue.id(),
        venue.issnL(),
        stats.scopusRatingsInserted());
    return Outcome.PROCESSED;
  }
}
