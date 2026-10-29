package dev.patra.catalog.infra.publication.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.publication.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.publication.model.enums.PublicationMedium;
import dev.patra.catalog.domain.publication.model.enums.PublicationStatus;
import dev.patra.catalog.domain.publication.model.vo.LanguageInfo;
import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.PublicationId;
import dev.patra.catalog.domain.publication.model.vo.PublicationMetadata;
import dev.patra.catalog.domain.publication.model.vo.PublicationSourceRow;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.infra.author.persistence.AuthorOrcidReadAdapter;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.common.ProvenanceCode;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 来源位置守卫的持久化一侧（spec 第 5.2、5.3 节）：真实 SQL 下的同步目标查询、来源行 upsert、
/// 带守卫的软删与墓碑、`last_synced_at` 的刷新规则。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PublicationRepositoryAdapter.class,
  AuthorOrcidReadAdapter.class,
  PublicationSyncRepositoryAdapter.class,
  JpaAuditingConfiguration.class,
  JacksonAutoConfiguration.class,
  JsonColumnCodec.class
})
@ComponentScan(
    basePackages = {
      "dev.patra.catalog.infra.*.persistence.mapper",
      "dev.patra.catalog.infra.*.persistence.converter"
    })
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("来源位置守卫集成测试")
class PublicationSourceGuardIT {

  private static final Instant NOW = Instant.parse("2026-10-24T05:00:00Z");
  private static final Instant ORIGINAL_SYNC = Instant.parse("2026-01-01T00:00:00Z");
  private static final SourcePosition P1340 = SourcePosition.ofFileName("pubmed26n1340.xml.gz");
  private static final SourcePosition P1350 = SourcePosition.ofFileName("pubmed26n1350.xml.gz");
  private static final SourcePosition P1360 = SourcePosition.ofFileName("pubmed26n1360.xml.gz");

  @Autowired private PublicationRepositoryAdapter publicationRepository;
  @Autowired private PublicationSyncRepositoryAdapter adapter;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("只有文献、没有来源行的旧数据：能查到，sourceRank 为空，覆盖后写上来源行")
  void publication_without_source_row_is_found_with_null_rank_and_overwritten() {
    long id = insert("92000001");

    PublicationSyncTarget target =
        adapter.findSyncTargetsByPmids(List.of("92000001")).get("92000001");

    assertThat(target.publicationId()).isEqualTo(id);
    assertThat(target.sourceRank()).isNull();
    assertThat(target.deleted()).isFalse();

    adapter.upsertSources(List.of(source("92000001", P1350, "<raw/>")));
    flushAndClear();
    assertThat(adapter.findSyncTargetsByPmids(List.of("92000001")).get("92000001").sourceRank())
        .isEqualTo(261_350);
  }

  @Test
  @DisplayName("后面的文件软删后，同步目标为已删、位置为删除文件（处理器据此挡住更早的重放）")
  void deleted_in_later_file_stays_deleted_when_earlier_file_replayed() {
    insert("92000002");
    adapter.upsertSources(List.of(source("92000002", P1340, "<raw/>")));

    int deleted = adapter.softDeleteByPmids(List.of("92000002"), P1350, false, NOW);
    flushAndClear();

    assertThat(deleted).isEqualTo(1);
    PublicationSyncTarget target =
        adapter.findSyncTargetsByPmids(List.of("92000002")).get("92000002");
    assertThat(target.deleted()).isTrue();
    assertThat(target.sourceRank()).isEqualTo(261_350);
    assertThat(rawXml("92000002")).isEqualTo("<raw/>");
  }

  @Test
  @DisplayName("更早文件的删除不撤销后面文件的复活：不软删、位置不降")
  void earlier_delete_does_not_undo_later_revival() {
    insert("92000003");
    adapter.upsertSources(List.of(source("92000003", P1360, "<raw/>")));

    int deleted = adapter.softDeleteByPmids(List.of("92000003"), P1340, false, NOW);
    flushAndClear();

    assertThat(deleted).isZero();
    PublicationSyncTarget target =
        adapter.findSyncTargetsByPmids(List.of("92000003")).get("92000003");
    assertThat(target.deleted()).isFalse();
    assertThat(target.sourceRank()).isEqualTo(261_360);
  }

  @Test
  @DisplayName("同一文件里既有文章又有 DeleteCitation：删除生效")
  void delete_in_same_file_as_article_applies() {
    insert("92000004");
    adapter.upsertSources(List.of(source("92000004", P1350, "<raw/>")));

    assertThat(adapter.softDeleteByPmids(List.of("92000004"), P1350, false, NOW)).isEqualTo(1);
  }

  @Test
  @DisplayName("ALL 范围删除库里没有的 PMID：写墓碑，之后查得到只有来源行的目标；EXISTING_ONLY 不写")
  void tombstone_blocks_insert_from_earlier_baseline_in_all_scope() {
    adapter.softDeleteByPmids(List.of("92000005"), P1350, true, NOW);
    adapter.softDeleteByPmids(List.of("92000006"), P1350, false, NOW);
    flushAndClear();

    Map<String, PublicationSyncTarget> targets =
        adapter.findSyncTargetsByPmids(List.of("92000005", "92000006"));
    assertThat(targets).containsOnlyKeys("92000005");
    assertThat(targets.get("92000005").isTombstone()).isTrue();
    assertThat(targets.get("92000005").sourceRank()).isEqualTo(261_350);
    assertThat(rawXml("92000005")).isNull();
  }

