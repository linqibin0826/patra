package dev.patra.catalog.infra.fulltext.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.read.FetchCandidate;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.autoconfigure.PatraHibernatePropertiesCustomizer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `FullTextCandidateReadAdapter` 集成测试：抓取候选的四类与排除项。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  FullTextCandidateReadAdapter.class,
  JpaAuditingConfiguration.class,
  PatraHibernatePropertiesCustomizer.class,
  JacksonAutoConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("FullTextCandidateReadAdapter 集成测试")
class FullTextCandidateReadAdapterIT {

  private static final Instant NOW = Instant.parse("2026-10-20T05:00:00Z");
  private static final Instant PAST = NOW.minusSeconds(3600);
  private static final Instant FUTURE = NOW.plusSeconds(3600);

  @Autowired private FullTextCandidateReadAdapter adapter;
  @Autowired private JdbcTemplate jdbc;

  private long idSeq;

  @BeforeEach
  void setUp() {
    idSeq = 9_500_000L;
  }

  @Test
  @DisplayName("新文献：有 PMCID、没有全文记录、未删除，按 publication_id keyset")
  void should_find_new_candidates_without_record() {
    long noRecord = publication("PMC1", false);
    long withRecord = publication("PMC2", false);
    fullText(withRecord, "PMC2", "PARSED", null, 0, null);
    long deleted = publication("PMC3", true);
    long second = publication("PMC4", false);

    assertThat(adapter.findNewFetchCandidates(0L, 1))
        .extracting(FetchCandidate::publicationId)
        .containsExactly(noRecord);
    assertThat(adapter.findNewFetchCandidates(noRecord, 10))
        .extracting(FetchCandidate::publicationId)
        .containsExactly(second)
        .doesNotContain(withRecord, deleted);
    assertThat(adapter.findNewFetchCandidates(0L, 10).getFirst().currentStatus()).isNull();
  }

  @Test
  @DisplayName("到期：抓取失败未超上限、暂不可用到期，带当前状态、次数与放开日期")
  void should_find_due_candidates() {
    long failedDue = publication("PMC11", false);
    fullText(failedDue, "PMC11", "FETCH_FAILED", null, 2, PAST);
    long failedExhausted = publication("PMC12", false);
    fullText(failedExhausted, "PMC12", "FETCH_FAILED", null, 8, PAST);
    long failedNotDue = publication("PMC13", false);
    fullText(failedNotDue, "PMC13", "FETCH_FAILED", null, 1, FUTURE);
    long notOpenDue = publication("PMC14", false);
    fullText(notOpenDue, "PMC14", "UNAVAILABLE", "NOT_OPEN_ACCESS", 3, PAST);
    long retracted = publication("PMC15", false);
    fullText(retracted, "PMC15", "UNAVAILABLE", "RETRACTED", 1, null);
    long deleted = publication("PMC16", true);
    fullText(deleted, "PMC16", "FETCH_FAILED", null, 1, PAST);
    jdbc.update(
        "INSERT INTO cat_publication_date (id, publication_id, date_type, date_value, year, month, day)"
            + " VALUES (?, ?, 'pmcrelease', '2027-01-15', 2027, 1, 15)",
        ++idSeq,
        notOpenDue);

    List<FetchCandidate> due = adapter.findDueFetchCandidates(NOW, 8, 100);

    assertThat(due)
        .extracting(FetchCandidate::publicationId)
        .containsExactlyInAnyOrder(failedDue, notOpenDue);
    FetchCandidate failed =
        due.stream().filter(c -> c.publicationId() == failedDue).findFirst().orElseThrow();
    assertThat(failed.currentStatus()).isEqualTo(FullTextStatus.FETCH_FAILED);
    assertThat(failed.attemptCount()).isEqualTo(2);
    FetchCandidate notOpen =
        due.stream().filter(c -> c.publicationId() == notOpenDue).findFirst().orElseThrow();
    assertThat(notOpen.pmcReleaseDate()).isEqualTo(LocalDate.of(2027, 1, 15));
  }

  @Test
  @DisplayName("PMCID 变了：与第一条 pmc 标识符比较，未到下次时间的不取")
  void should_find_pmcid_changed_candidates() {
    long changed = publication("PMC21NEW", false);
    fullText(changed, "PMC21OLD", "PARSED", null, 0, null);
    long changedNotDue = publication("PMC22NEW", false);
    fullText(changedNotDue, "PMC22OLD", "PARSED", null, 1, FUTURE);
    long twoIdentifiers = publication("PMC23", false);
    fullText(twoIdentifiers, "PMC23", "PARSED", null, 0, null);
    jdbc.update(
        "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source)"
            + " VALUES (?, ?, 'pmc', 'PMC23OTHER', 'Crossref')",
        ++idSeq,
        twoIdentifiers);

    List<FetchCandidate> candidates = adapter.findPmcidChangedCandidates(NOW, 0L, 100);

    assertThat(candidates).extracting(FetchCandidate::publicationId).containsExactly(changed);
    assertThat(candidates.getFirst().pmcid()).isEqualTo("PMC21NEW");
  }

