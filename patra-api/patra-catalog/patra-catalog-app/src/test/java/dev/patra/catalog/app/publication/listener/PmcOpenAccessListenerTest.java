package dev.patra.catalog.app.publication.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.patra.catalog.domain.fulltext.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.publication.model.vo.PublicationOaLocation;
import dev.patra.catalog.domain.publication.port.repository.PublicationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PmcOpenAccessListener` 单元测试。
@DisplayName("PmcOpenAccessListener")
class PmcOpenAccessListenerTest {

  private final PublicationRepository publicationRepository = mock(PublicationRepository.class);
  private final PmcOpenAccessListener listener = new PmcOpenAccessListener(publicationRepository);

  @Test
  @DisplayName("收到全文已抓取事件时把文献标为 PMC 开放获取")
  void should_mark_pmc_open_access_on_event() {
    listener.on(new PmcFullTextFetchedEvent(7L, "PMC1", "CC BY", true));

    verify(publicationRepository)
        .markPmcOpenAccess(7L, PublicationOaLocation.ofPmc("PMC1", "CC BY", true));
  }
}
