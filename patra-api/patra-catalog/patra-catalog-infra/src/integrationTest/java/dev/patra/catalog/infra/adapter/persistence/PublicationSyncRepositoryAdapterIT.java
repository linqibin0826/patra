package dev.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.model.aggregate.PublicationAggregate;
import dev.patra.catalog.domain.model.enums.PublicationDateType;
import dev.patra.catalog.domain.model.enums.PublicationMedium;
import dev.patra.catalog.domain.model.enums.PublicationStatus;
import dev.patra.catalog.domain.model.vo.publication.LanguageInfo;
import dev.patra.catalog.domain.model.vo.publication.Pagination;
import dev.patra.catalog.domain.model.vo.publication.PublicationAuthorSnapshot;
import dev.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.patra.catalog.domain.model.vo.publication.PublicationId;
import dev.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import dev.patra.catalog.domain.model.vo.publication.PublicationMeshHeading;
import dev.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.domain.model.vo.publication.SourcePosition;
import dev.patra.catalog.domain.model.vo.venue.VenueId;
import dev.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import dev.patra.catalog.infra.adapter.read.AuthorOrcidReadAdapter;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.common.ProvenanceCode;
import dev.patra.common.PublicationIdentifierType;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `PublicationSyncRepositoryAdapter` 集成测试：覆盖更新保留别的流程写入的数据。
///
/// 插入后与每次 native SQL 改数之后都 `flush` + `clear`，避免托管实体掩盖或冲掉 native 写入。
/// 导入生产同款的持久化异常转换（切片默认没有），验证 `@Repository` 把唯一约束冲突转成 Spring 异常。
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
@ComponentScan(basePackages = "dev.patra.catalog.infra.persistence.converter")
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("PublicationSyncRepositoryAdapter 集成测试")
class PublicationSyncRepositoryAdapterIT {

  private static final Instant SYNCED_AT = Instant.parse("2026-10-20T05:00:00Z");

