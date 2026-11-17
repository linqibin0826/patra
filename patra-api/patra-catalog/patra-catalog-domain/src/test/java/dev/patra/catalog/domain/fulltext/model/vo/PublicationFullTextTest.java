package dev.patra.catalog.domain.fulltext.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import org.junit.jupiter.api.Test;

/// 全文记录产生「PMC 全文已抓取」事件：文献子域靠它标开放获取，所以带上记录里的 PMCID、许可与是否作者手稿。
///
/// 解析在事务外读对象存储，期间抓取任务可能已重抓：锁住行之后要判断它还是不是当初那条解析候选。
class PublicationFullTextTest {

  private static final ParseCandidate CANDIDATE =
      ParseCandidate.builder()
          .publicationId(1L)
          .pmcid("PMC1234567")
          .pmcVersion("PMC1234567.1")
          .objectPrefix("pmc/PMC1234567/PMC1234567.1/")
          .build();

  private static final PublicationFullText FETCHED =
      PublicationFullText.builder()
          .publicationId(1L)
          .pmcid("PMC1234567")
          .pmcVersion("PMC1234567.1")
          .objectPrefix("pmc/PMC1234567/PMC1234567.1/")
          .status(FullTextStatus.FETCHED)
          .build();

  @Test
  void should_still_await_parsing_when_same_xml_is_fetched() {
    assertThat(FETCHED.stillAwaitsParsing(CANDIDATE, 2)).isTrue();
  }

  @Test
  void should_not_await_parsing_when_refetched_to_another_version() {
    PublicationFullText refetched =
        FETCHED.toBuilder()
            .pmcVersion("PMC1234567.2")
            .objectPrefix("pmc/PMC1234567/PMC1234567.2/")
            .build();

    assertThat(refetched.stillAwaitsParsing(CANDIDATE, 2)).isFalse();
  }

  @Test
  void should_not_await_parsing_when_already_parsed_by_current_parser() {
    PublicationFullText parsed =
        FETCHED.toBuilder().status(FullTextStatus.PARSED).parserVersion(2).build();

    assertThat(parsed.stillAwaitsParsing(CANDIDATE, 2)).isFalse();
  }

  @Test
  void should_await_parsing_when_parsed_or_failed_by_older_parser() {
    assertThat(
            FETCHED.toBuilder()
                .status(FullTextStatus.PARSE_FAILED)
                .parserVersion(1)
                .build()
                .stillAwaitsParsing(CANDIDATE, 2))
        .isTrue();
    assertThat(
            FETCHED.toBuilder()
                .status(FullTextStatus.UNAVAILABLE)
                .unavailableReason(FullTextUnavailableReason.NO_BODY)
                .parserVersion(1)
                .build()
                .stillAwaitsParsing(CANDIDATE, 2))
        .isTrue();
  }

  @Test
  void should_not_await_parsing_when_became_unavailable_for_other_reason() {
    PublicationFullText retracted =
        FETCHED.toBuilder()
            .status(FullTextStatus.UNAVAILABLE)
            .unavailableReason(FullTextUnavailableReason.RETRACTED)
            .build();

    assertThat(retracted.stillAwaitsParsing(CANDIDATE, 2)).isFalse();
  }

  @Test
  void should_produce_fetched_event_from_record() {
    PublicationFullText fullText =
        PublicationFullText.builder()
            .publicationId(1L)
            .pmcid("PMC1234567")
            .status(FullTextStatus.FETCHED)
            .licenseCode("CC BY")
            .manuscript(true)
            .build();

    assertThat(fullText.fetchedEvent())
        .isEqualTo(new PmcFullTextFetchedEvent(1L, "PMC1234567", "CC BY", true));
  }
}
