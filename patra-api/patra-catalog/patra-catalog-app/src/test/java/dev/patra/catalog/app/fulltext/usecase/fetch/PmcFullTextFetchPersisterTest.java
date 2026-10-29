package dev.patra.catalog.app.fulltext.usecase.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.fulltext.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleFile;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.fulltext.policy.FullTextRetrySchedule;
import dev.patra.catalog.domain.fulltext.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/// `PmcFullTextFetchPersister` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("PmcFullTextFetchPersister")
class PmcFullTextFetchPersisterTest {

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");

  @Mock private PublicationFullTextRepository fullTextRepository;
  @Mock private ApplicationEventPublisher events;
  @Captor private ArgumentCaptor<PublicationFullText> savedCaptor;

  private PmcFullTextFetchPersister persister;

  @BeforeEach
  void setUp() {
    persister =
        new PmcFullTextFetchPersister(
            fullTextRepository,
            events,
            new FullTextRetrySchedule(Clock.fixed(NOW, ZoneOffset.UTC)));
  }

  @Test
  @DisplayName("抓取成功时次数清零、写入文件信息并发布全文已抓取事件")
  void should_reset_attempts_and_mark_pmc_open_access_when_fetched() {
    PmcArticleMetadata selected =
        PmcArticleMetadata.builder()
            .pmcid("PMC1")
            .versionPrefix("PMC1.2")
            .openAccess(true)
            .manuscript(true)
            .licenseCode("CC BY")
            .xml(PmcArticleFile.of("PMC1.2.xml", "xmlmd5"))
            .pdf(PmcArticleFile.of("PMC1.2.pdf", "pdfmd5"))
            .build();
    List<FullTextMedia> media = List.of(FullTextMedia.of("g1.webp", 10, 20));

    persister.recordFetched(candidate(FullTextStatus.FETCH_FAILED, 3), selected, 1024L, media, NOW);

    verify(fullTextRepository).save(savedCaptor.capture());
    PublicationFullText saved = savedCaptor.getValue();
    assertThat(saved.status()).isEqualTo(FullTextStatus.FETCHED);
    assertThat(saved.attemptCount()).isZero();
    assertThat(saved.nextAttemptAt()).isNull();
    assertThat(saved.lastError()).isNull();
    assertThat(saved.pmcVersion()).isEqualTo("PMC1.2");
    assertThat(saved.objectPrefix()).isEqualTo("pmc/PMC1/PMC1.2/");
    assertThat(saved.xmlMd5()).isEqualTo("xmlmd5");
    assertThat(saved.pdfObjectKey()).isEqualTo("pmc/PMC1/PMC1.2/PMC1.2.pdf");
    assertThat(saved.pdfSize()).isEqualTo(1024L);
    assertThat(saved.pdfMd5()).isEqualTo("pdfmd5");
    assertThat(saved.media()).isEqualTo(media);
    assertThat(saved.fetchedAt()).isEqualTo(NOW);
    verify(events).publishEvent(new PmcFullTextFetchedEvent(7L, "PMC1", "CC BY", true));
  }

  @Test
  @DisplayName("同一状态连续出现时次数累加并据此算再查时间")
  void should_increment_attempts_when_same_status_repeats() {
    persister.recordUnavailable(
        candidate(FullTextStatus.UNAVAILABLE, 2), FullTextUnavailableReason.NOT_IN_BUCKET);

    verify(fullTextRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().attemptCount()).isEqualTo(3);
    assertThat(savedCaptor.getValue().nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("换了状态时次数从 1 起算")
  void should_restart_attempts_from_one_when_status_changes() {
    persister.recordFailure(
        candidate(FullTextStatus.UNAVAILABLE, 4), new IllegalStateException("boom"));

    verify(fullTextRepository).save(savedCaptor.capture());
    PublicationFullText saved = savedCaptor.getValue();
    assertThat(saved.status()).isEqualTo(FullTextStatus.FETCH_FAILED);
    assertThat(saved.attemptCount()).isEqualTo(1);
    assertThat(saved.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    assertThat(saved.lastError()).isEqualTo("IllegalStateException: boom");
  }

  @Test
  @DisplayName("撤稿不再查，next_attempt_at 为空")
  void should_leave_next_attempt_null_when_retracted() {
    persister.recordUnavailable(candidate(null, 0), FullTextUnavailableReason.RETRACTED);

    verify(fullTextRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().unavailableReason())
        .isEqualTo(FullTextUnavailableReason.RETRACTED);
    assertThat(savedCaptor.getValue().nextAttemptAt()).isNull();
  }

  @Test
  @DisplayName("失败原因截断到 2000 字符")
  void should_truncate_last_error_to_2000() {
    persister.recordFailure(candidate(null, 0), new IllegalStateException("x".repeat(3000)));

    verify(fullTextRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().lastError()).hasSize(2000);
  }

  @Test
  @DisplayName("已有可读全文的记录重抓失败时保留状态与内容，只记错误与下次时间")
  void should_keep_content_and_status_when_refetch_of_parsed_record_fails() {
    PublicationFullText parsed =
        PublicationFullText.builder()
            .publicationId(7L)
            .pmcid("PMC1")
            .pmcVersion("PMC1.2")
            .status(FullTextStatus.PARSED)
            .objectPrefix("pmc/PMC1/PMC1.2/")
            .pdfObjectKey("pmc/PMC1/PMC1.2/PMC1.2.pdf")
            .parserVersion(1)
            .wordCount(500)
            .build();
    when(fullTextRepository.findByPublicationId(7L)).thenReturn(Optional.of(parsed));

    persister.recordFailure(candidate(FullTextStatus.PARSED, 0), new IllegalStateException("503"));

    verify(fullTextRepository).save(savedCaptor.capture());
    PublicationFullText saved = savedCaptor.getValue();
    assertThat(saved.status()).isEqualTo(FullTextStatus.PARSED);
    assertThat(saved.objectPrefix()).isEqualTo("pmc/PMC1/PMC1.2/");
    assertThat(saved.pdfObjectKey()).isEqualTo("pmc/PMC1/PMC1.2/PMC1.2.pdf");
    assertThat(saved.wordCount()).isEqualTo(500);
    assertThat(saved.attemptCount()).isEqualTo(1);
    assertThat(saved.lastError()).isEqualTo("IllegalStateException: 503");
    assertThat(saved.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
  }

  @Test
  @DisplayName("变成不可用时 has_full_text 置回 false")
  void should_clear_has_full_text_when_becoming_unavailable() {
    persister.recordUnavailable(
        candidate(FullTextStatus.PARSED, 0), FullTextUnavailableReason.NOT_OPEN_ACCESS);

    verify(events, never()).publishEvent(any(Object.class));
    verify(fullTextRepository).setHasFullText(7L, false);
  }

  private static FetchCandidate candidate(FullTextStatus status, int attempts) {
    return FetchCandidate.builder()
        .publicationId(7L)
        .pmcid("PMC1")
        .currentStatus(status)
        .attemptCount(attempts)
        .build();
  }
}