  @Autowired private PublicationRepositoryAdapter publicationRepository;
  @Autowired private PublicationSyncRepositoryAdapter adapter;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("覆盖时保留别的流程写入的字段，PubMed 管的部分全部替换，内部 ID 不变")
  void should_keep_fields_owned_by_other_flows_when_overwriting() {
    PublicationAggregate original = aggregate("91000001", "10.9100/a", "Title A");
    original.attachAuthors(List.of(author("Author X")));
    original.addExtendedIdentifiers(
        List.of(PublicationIdentifier.of(PublicationIdentifierType.PMC, "PMC9100001", "PubMed")));
    publicationRepository.insertAllWithAssociations(
        List.of(completeData(original, "D000001", "batch-1")));
    flushAndClear();
    long id = original.getId().value();

    jdbc.update("UPDATE cat_publication SET is_oa = true, citation_count = 42 WHERE id = ?", id);
    jdbc.update(
        "UPDATE cat_publication_metadata SET has_full_text = true, full_text_url = 'https://x'"
            + " WHERE publication_id = ?",
        id);
    jdbc.update(
        "INSERT INTO cat_publication_oa_location (id, publication_id, oa_status, location_type, url)"
            + " VALUES (?, ?, 'GREEN', 'pubmed_central', 'https://pmc/1')",
        id + 1,
        id);
    jdbc.update(
        "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source)"
            + " VALUES (?, ?, 'pii', 'S0000-PII', 'Crossref')",
        id + 2,
        id);
    jdbc.update(
        "INSERT INTO cat_publication_fulltext (id, publication_id, pmcid, status)"
            + " VALUES (?, ?, 'PMC9100001', 'PARSED')",
        id + 3,
        id);
    jdbc.update(
        "INSERT INTO cat_publication_fulltext_body (publication_id, body, parser_version)"
            + " VALUES (?, '{}'::jsonb, 1)",
        id);
    flushAndClear();
    int publicationRows = count("SELECT count(*) FROM cat_publication");

    PublicationAggregate revised = aggregate("91000001", "10.9100/a", "Title B");
    revised.attachAuthors(List.of(author("Author Y")));
    revised.addExtendedIdentifiers(
        List.of(PublicationIdentifier.of(PublicationIdentifierType.PMC, "PMC9100001", "PubMed")));
    revised.assignId(PublicationId.of(id));
    adapter.overwriteAllWithAssociations(
        List.of(completeData(revised, "D000002", "batch-2")), Map.of(), SYNCED_AT, Set.of(id));
    flushAndClear();

    assertThat(string("SELECT title FROM cat_publication WHERE id = ?", id)).isEqualTo("Title B");
    assertThat(
            jdbc.queryForList(
                "SELECT display_name FROM cat_publication_author WHERE publication_id = ?",
                String.class,
                id))
        .containsExactly("Author Y");
    assertThat(
            jdbc.queryForList(
                "SELECT descriptor_ui FROM cat_publication_mesh_heading WHERE publication_id = ?",
                String.class,
                id))
        .containsExactly("D000002");
    assertThat(
            jdbc.queryForObject(
                "SELECT is_oa FROM cat_publication WHERE id = ?", Boolean.class, id))
        .isTrue();
    assertThat(count("SELECT citation_count FROM cat_publication WHERE id = ?", id)).isEqualTo(42);
    assertThat(
            jdbc.queryForObject(
                "SELECT has_full_text FROM cat_publication_metadata WHERE publication_id = ?",
                Boolean.class,
                id))
        .isTrue();
    assertThat(
            string(
                "SELECT full_text_url FROM cat_publication_metadata WHERE publication_id = ?", id))
        .isEqualTo("https://x");
    assertThat(
            string(
                "SELECT import_batch FROM cat_publication_metadata WHERE publication_id = ?", id))
        .isEqualTo("batch-2");
    assertThat(
            count("SELECT count(*) FROM cat_publication_oa_location WHERE publication_id = ?", id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT source FROM cat_publication_identifier WHERE publication_id = ? ORDER BY source",
                String.class,
                id))
        .containsExactly("Crossref", "PubMed");
    assertThat(count("SELECT count(*) FROM cat_publication_fulltext WHERE publication_id = ?", id))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM cat_publication_fulltext_body WHERE publication_id = ?", id))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM cat_publication")).isEqualTo(publicationRows);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_synced_at FROM cat_publication WHERE id = ?",
                OffsetDateTime.class,
                id))
        .isEqualTo(SYNCED_AT.atOffset(ZoneOffset.UTC));
  }

  @Test
  @DisplayName("已软删除的文献被覆盖时复活，内容为新值")
  void should_revive_soft_deleted_publication_when_overwriting() {
    PublicationAggregate original = aggregate("91000002", "10.9100/b", "Old");
    publicationRepository.insertAllWithAssociations(
        List.of(completeData(original, "D000001", "batch-1")));
    flushAndClear();
    long id = original.getId().value();
    adapter.softDeleteByPmids(
        List.of("91000002"), SourcePosition.ofFileName("pubmed26n1350.xml.gz"), false, SYNCED_AT);
    flushAndClear();

    PublicationAggregate revised = aggregate("91000002", "10.9100/b", "New");
    revised.assignId(PublicationId.of(id));
    adapter.overwriteAllWithAssociations(
        List.of(completeData(revised, "D000003", "batch-2")), Map.of(), SYNCED_AT, Set.of());
    flushAndClear();

    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM cat_publication WHERE id = ?", Boolean.class, id))
        .isTrue();
    assertThat(string("SELECT title FROM cat_publication WHERE id = ?", id)).isEqualTo("New");
  }

  @Test
  @DisplayName("按 PMID 能查到已软删除的同步目标")
  void should_find_soft_deleted_target_by_pmid() {
    PublicationAggregate original = aggregate("91000003", "10.9100/c", "T");
    publicationRepository.insertAllWithAssociations(
        List.of(PublicationCompleteData.ofPublication(original)));
    flushAndClear();
    adapter.softDeleteByPmids(
        List.of("91000003"), SourcePosition.ofFileName("pubmed26n1350.xml.gz"), false, SYNCED_AT);
    flushAndClear();

    Map<String, PublicationSyncTarget> targets =
        adapter.findSyncTargetsByPmids(List.of("91000003", "99999999"));

    assertThat(targets).containsOnlyKeys("91000003");
    PublicationSyncTarget target = targets.get("91000003");
    assertThat(target.publicationId()).isEqualTo(original.getId().value());
    assertThat(target.doi()).isEqualTo("10.9100/c");
    assertThat(target.deleted()).isTrue();
  }

  @Test
  @DisplayName("同一 PMID 删两次，第二次不计数")
  void should_not_count_already_deleted_when_soft_deleting() {
    publicationRepository.insertAllWithAssociations(
        List.of(PublicationCompleteData.ofPublication(aggregate("91000004", "10.9100/d", "T"))));
    flushAndClear();

    assertThat(
            adapter.softDeleteByPmids(
                List.of("91000004", "99999998"),
                SourcePosition.ofFileName("pubmed26n1350.xml.gz"),
                false,
                SYNCED_AT))
        .isEqualTo(1);
    flushAndClear();
    assertThat(
            adapter.softDeleteByPmids(
                List.of("91000004"),
                SourcePosition.ofFileName("pubmed26n1350.xml.gz"),
                false,
                SYNCED_AT))
        .isZero();
  }

  @Test
  @DisplayName("按 DOI 原值精确查归属，包含已软删除的行、区分大小写")
  void should_find_doi_owner_including_soft_deleted_and_exact_case() {
    PublicationAggregate upper = aggregate("91000005", "10.9100/ABC", "T");
    PublicationAggregate deleted = aggregate("91000006", "10.9100/x", "T");
    publicationRepository.insertAllWithAssociations(
        List.of(
            PublicationCompleteData.ofPublication(upper),
            PublicationCompleteData.ofPublication(deleted)));
    flushAndClear();
    adapter.softDeleteByPmids(
        List.of("91000006"), SourcePosition.ofFileName("pubmed26n1350.xml.gz"), false, SYNCED_AT);
    flushAndClear();

    assertThat(adapter.findPublicationIdsByDois(List.of("10.9100/ABC", "10.9100/x")))
        .containsEntry("10.9100/ABC", upper.getId().value())
        .containsEntry("10.9100/x", deleted.getId().value());
    assertThat(adapter.findPublicationIdsByDois(List.of("10.9100/abc"))).isEmpty();
  }

  @Test
  @DisplayName("DOI 冲突时按 doiOverrides 写入旧 DOI")
  void should_apply_doi_override_when_overwriting() {
    PublicationAggregate original = aggregate("91000007", "10.9100/old", "T");
    publicationRepository.insertAllWithAssociations(
        List.of(PublicationCompleteData.ofPublication(original)));
    flushAndClear();
    long id = original.getId().value();

    PublicationAggregate revised = aggregate("91000007", "10.9100/taken", "T2");
    revised.assignId(PublicationId.of(id));
    adapter.overwriteAllWithAssociations(
        List.of(PublicationCompleteData.ofPublication(revised)),
        Map.of(id, "10.9100/old"),
        SYNCED_AT,
        Set.of());
    flushAndClear();

    assertThat(string("SELECT doi FROM cat_publication WHERE id = ?", id)).isEqualTo("10.9100/old");
    assertThat(string("SELECT title FROM cat_publication WHERE id = ?", id)).isEqualTo("T2");
  }

  @Test
  @DisplayName("覆盖时撞 uk_doi 抛 DataIntegrityViolationException（作业的跳过策略认得）")
  void should_translate_unique_violation_to_data_integrity_exception() {
    PublicationAggregate a = aggregate("91000008", "10.9100/dup", "A");
    PublicationAggregate b = aggregate("91000009", "10.9100/other", "B");
    publicationRepository.insertAllWithAssociations(
        List.of(
            PublicationCompleteData.ofPublication(a), PublicationCompleteData.ofPublication(b)));
    flushAndClear();

    PublicationAggregate revisedB = aggregate("91000009", "10.9100/dup", "B2");
    revisedB.assignId(PublicationId.of(b.getId().value()));

    assertThatThrownBy(
            () ->
                adapter.overwriteAllWithAssociations(
                    List.of(PublicationCompleteData.ofPublication(revisedB)),
                    Map.of(),
                    SYNCED_AT,
                    Set.of()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("插入：引用列与按精度的日期落库，年精度的日期行不带月日、date_value 为空")
  void should_persist_citation_columns_and_precise_dates() {
    PublicationAggregate p = aggregate("91000101", "10.9100/c1", "Citation insert");
    p.assignCitationDetails(10, null, Pagination.of("1451-61", "1451", "1461"), "N Engl J Med");
    publicationRepository.insertAllWithAssociations(
        List.of(
            PublicationCompleteData.builder()
                .publication(p)
                .metadata(PublicationMetadata.ofImport(ProvenanceCode.PUBMED, "batch-c1"))
                .dates(List.of(PublicationDate.ofYear(PublicationDateType.PUBLISHED, 2024)))
                .build()));
    flushAndClear();
    long id = p.getId().value();

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT publication_month, publication_day, medline_pgn, start_page, end_page,"
                + " journal_iso_abbreviation FROM cat_publication WHERE id = ?",
            id);
    assertThat(row)
        .containsEntry("publication_month", 10)
        .containsEntry("medline_pgn", "1451-61")
        .containsEntry("start_page", "1451")
        .containsEntry("end_page", "1461")
        .containsEntry("journal_iso_abbreviation", "N Engl J Med");
    assertThat(row.get("publication_day")).isNull();
    Map<String, Object> date =
        jdbc.queryForMap(
            "SELECT month, day, date_value, date_precision FROM cat_publication_date"
                + " WHERE publication_id = ?",
            id);
    assertThat(date.get("month")).isNull();
    assertThat(date.get("day")).isNull();
    assertThat(date.get("date_value")).isNull();
    assertThat(date).containsEntry("date_precision", "year");
  }

  @Test
  @DisplayName("覆盖：引用列一并更新")
  void should_overwrite_citation_columns() {
    PublicationAggregate original = aggregate("91000102", "10.9100/c2", "Citation overwrite");
    publicationRepository.insertAllWithAssociations(
        List.of(completeData(original, "D000001", "batch-c2")));
    flushAndClear();
    long id = original.getId().value();

    PublicationAggregate revised = aggregate("91000102", "10.9100/c2", "Citation overwrite");
    revised.assignCitationDetails(3, 5, Pagination.of("e12", null, null), "Sci Rep");
    revised.assignId(PublicationId.of(id));
    adapter.overwriteAllWithAssociations(
        List.of(completeData(revised, "D000001", "batch-c2b")), Map.of(), SYNCED_AT, Set.of());
    flushAndClear();

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT publication_month, publication_day, medline_pgn, journal_iso_abbreviation"
                + " FROM cat_publication WHERE id = ?",
            id);
    assertThat(row)
        .containsEntry("publication_month", 3)
        .containsEntry("publication_day", 5)
        .containsEntry("medline_pgn", "e12")
        .containsEntry("journal_iso_abbreviation", "Sci Rep");
  }

  private PublicationCompleteData completeData(
      PublicationAggregate aggregate, String descriptorUi, String importBatch) {
    return PublicationCompleteData.builder()
        .publication(aggregate)
        .metadata(PublicationMetadata.ofImport(ProvenanceCode.PUBMED, importBatch))
        .meshHeadings(List.of(PublicationMeshHeading.ofDescriptorOnly(descriptorUi, true, 1)))
        .build();
  }

  private static PublicationAggregate aggregate(String pmid, String doi, String title) {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        doi,
        VenueId.of(1001L),
        VenueInstanceId.of(2001L),
        title,
        null,
        LanguageInfo.of("English", "en-US"),
        PublicationStatus.PPUBLISH,
        PublicationMedium.PRINT,
        2024,
        true,
        0,
        null);
  }

  private static PublicationAuthorSnapshot author(String displayName) {
    return PublicationAuthorSnapshot.builder()
        .order(1)
        .displayName(displayName)
        .firstAuthor(true)
        .build();
  }

  private void flushAndClear() {
    em.flush();
    em.clear();
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private String string(String sql, Object... args) {
    return jdbc.queryForObject(sql, String.class, args);
  }
}
