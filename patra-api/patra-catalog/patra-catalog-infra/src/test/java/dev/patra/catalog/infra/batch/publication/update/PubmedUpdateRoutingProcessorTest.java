package dev.patra.catalog.infra.batch.publication.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.model.enums.PublicationMedium;
import dev.patra.catalog.domain.model.enums.PublicationStatus;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.LanguageInfo;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.domain.model.vo.venue.VenueId;
import dev.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.batch.publication.PublicationImportResult;
import dev.patra.catalog.infra.batch.publication.PubmedArticleItemProcessor;
import dev.patra.common.CanonicalPublication;
import dev.patra.common.CanonicalPublication.Identifier;
import dev.patra.common.ProvenanceCode;
import dev.patra.common.PublicationIdentifierType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PubmedUpdateRoutingProcessor` 单元测试。
@DisplayName("PubmedUpdateRoutingProcessor")
class PubmedUpdateRoutingProcessorTest {

  private static final String PMID = "91000001";

  private PublicationSyncRepository syncRepository;
  private PubmedArticleItemProcessor delegate;
  private PubmedUpdateCounters counters;

  @BeforeEach
  void setUp() {
    syncRepository = mock(PublicationSyncRepository.class);
    delegate = mock(PubmedArticleItemProcessor.class);
    counters = new PubmedUpdateCounters();
  }

  @Test
  @DisplayName("EXISTING_ONLY 下库里没有的记录直接跳过，不调用委托处理器")
  void should_not_call_delegate_when_absent_and_existing_only() throws Exception {
    when(syncRepository.findSyncTargetsByPmids(anyCollection())).thenReturn(Map.of());

    PublicationSyncItem item = processor(PubmedUpdateScope.EXISTING_ONLY).process(article(PMID));

    assertThat(item).isNull();
    verifyNoInteractions(delegate);
    assertThat(counters.snapshot(0).skippedAbsent()).isEqualTo(1);
  }

  @Test
  @DisplayName("ALL 下库里没有的记录交给委托处理器，作为新建项")
  void should_create_when_absent_and_scope_all() throws Exception {
    CanonicalPublication article = article(PMID);
    PublicationImportResult result = result();
    when(syncRepository.findSyncTargetsByPmids(anyCollection())).thenReturn(Map.of());
    when(delegate.process(article)).thenReturn(result);

    PublicationSyncItem item = processor(PubmedUpdateScope.ALL).process(article);

    assertThat(item.result()).isSameAs(result);
    assertThat(item.target()).isNull();
  }

  @Test
  @DisplayName("库里已有的记录带上同步目标")
  void should_carry_target_when_present() throws Exception {
    CanonicalPublication article = article(PMID);
    PublicationImportResult result = result();
    PublicationSyncTarget target = PublicationSyncTarget.of(7L, PMID, "10.1/x", false);
    when(syncRepository.findSyncTargetsByPmids(anyCollection())).thenReturn(Map.of(PMID, target));
    when(delegate.process(article)).thenReturn(result);

    PublicationSyncItem item = processor(PubmedUpdateScope.EXISTING_ONLY).process(article);

    assertThat(item.result()).isSameAs(result);
    assertThat(item.target()).isEqualTo(target);
  }

  @Test
  @DisplayName("委托处理器判为无效时跳过并计数")
  void should_count_invalid_when_delegate_returns_null() throws Exception {
    CanonicalPublication article = article(PMID);
    PublicationSyncTarget target = PublicationSyncTarget.of(7L, PMID, null, false);
    when(syncRepository.findSyncTargetsByPmids(anyCollection())).thenReturn(Map.of(PMID, target));
    when(delegate.process(article)).thenReturn(null);

    assertThat(processor(PubmedUpdateScope.EXISTING_ONLY).process(article)).isNull();
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
  }

  @Test
  @DisplayName("没有 PMID 的记录计为无效，不查库")
  void should_count_invalid_when_pmid_missing() throws Exception {
    CanonicalPublication article = CanonicalPublication.builder().title("T").build();

    assertThat(processor(PubmedUpdateScope.ALL).process(article)).isNull();
    verifyNoInteractions(syncRepository, delegate);
    assertThat(counters.snapshot(0).skippedInvalid()).isEqualTo(1);
  }

  private PubmedUpdateRoutingProcessor processor(PubmedUpdateScope scope) {
    return new PubmedUpdateRoutingProcessor(syncRepository, delegate, scope, counters);
  }

  private static CanonicalPublication article(String pmid) {
    return CanonicalPublication.builder()
        .identifiers(
            List.of(Identifier.builder().type(PublicationIdentifierType.PMID).value(pmid).build()))
        .title("T")
        .build();
  }

  private static PublicationImportResult result() {
    return PublicationImportResult.ofPublication(
        PublicationAggregate.create(
            ProvenanceCode.PUBMED,
            PMID,
            null,
            VenueId.of(1L),
            VenueInstanceId.of(2L),
            "T",
            null,
            LanguageInfo.of("English", "en-US"),
            PublicationStatus.PPUBLISH,
            PublicationMedium.PRINT,
            2024,
            true,
            0,
            null));
  }
}
