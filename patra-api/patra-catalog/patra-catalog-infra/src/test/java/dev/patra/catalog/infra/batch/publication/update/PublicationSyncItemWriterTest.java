package dev.patra.catalog.infra.batch.publication.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.model.enums.PublicationMedium;
import dev.patra.catalog.domain.model.enums.PublicationStatus;
import dev.patra.catalog.domain.model.vo.publication.LanguageInfo;
import dev.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.domain.model.vo.venue.VenueId;
import dev.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.batch.publication.PublicationImportResult;
import dev.patra.catalog.infra.batch.publication.PublicationImportResultMapper;
import dev.patra.catalog.infra.batch.publication.PublicationItemWriter;
import dev.patra.common.ProvenanceCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.infrastructure.item.Chunk;

/// `PublicationSyncItemWriter` 单元测试。
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PublicationSyncItemWriter")
class PublicationSyncItemWriterTest {

  @Captor private ArgumentCaptor<List<PublicationCompleteData>> dataCaptor;

  @Captor private ArgumentCaptor<Map<Long, String>> overridesCaptor;

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");

  private PublicationSyncRepository syncRepository;
  private PublicationItemWriter insertWriter;
  private PubmedUpdateCounters counters;
  private PublicationSyncItemWriter writer;

  @BeforeEach
  void setUp() {
    syncRepository = mock(PublicationSyncRepository.class);
    insertWriter = mock(PublicationItemWriter.class);
    counters = new PubmedUpdateCounters();
    writer =
        new PublicationSyncItemWriter(
            syncRepository,
            insertWriter,
            new PublicationImportResultMapper(),
            counters,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(syncRepository.findPublicationIdsByDois(anyCollection())).thenReturn(Map.of());
  }

  @Test
  @DisplayName("同一 chunk 里同一 PMID 出现两次，以后出现的为准")
  void should_keep_last_occurrence_when_duplicate_pmid_in_chunk() throws Exception {
    PublicationSyncTarget target = PublicationSyncTarget.of(7L, "1", null, false);

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", null, "A"), target),
            PublicationSyncItem.of(result("1", null, "B"), target)));

    List<PublicationCompleteData> written = captureOverwrite();
    assertThat(written)
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.publication().getTitle()).isEqualTo("B");
            });
    assertThat(counters.snapshot(0).updated()).isEqualTo(1);
  }

  @Test
  @DisplayName("新 DOI 已属于另一篇文献时保留旧 DOI 并计数")
  void should_keep_old_doi_when_new_doi_belongs_to_another_publication() throws Exception {
    PublicationSyncTarget target = PublicationSyncTarget.of(7L, "1", "10.1/old", false);
    when(syncRepository.findPublicationIdsByDois(anyCollection()))
        .thenReturn(Map.of("10.1/taken", 99L));

    writer.write(new Chunk<>(PublicationSyncItem.of(result("1", " 10.1/taken ", "T"), target)));

    verify(syncRepository)
        .overwriteAllWithAssociations(anyList(), overridesCaptor.capture(), eq(NOW));
    assertThat(overridesCaptor.getValue()).containsExactly(Map.entry(7L, "10.1/old"));
    assertThat(counters.snapshot(0).doiConflicts()).isEqualTo(1);
  }

  @Test
  @DisplayName("同一 chunk 里两篇要改成同一个新 DOI 时，只给第一篇，第二篇保留旧 DOI")
  void should_keep_old_doi_when_two_items_in_chunk_claim_same_new_doi() throws Exception {
    PublicationSyncTarget first = PublicationSyncTarget.of(7L, "1", "10.1/a", false);
    PublicationSyncTarget second = PublicationSyncTarget.of(8L, "2", "10.1/b", false);

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", "10.1/new", "T1"), first),
            PublicationSyncItem.of(result("2", "10.1/new", "T2"), second)));

    verify(syncRepository)
        .overwriteAllWithAssociations(anyList(), overridesCaptor.capture(), eq(NOW));
    assertThat(overridesCaptor.getValue()).containsExactly(Map.entry(8L, "10.1/b"));
    assertThat(counters.snapshot(0).doiConflicts()).isEqualTo(1);
  }

  @Test
  @DisplayName("覆盖前把已有 ID 分配给聚合")
  void should_assign_existing_id_before_overwrite() throws Exception {
    PublicationSyncTarget target = PublicationSyncTarget.of(7L, "1", null, false);

    writer.write(new Chunk<>(PublicationSyncItem.of(result("1", null, "T"), target)));

    assertThat(captureOverwrite())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getPublicationId()).isEqualTo(7L);
            });
  }

  @Test
  @DisplayName("没有同步目标的记录交给插入写入器，按实际入库条数计新建")
  void should_insert_when_no_target() throws Exception {
    when(syncRepository.findSyncTargetsByPmids(anyCollection()))
        .thenReturn(Map.of("2", PublicationSyncTarget.of(8L, "2", null, false)));

    writer.write(new Chunk<>(PublicationSyncItem.of(result("2", null, "T"), null)));

    verify(insertWriter).write(any());
    verify(syncRepository, never()).overwriteAllWithAssociations(anyList(), anyMap(), any());
    assertThat(counters.snapshot(0).created()).isEqualTo(1);
  }

  @Test
  @DisplayName("覆盖抛异常时不计数（Spring Batch 会回滚并逐条重写）")
  void should_not_count_when_overwrite_throws() {
    PublicationSyncTarget target = PublicationSyncTarget.of(7L, "1", null, false);
    doThrow(new IllegalStateException("boom"))
        .when(syncRepository)
        .overwriteAllWithAssociations(anyList(), anyMap(), any());

    assertThatThrownBy(
            () -> writer.write(new Chunk<>(PublicationSyncItem.of(result("1", null, "T"), target))))
        .isInstanceOf(IllegalStateException.class);
    assertThat(counters.snapshot(0).updated()).isZero();
    assertThat(counters.snapshot(0).doiConflicts()).isZero();
  }

  private List<PublicationCompleteData> captureOverwrite() {
    verify(syncRepository).overwriteAllWithAssociations(dataCaptor.capture(), anyMap(), eq(NOW));
    return dataCaptor.getValue();
  }

  private static PublicationImportResult result(String pmid, String doi, String title) {
    return PublicationImportResult.ofPublication(
        PublicationAggregate.create(
            ProvenanceCode.PUBMED,
            pmid,
            doi,
            VenueId.of(1L),
            VenueInstanceId.of(2L),
            title,
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