  @Test
  @DisplayName("同一篇有两条 pmc 标识符时只出现一次")
  void should_deduplicate_publication_with_two_pmc_identifiers() {
    long id = publication("PMC31", false);
    jdbc.update(
        "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source)"
            + " VALUES (?, ?, 'pmc', 'PMC31', 'Crossref')",
        ++idSeq,
        id);

    assertThat(adapter.findNewFetchCandidates(0L, 10))
        .extracting(FetchCandidate::publicationId)
        .containsExactly(id);
  }

  @Test
  @DisplayName("手动重抓按 ID 取，忽略状态")
  void should_find_by_publication_ids_ignoring_status() {
    long parsed = publication("PMC31", false);
    fullText(parsed, "PMC31", "PARSED", null, 0, null);

    assertThat(adapter.findByPublicationIds(List.of(parsed)))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.pmcid()).isEqualTo("PMC31");
              assertThat(c.currentStatus()).isEqualTo(FullTextStatus.PARSED);
            });
  }

  @Test
  @DisplayName("解析候选：FETCHED，以及解析器版本落后的 PARSED、PARSE_FAILED、NO_BODY")
  void should_include_outdated_parsed_failed_and_no_body() {
    long fetched = publication("PMC41", false);
    fullText(fetched, "PMC41", "FETCHED", null, 0, null);
    long parsedOld = publication("PMC42", false);
    fullText(parsedOld, "PMC42", "PARSED", null, 0, null);
    parserVersion(parsedOld, 1);
    long parsedCurrent = publication("PMC43", false);
    fullText(parsedCurrent, "PMC43", "PARSED", null, 0, null);
    parserVersion(parsedCurrent, 2);
    long failedOld = publication("PMC44", false);
    fullText(failedOld, "PMC44", "PARSE_FAILED", null, 0, null);
    parserVersion(failedOld, 1);
    long noBodyOld = publication("PMC45", false);
    fullText(noBodyOld, "PMC45", "UNAVAILABLE", "NO_BODY", 0, null);
    parserVersion(noBodyOld, 1);
    long notInBucket = publication("PMC46", false);
    fullText(notInBucket, "PMC46", "UNAVAILABLE", "NOT_IN_BUCKET", 1, PAST);
    long deletedFetched = publication("PMC47", true);
    fullText(deletedFetched, "PMC47", "FETCHED", null, 0, null);
    jdbc.update(
        "UPDATE cat_publication_fulltext SET pmc_version = 'PMC41.2', object_prefix = 'pmc/PMC41/PMC41.2/',"
            + " media = '[{\"name\":\"g1.webp\",\"width\":3,\"height\":2}]'::jsonb WHERE publication_id = ?",
        fetched);

    List<ParseCandidate> candidates = adapter.findParseCandidates(2, 0L, 100);

    assertThat(candidates)
        .extracting(ParseCandidate::publicationId)
        .containsExactly(fetched, parsedOld, failedOld, noBodyOld);
    ParseCandidate first = candidates.getFirst();
    assertThat(first.pmcVersion()).isEqualTo("PMC41.2");
    assertThat(first.objectPrefix()).isEqualTo("pmc/PMC41/PMC41.2/");
    assertThat(first.media()).containsExactly(FullTextMedia.of("g1.webp", 3, 2));
  }

  private void parserVersion(long publicationId, int version) {
    jdbc.update(
        "UPDATE cat_publication_fulltext SET parser_version = ? WHERE publication_id = ?",
        version,
        publicationId);
  }

  private long publication(String pmcid, boolean deleted) {
    long id = ++idSeq;
    jdbc.update(
        "INSERT INTO cat_publication (id, provenance_code, pmid, venue_instance_id, title,"
            + " publication_year, deleted_at) VALUES (?, 'PUBMED', ?, 1, 'T', 2025, ?)",
        id,
        String.valueOf(id),
        deleted ? Timestamp.from(PAST) : null);
    jdbc.update(
        "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source)"
            + " VALUES (?, ?, 'pmc', ?, 'PubMed')",
        ++idSeq,
        id,
        pmcid);
    return id;
  }

  private void fullText(
      long publicationId,
      String pmcid,
      String status,
      String reason,
      int attempts,
      Instant nextAttemptAt) {
    jdbc.update(
        "INSERT INTO cat_publication_fulltext (id, publication_id, pmcid, status, unavailable_reason,"
            + " attempt_count, next_attempt_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        ++idSeq,
        publicationId,
        pmcid,
        status,
        reason,
        attempts,
        nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt));
  }
}
