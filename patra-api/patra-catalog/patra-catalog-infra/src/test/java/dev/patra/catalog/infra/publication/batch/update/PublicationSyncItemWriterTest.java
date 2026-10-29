package dev.patra.catalog.infra.publication.batch.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.publication.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.publication.model.enums.PublicationMedium;
import dev.patra.catalog.domain.publication.model.enums.PublicationStatus;
import dev.patra.catalog.domain.publication.model.vo.LanguageInfo;
import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.PublicationSourceRow;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult;
import dev.patra.catalog.infra.publication.batch.PublicationImportResultMapper;
import dev.patra.catalog.infra.publication.batch.PublicationItemWriter;
import dev.patra.common.ProvenanceCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  private static final String RAW = "<PubmedArticle/>";
  private static final SourcePosition POSITION = SourcePosition.ofFileName("pubmed26n1335.xml.gz");

  @Captor private ArgumentCaptor<List<PublicationCompleteData>> dataCaptor;

  @Captor private ArgumentCaptor<Map<Long, String>> overridesCaptor;

  @Captor private ArgumentCaptor<List<PublicationSourceRow>> sourcesCaptor;

  @Captor private ArgumentCaptor<Set<Long>> refreshCaptor;

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");

  private PublicationSyncRepository syncRepository;
  private PublicationItemWriter insertWriter;
  private PubmedImportCounters counters;
  private PublicationSyncItemWriter writer;

  @BeforeEach
  void setUp() {
    syncRepository = mock(PublicationSyncRepository.class);
    insertWriter = mock(PublicationItemWriter.class);
    counters = new PubmedImportCounters();
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
    PublicationSyncTarget target =
        PublicationSyncTarget.builder()
            .publicationId(7L)
            .pmid("1")
            .doi(null)
            .deleted(false)
            .build();

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", null, "A"), target, RAW, POSITION),
            PublicationSyncItem.of(result("1", null, "B"), target, RAW, POSITION)));

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
    PublicationSyncTarget target =
        PublicationSyncTarget.builder()
            .publicationId(7L)
            .pmid("1")
            .doi("10.1/old")
            .deleted(false)
            .build();
    when(syncRepository.findPublicationIdsByDois(anyCollection()))
        .thenReturn(Map.of("10.1/taken", 99L));

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", " 10.1/taken ", "T"), target, RAW, POSITION)));

    verify(syncRepository)
        .overwriteAllWithAssociations(anyList(), overridesCaptor.capture(), eq(NOW), anySet());
    assertThat(overridesCaptor.getValue()).containsExactly(Map.entry(7L, "10.1/old"));
    assertThat(counters.snapshot(0).doiConflicts()).isEqualTo(1);
  }

  @Test
  @DisplayName("同一 chunk 里两篇要改成同一个新 DOI 时，只给第一篇，第二篇保留旧 DOI")
  void should_keep_old_doi_when_two_items_in_chunk_claim_same_new_doi() throws Exception {
    PublicationSyncTarget first =
        PublicationSyncTarget.builder()
            .publicationId(7L)
            .pmid("1")
            .doi("10.1/a")
            .deleted(false)
            .build();
    PublicationSyncTarget second =
        PublicationSyncTarget.builder()
            .publicationId(8L)
            .pmid("2")
            .doi("10.1/b")
            .deleted(false)
            .build();

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", "10.1/new", "T1"), first, RAW, POSITION),
            PublicationSyncItem.of(result("2", "10.1/new", "T2"), second, RAW, POSITION)));

    verify(syncRepository)
        .overwriteAllWithAssociations(anyList(), overridesCaptor.capture(), eq(NOW), anySet());
    assertThat(overridesCaptor.getValue()).containsExactly(Map.entry(8L, "10.1/b"));
    assertThat(counters.snapshot(0).doiConflicts()).isEqualTo(1);
  }

  @Test
  @DisplayName("覆盖前把已有 ID 分配给聚合")
  void should_assign_existing_id_before_overwrite() throws Exception {
    PublicationSyncTarget target =
        PublicationSyncTarget.builder()
            .publicationId(7L)
            .pmid("1")
            .doi(null)
            .deleted(false)
            .build();

    writer.write(
        new Chunk<>(PublicationSyncItem.of(result("1", null, "T"), target, RAW, POSITION)));

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
        .thenReturn(
            Map.of(
                "2",
                PublicationSyncTarget.builder()
                    .publicationId(8L)
                    .pmid("2")
                    .doi(null)
                    .deleted(false)
                    .build()));

    writer.write(new Chunk<>(PublicationSyncItem.of(result("2", null, "T"), null, RAW, POSITION)));

    verify(insertWriter).write(any());
    verify(syncRepository, never())
        .overwriteAllWithAssociations(anyList(), anyMap(), any(), anySet());
    assertThat(counters.snapshot(0).created()).isEqualTo(1);
  }

  @Test
  @DisplayName("覆盖抛异常时不计数（Spring Batch 会回滚并逐条重写）")
  void should_not_count_when_overwrite_throws() {
    PublicationSyncTarget target =
        PublicationSyncTarget.builder()
            .publicationId(7L)
            .pmid("1")
            .doi(null)
            .deleted(false)
            .build();
    doThrow(new IllegalStateException("boom"))
        .when(syncRepository)
        .overwriteAllWithAssociations(anyList(), anyMap(), any(), anySet());

    assertThatThrownBy(
            () ->
                writer.write(
                    new Chunk<>(
                        PublicationSyncItem.of(result("1", null, "T"), target, RAW, POSITION))))
        .isInstanceOf(IllegalStateException.class);
    assertThat(counters.snapshot(0).updated()).isZero();
    assertThat(counters.snapshot(0).doiConflicts()).isZero();
  }

  @Test
  @DisplayName("来源行：覆盖的全写；新增的只写真正插入成功的（插入写入器丢掉的不写），回查只算有文献 ID 的")
  void should_upsert_source_for_overwrites_and_inserted_pmids_only() throws Exception {
    PublicationSyncTarget existing =
        PublicationSyncTarget.builder().publicationId(7L).pmid("1").sourceRank(261_300).build();
    when(syncRepository.findSyncTargetsByPmids(anyCollection()))
        .thenReturn(
            Map.of(
                "2",
                PublicationSyncTarget.builder().publicationId(8L).pmid("2").build(),
                "3",
                PublicationSyncTarget.builder().pmid("3").sourceRank(261_400).build()));

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", null, "A"), existing, "<a/>", POSITION),
            PublicationSyncItem.of(result("2", null, "B"), null, "<b/>", POSITION),
            PublicationSyncItem.of(result("3", null, "C"), null, "<c/>", POSITION)));

    verify(syncRepository).upsertSources(sourcesCaptor.capture());
    assertThat(sourcesCaptor.getValue())
        .extracting(PublicationSourceRow::pmid, PublicationSourceRow::rawXml)
        .containsExactlyInAnyOrder(tuple("1", "<a/>"), tuple("2", "<b/>"));
    assertThat(sourcesCaptor.getValue())
        .allSatisfy(
            r -> {
              assertThat(r.sourceFile()).isEqualTo("pubmed26n1335.xml.gz");
              assertThat(r.sourceRank()).isEqualTo(261_335);
              assertThat(r.parsedAt()).isEqualTo(NOW);
            });
    assertThat(counters.snapshot(0).created()).isEqualTo(1);
  }

  @Test
  @DisplayName("last_synced_at 只在库里有位置且进来的更新时刷新；同位置与库里没有位置都不刷新")
  void should_refresh_synced_at_only_for_newer_non_null_rank() throws Exception {
    PublicationSyncTarget older =
        PublicationSyncTarget.builder().publicationId(7L).pmid("1").sourceRank(261_300).build();
    PublicationSyncTarget same =
        PublicationSyncTarget.builder().publicationId(8L).pmid("2").sourceRank(261_335).build();
    PublicationSyncTarget legacy =
        PublicationSyncTarget.builder().publicationId(9L).pmid("3").build();

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", null, "A"), older, RAW, POSITION),
            PublicationSyncItem.of(result("2", null, "B"), same, RAW, POSITION),
            PublicationSyncItem.of(result("3", null, "C"), legacy, RAW, POSITION)));

    verify(syncRepository)
        .overwriteAllWithAssociations(anyList(), anyMap(), eq(NOW), refreshCaptor.capture());
    assertThat(refreshCaptor.getValue()).containsExactly(7L);
  }

  @Test
  @DisplayName("同一 chunk 重复 PMID：来源行也只留后出现的那篇")
  void should_keep_last_item_for_duplicate_pmid_in_chunk() throws Exception {
    PublicationSyncTarget target =
        PublicationSyncTarget.builder().publicationId(7L).pmid("1").build();

    writer.write(
        new Chunk<>(
            PublicationSyncItem.of(result("1", null, "A"), target, "<first/>", POSITION),
            PublicationSyncItem.of(result("1", null, "B"), target, "<second/>", POSITION)));

    verify(syncRepository).upsertSources(sourcesCaptor.capture());
    assertThat(sourcesCaptor.getValue())
        .singleElement()
        .extracting(PublicationSourceRow::rawXml)
        .isEqualTo("<second/>");
  }

  @Test
  @DisplayName("DOI 冲突保留旧 DOI 时，来源行照常写")
  void should_still_upsert_source_when_doi_conflict_keeps_old_doi() throws Exception {
    PublicationSyncTarget target =
        PublicationSyncTarget.builder().publicationId(7L).pmid("1").doi("10.1/old").build();
    when(syncRepository.findPublicationIdsByDois(anyCollection()))
        .thenReturn(Map.of("10.1/taken", 99L));

    writer.write(
        new Chunk<>(PublicationSyncItem.of(result("1", "10.1/taken", "T"), target, RAW, POSITION)));

    verify(syncRepository).upsertSources(sourcesCaptor.capture());
    assertThat(sourcesCaptor.getValue())
        .extracting(PublicationSourceRow::pmid)
        .containsExactly("1");
  }

  private List<PublicationCompleteData> captureOverwrite() {
    verify(syncRepository)
        .overwriteAllWithAssociations(dataCaptor.capture(), anyMap(), eq(NOW), anySet());
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