  @Test
  @DisplayName("覆盖时只给 refreshSyncedAt 里的文献刷新 last_synced_at，其余保留原值")
  void equal_rank_and_null_rank_overwrites_keep_last_synced_at() {
    long kept = insert("92000007");
    long refreshed = insert("92000008");
    jdbc.update(
        "UPDATE cat_publication SET last_synced_at = ? WHERE id IN (?, ?)",
        Timestamp.from(ORIGINAL_SYNC),
        kept,
        refreshed);
    flushAndClear();

    adapter.overwriteAllWithAssociations(
        List.of(overwriteData("92000007", kept), overwriteData("92000008", refreshed)),
        Map.of(),
        NOW,
        Set.of(refreshed));
    flushAndClear();

    assertThat(syncedAt(kept)).isEqualTo(ORIGINAL_SYNC);
    assertThat(syncedAt(refreshed)).isEqualTo(NOW);
  }

  @Test
  @DisplayName("本地重算候选：按 PMID 分页，只要有原文且文献未软删的；墓碑与软删的不出现")
  void should_page_reparse_candidates_excluding_tombstones_and_deleted() {
    insert("92000101");
    insert("92000102");
    insert("92000103");
    adapter.upsertSources(
        List.of(
            source("92000101", P1340, "<a/>"),
            source("92000102", P1350, "<b/>"),
            source("92000103", P1350, "<c/>")));
    adapter.softDeleteByPmids(List.of("92000103"), P1360, false, NOW);
    adapter.softDeleteByPmids(List.of("92000199"), P1360, true, NOW);
    flushAndClear();

    List<PubmedSourceRecord> first = adapter.findReparseCandidates(null, 1);
    List<PubmedSourceRecord> rest = adapter.findReparseCandidates(first.getLast().pmid(), 10);

    assertThat(first).extracting(PubmedSourceRecord::pmid).containsExactly("92000101");
    assertThat(first.getFirst().rawXml()).isEqualTo("<a/>");
    assertThat(first.getFirst().position()).isEqualTo(P1340);
    assertThat(rest).extracting(PubmedSourceRecord::pmid).containsExactly("92000102");
  }

  @Test
  @DisplayName("同步目标查询由请求的 PMID 驱动：两张表都走索引，不做全连接")
  void sync_target_lookup_uses_indexes_without_full_join() {
    em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();

    List<?> plan =
        em.createNativeQuery("EXPLAIN " + PublicationSyncRepositoryAdapter.SYNC_TARGETS_SQL)
            .setParameter("pmids", new String[] {"92000401", "92000402"})
            .getResultList();

    String text = plan.stream().map(String::valueOf).collect(Collectors.joining("\n"));
    assertThat(text)
        .doesNotContain("Full Join")
        .doesNotContain("Seq Scan")
        .contains("uk_pmid")
        .contains("cat_publication_source_pkey");
  }

  @Test
  @DisplayName("删除列表里同一 PMID 出现两次：照常软删、来源表各一行，不报错")
  void duplicate_pmids_in_delete_list_are_handled_once() {
    insert("92000301");

    int deleted =
        adapter.softDeleteByPmids(
            List.of("92000301", "92000301", "92000302", "92000302"), P1350, true, NOW);
    flushAndClear();

    assertThat(deleted).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_source WHERE pmid IN ('92000301', '92000302')",
                Integer.class))
        .isEqualTo(2);
  }

  // ===== fixtures =====

  private long insert(String pmid) {
    PublicationAggregate p = aggregate(pmid);
    publicationRepository.insertAllWithAssociations(
        List.of(
            PublicationCompleteData.builder()
                .publication(p)
                .metadata(PublicationMetadata.ofImport(ProvenanceCode.PUBMED, "batch"))
                .build()));
    flushAndClear();
    return p.getId().value();
  }

  private static PublicationCompleteData overwriteData(String pmid, long id) {
    PublicationAggregate p = aggregate(pmid);
    p.assignId(PublicationId.of(id));
    return PublicationCompleteData.builder()
        .publication(p)
        .metadata(PublicationMetadata.ofImport(ProvenanceCode.PUBMED, "batch-2"))
        .build();
  }

  private static PublicationAggregate aggregate(String pmid) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        null,
        VenueId.of(1001L),
        VenueInstanceId.of(2001L),
        "Title " + pmid,
        null,
        LanguageInfo.of("English", "en-US"),
        PublicationStatus.PPUBLISH,
        PublicationMedium.PRINT,
        2024,
        true,
        0,
        null);
  }

  private static PublicationSourceRow source(String pmid, SourcePosition position, String raw) {
    return PublicationSourceRow.builder()
        .pmid(pmid)
        .sourceFile(position.fileName())
        .sourceRank(position.rank())
        .rawXml(raw)
        .parsedAt(NOW)
        .build();
  }

  private String rawXml(String pmid) {
    return jdbc.queryForObject(
        "SELECT raw_xml FROM cat_publication_source WHERE pmid = ?", String.class, pmid);
  }

  private Instant syncedAt(long id) {
    return jdbc.queryForObject(
            "SELECT last_synced_at FROM cat_publication WHERE id = ?", Timestamp.class, id)
        .toInstant();
  }

  private void flushAndClear() {
    em.flush();
    em.clear();
  }
}
