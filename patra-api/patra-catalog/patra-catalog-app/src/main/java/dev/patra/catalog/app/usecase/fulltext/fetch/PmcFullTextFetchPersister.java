package dev.patra.catalog.app.usecase.fulltext.fetch;

import dev.patra.catalog.domain.model.enums.FullTextStatus;
import dev.patra.catalog.domain.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.domain.model.vo.fulltext.FullTextMedia;
import dev.patra.catalog.domain.model.vo.fulltext.PmcArticleMetadata;
import dev.patra.catalog.domain.model.vo.fulltext.PublicationFullText;
import dev.patra.catalog.domain.model.vo.publication.PublicationOaLocation;
import dev.patra.catalog.domain.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.port.repository.PublicationRepository;
import dev.patra.catalog.domain.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.domain.service.fulltext.FullTextRetrySchedule;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
  private final PublicationRepository publicationRepository;
  private final FullTextRetrySchedule retrySchedule;

  /// 记录抓取成功：全文记录 `FETCHED`，文献标为开放获取并替换 PMC 位置。
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
    publicationRepository.markPmcOpenAccess(
        c.publicationId(),
        PublicationOaLocation.ofPmc(c.pmcid(), selected.licenseCode(), selected.manuscript()));
  }

  /// 记录不可用；可再查的原因按次数或放开日期算下次再查时间，撤稿等终态不再查。
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
  }

  /// 记录抓取失败，按次数退避。
  ///
  /// @param c 候选
  /// @param error 失败原因
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordFailure(FetchCandidate c, Exception error) {
    int attempts = nextAttemptCount(c, FullTextStatus.FETCH_FAILED);
    fullTextRepository.save(
        PublicationFullText.builder()
            .publicationId(c.publicationId())
            .pmcid(c.pmcid())
            .status(FullTextStatus.FETCH_FAILED)
            .attemptCount(attempts)
            .lastError(truncate(error.getClass().getSimpleName() + ": " + error.getMessage()))
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
