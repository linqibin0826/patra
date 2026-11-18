package dev.patra.catalog.app.fulltext.usecase.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.port.parser.JatsBodyParserPort;
import dev.patra.catalog.domain.fulltext.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// `JatsFullTextParsePersister` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("JatsFullTextParsePersister")
class JatsFullTextParsePersisterTest {

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");
  private static final ParseCandidate CANDIDATE =
      ParseCandidate.builder()
          .publicationId(7L)
          .pmcid("PMC1")
          .pmcVersion("PMC1.2")
          .objectPrefix("p/")
          .build();
  private static final PublicationFullText FETCHED =
      PublicationFullText.builder()
          .publicationId(7L)
          .pmcid("PMC1")
          .pmcVersion("PMC1.2")
          .objectPrefix("p/")
          .status(FullTextStatus.FETCHED)
          .pdfObjectKey("p/PMC1.2.pdf")
          .build();
  private static final PublicationFullText REFETCHED =
      FETCHED.toBuilder().pmcVersion("PMC1.3").pdfObjectKey("p/PMC1.3.pdf").build();

  @Mock private PublicationFullTextRepository repository;
  @InjectMocks private JatsFullTextParsePersister persister;
  @Captor private ArgumentCaptor<PublicationFullText> captor;

  private void givenLocked(PublicationFullText current) {
    when(repository.lockByPublicationId(7L)).thenReturn(Optional.of(current));
  }

  @Test
  @DisplayName("解析成功：写正文、记 PARSED 与字数，保留抓取字段，has_full_text 置 true")
  void should_record_parsed() {
    givenLocked(FETCHED);
    FullTextBody body = FullTextBody.builder().schemaVersion(1).wordCount(42).build();

    assertThat(persister.recordParsed(CANDIDATE, body, NOW)).isTrue();

    InOrder order = inOrder(repository);
    order.verify(repository).lockByPublicationId(7L);
    order.verify(repository).saveBody(7L, body, JatsBodyParserPort.PARSER_VERSION);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(FullTextStatus.PARSED);
    assertThat(captor.getValue().wordCount()).isEqualTo(42);
    assertThat(captor.getValue().parserVersion()).isEqualTo(JatsBodyParserPort.PARSER_VERSION);
    assertThat(captor.getValue().parsedAt()).isEqualTo(NOW);
    assertThat(captor.getValue().pdfObjectKey()).isEqualTo("p/PMC1.2.pdf");
    verify(repository).setHasFullText(7L, true);
  }

  @Test
  @DisplayName("无正文：记 UNAVAILABLE / NO_BODY 并写解析器版本，has_full_text 置 false")
  void should_record_no_body() {
    givenLocked(FETCHED);

    assertThat(persister.recordNoBody(CANDIDATE, NOW)).isTrue();

    verify(repository).save(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(FullTextStatus.UNAVAILABLE);
    assertThat(captor.getValue().unavailableReason()).isEqualTo(FullTextUnavailableReason.NO_BODY);
    assertThat(captor.getValue().parserVersion()).isEqualTo(JatsBodyParserPort.PARSER_VERSION);
    verify(repository).setHasFullText(7L, false);
  }

  @Test
  @DisplayName("解析失败：记 PARSE_FAILED、原因与解析器版本")
  void should_record_parse_failure() {
    givenLocked(FETCHED);

    assertThat(
            persister.recordParseFailure(
                CANDIDATE, new IllegalStateException("x".repeat(3000)), NOW))
        .isTrue();

    verify(repository).save(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(FullTextStatus.PARSE_FAILED);
    assertThat(captor.getValue().lastError()).hasSize(2000);
    assertThat(captor.getValue().parserVersion()).isEqualTo(JatsBodyParserPort.PARSER_VERSION);
    verify(repository).setHasFullText(7L, false);
  }

  /// 解析期间抓取任务已重抓到新版本：锁住行后发现不是当初那份 XML，旧解析结果整篇丢弃，
  /// 正文、状态、`has_full_text` 都不写，新内容留给下一轮解析。
  @Test
  @DisplayName("解析期间已重抓：丢弃解析结果，什么都不写")
  void should_discard_parse_result_when_refetched_meanwhile() {
    givenLocked(REFETCHED);
    FullTextBody body = FullTextBody.builder().schemaVersion(1).wordCount(42).build();

    assertThat(persister.recordParsed(CANDIDATE, body, NOW)).isFalse();
    assertThat(persister.recordNoBody(CANDIDATE, NOW)).isFalse();

    verify(repository, never()).saveBody(anyLong(), any(), anyInt());
    verify(repository, never()).save(any());
    verify(repository, never()).setHasFullText(anyLong(), anyBoolean());
  }

  /// 旧 XML 上的解析失败同样不能把新抓到的记录打成 `PARSE_FAILED`。
  @Test
  @DisplayName("解析期间已重抓：解析失败也不写")
  void should_discard_parse_failure_when_refetched_meanwhile() {
    givenLocked(REFETCHED);

    assertThat(persister.recordParseFailure(CANDIDATE, new IllegalStateException("x"), NOW))
        .isFalse();

    verify(repository, never()).save(any());
    verify(repository, never()).setHasFullText(anyLong(), anyBoolean());
  }
}
