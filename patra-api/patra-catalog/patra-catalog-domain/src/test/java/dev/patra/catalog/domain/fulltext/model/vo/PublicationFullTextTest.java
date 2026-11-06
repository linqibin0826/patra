package dev.patra.catalog.domain.fulltext.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import org.junit.jupiter.api.Test;

/// 全文记录产生「PMC 全文已抓取」事件：文献子域靠它标开放获取，所以带上记录里的 PMCID、许可与是否作者手稿。
class PublicationFullTextTest {

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
