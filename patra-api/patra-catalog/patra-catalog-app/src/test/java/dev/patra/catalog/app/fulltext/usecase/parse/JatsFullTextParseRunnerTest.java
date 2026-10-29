package dev.patra.catalog.app.fulltext.usecase.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.app.fulltext.config.FullTextJobProperties;
import dev.patra.catalog.domain.fulltext.exception.FullTextFileNotFoundException;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.JatsParseResult;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFileInfo;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.port.parser.JatsBodyParserPort;
import dev.patra.catalog.domain.fulltext.port.read.FullTextCandidateReadPort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// `JatsFullTextParseRunner` 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("JatsFullTextParseRunner")
class JatsFullTextParseRunnerTest {

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");
  private static final List<FullTextMedia> MEDIA = List.of(FullTextMedia.of("g1.webp", 3, 2));
  private static final ParseCandidate CANDIDATE =
      ParseCandidate.builder()
          .publicationId(7L)
          .pmcid("PMC1")
          .pmcVersion("PMC1.2")
          .objectPrefix("pmc/PMC1/PMC1.2/")
          .media(MEDIA)
          .build();

  @Mock private FullTextCandidateReadPort candidatePort;
  @Mock private FullTextObjectStoragePort storage;
  @Mock private JatsBodyParserPort parser;
  @Mock private JatsFullTextParsePersister persister;

  private JatsFullTextParseRunner runner;

  @BeforeEach
  void setUp() {
    FullTextJobProperties properties =
        new FullTextJobProperties(
            new FullTextJobProperties.Fetch(200, 4, 8), new FullTextJobProperties.Parse(10));
    runner =
        new JatsFullTextParseRunner(
            candidatePort,
            storage,
            parser,
            persister,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(candidatePort.findParseCandidates(JatsBodyParserPort.PARSER_VERSION, 0L, 10))
        .thenReturn(List.of(CANDIDATE));
    when(candidatePort.findParseCandidates(JatsBodyParserPort.PARSER_VERSION, 7L, 10))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName("解析成功时交 Persister 记 PARSED，按清单匹配图片")
  void should_mark_parsed_and_set_has_full_text() {
    FullTextBody body = FullTextBody.builder().schemaVersion(1).wordCount(5).build();
    when(storage.open("pmc/PMC1/PMC1.2/PMC1.2.xml")).thenReturn(file());
    when(parser.parse(any(), eq(MEDIA))).thenReturn(JatsParseResult.ofBody(body, 0));

    assertThat(runner.run()).isEqualTo(FullTextParseRunStats.of(1, 1, 0, 0));

    verify(persister).recordParsed(CANDIDATE, body, NOW);
  }

  @Test
  @DisplayName("无正文时记 NO_BODY")
  void should_mark_no_body_with_parser_version() {
    when(storage.open("pmc/PMC1/PMC1.2/PMC1.2.xml")).thenReturn(file());
    when(parser.parse(any(), eq(MEDIA))).thenReturn(JatsParseResult.ofNoBody());

    assertThat(runner.run()).isEqualTo(FullTextParseRunStats.of(1, 0, 1, 0));

    verify(persister).recordNoBody(CANDIDATE, NOW);
  }

  @Test
  @DisplayName("XML 不合法时记 PARSE_FAILED")
  void should_mark_parse_failed_with_parser_version() {
    when(storage.open("pmc/PMC1/PMC1.2/PMC1.2.xml")).thenReturn(file());
    XmlParseException error = new XmlParseException("bad");
    when(parser.parse(any(), eq(MEDIA))).thenThrow(error);

    assertThat(runner.run()).isEqualTo(FullTextParseRunStats.of(1, 0, 0, 1));

    verify(persister).recordParseFailure(CANDIDATE, error, NOW);
  }

  @Test
  @DisplayName("读存储出错时不写记录、保持 FETCHED，计失败后继续")
  void should_keep_fetched_when_storage_fails() {
    when(storage.open("pmc/PMC1/PMC1.2/PMC1.2.xml"))
        .thenThrow(new FullTextFileNotFoundException("k", null));

    assertThat(runner.run()).isEqualTo(FullTextParseRunStats.of(1, 0, 0, 1));

    verifyNoInteractions(persister);
    verify(parser, never()).parse(any(), any());
  }

  @Test
  @DisplayName("解析中途读流出错（异常原因是 IOException）时按读存储失败处理，不记 PARSE_FAILED")
  void should_keep_fetched_when_stream_breaks_during_parse() {
    when(storage.open("pmc/PMC1/PMC1.2/PMC1.2.xml")).thenReturn(file());
    when(parser.parse(any(), eq(MEDIA)))
        .thenThrow(new XmlParseException("broken", new IOException("connection reset")));

    assertThat(runner.run()).isEqualTo(FullTextParseRunStats.of(1, 0, 0, 1));

    verifyNoInteractions(persister);
  }

  private static StoredFile file() {
    return StoredFile.of(
        new ByteArrayInputStream("<article/>".getBytes()),
        StoredFileInfo.of(10, "application/xml", "e"));
  }
}
