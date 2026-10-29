package dev.patra.catalog.app.usecase.fulltext.fetch;

import dev.patra.catalog.domain.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.model.enums.FullTextStatus;
import dev.patra.catalog.domain.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.domain.model.vo.fulltext.FullTextMedia;
import dev.patra.catalog.domain.model.vo.fulltext.PmcArticleMetadata;
import dev.patra.catalog.domain.model.vo.fulltext.PublicationFullText;
import dev.patra.catalog.domain.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.domain.service.fulltext.FullTextRetrySchedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 抓取结果的持久化，每篇一个 `REQUIRES_NEW` 事务（Worker 不开事务）。
///
/// 次数只在同一状态连续出现时累加，换状态从 1 起算，抓取成功清零。
///
/// @author linqibin
/// @since 0.11.0
@Component
@RequiredArgsConstructor
public class PmcFullTextFetchPersister {

  private static final int MAX_ERROR_LENGTH = 2000;

  private final PublicationFullTextRepository fullTextRepository;
  private final ApplicationEventPublisher events;
  private final FullTextRetrySchedule retrySchedule;

  /// 记录抓取成功：全文记录 `FETCHED`，并发布 [PmcFullTextFetchedEvent]，由文献子域在同一事务里
  /// 标为开放获取并替换 PMC 位置。
  ///
  /// @param c 候选
  /// @param selected 选中的版本
  /// @param pdfSize PDF 字节数，没有 PDF 时为 `null`
  /// @param media 已下载的图片
  /// @param fetchedAt 抓取时间
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordFetched(
      FetchCandidate c,
      PmcArticleMetadata selected,
      Long pdfSize,
      List<FullTextMedia> media,
      Instant fetchedAt) {
    String prefix = FullTextObjectStoragePort.objectPrefix(c.pmcid(), selected.versionPrefix());
    fullTextRepository.save(
        PublicationFullText.builder()
            .publicationId(c.publicationId())
            .pmcid(c.pmcid())
            .pmcVersion(selected.versionPrefix())
            .status(FullTextStatus.FETCHED)
            .licenseCode(selected.licenseCode())
            .manuscript(selected.manuscript())
            .objectPrefix(prefix)
            .xmlMd5(selected.xml().md5())
            .pdfObjectKey(selected.pdf() != null ? prefix + selected.pdf().fileName() : null)
            .pdfSize(selected.pdf() != null ? pdfSize : null)
            .pdfMd5(selected.pdf() != null ? selected.pdf().md5() : null)
            .media(media)
            .fetchedAt(fetchedAt)
            .attemptCount(0)
            .build());
    events.publishEvent(
        new PmcFullTextFetchedEvent(
            c.publicationId(), c.pmcid(), selected.licenseCode(), selected.manuscript()));
  }

  /// 记录不可用；可再查的原因按次数或放开日期算下次再查时间，撤稿等终态不再查。
  ///
  /// 不可用意味着不再允许分发：清空已抓取的内容信息，`has_full_text` 置回 false。
  ///
  /// @param c 候选
  /// @param reason 原因
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordUnavailable(FetchCandidate c, FullTextUnavailableReason reason) {
    int attempts = nextAttemptCount(c, FullTextStatus.UNAVAILABLE);
    fullTextRepository.save(
        PublicationFullText.builder()
            .publicationId(c.publicationId())
            .pmcid(c.pmcid())
            .status(FullTextStatus.UNAVAILABLE)
            .unavailableReason(reason)
            .attemptCount(attempts)
            .nextAttemptAt(
                reason.isRecheckable()
                    ? retrySchedule.afterUnavailable(attempts, c.pmcReleaseDate())
                    : null)
            .build());
    fullTextRepository.setHasFullText(c.publicationId(), false);
  }

  /// 记录抓取失败，按次数退避。
  ///
  /// 已有抓取内容的记录（手动重抓、PMCID 变更重抓）失败时保留原状态与内容，只记错误、次数与下次时间——
  /// 一次 5xx 不能让已能阅读的全文和 PDF 消失。没有内容的记录记为 `FETCH_FAILED`。
  ///
  /// @param c 候选
  /// @param error 失败原因
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordFailure(FetchCandidate c, Exception error) {
    String message = truncate(error.getClass().getSimpleName() + ": " + error.getMessage());
    Optional<PublicationFullText> current =
        fullTextRepository.findByPublicationId(c.publicationId());
    if (current.isPresent() && current.get().objectPrefix() != null) {
      int attempts = current.get().lastError() != null ? current.get().attemptCount() + 1 : 1;
      fullTextRepository.save(
          current.get().toBuilder()
              .attemptCount(attempts)
              .lastError(message)
              .nextAttemptAt(retrySchedule.afterFetchFailure(attempts))
              .build());
      return;
    }
    int attempts = nextAttemptCount(c, FullTextStatus.FETCH_FAILED);
    fullTextRepository.save(
        PublicationFullText.builder()
            .publicationId(c.publicationId())
            .pmcid(c.pmcid())
            .status(FullTextStatus.FETCH_FAILED)
            .attemptCount(attempts)
            .lastError(message)
            .nextAttemptAt(retrySchedule.afterFetchFailure(attempts))
            .build());
  }

  /// 同一状态连续出现时累加，换状态从 1 起算。
  private static int nextAttemptCount(FetchCandidate c, FullTextStatus status) {
    return c.currentStatus() == status ? c.attemptCount() + 1 : 1;
  }

  /// 截断到列宽。
  private static String truncate(String value) {
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }
}
