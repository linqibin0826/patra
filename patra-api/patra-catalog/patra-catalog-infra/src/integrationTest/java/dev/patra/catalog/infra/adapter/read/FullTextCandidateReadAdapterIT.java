package dev.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.model.enums.FullTextStatus;
import dev.patra.catalog.domain.model.read.fulltext.FetchCandidate;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `FullTextCandidateReadAdapter` 集成测试：抓取候选的四类与排除项。
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FullTextCandidateReadAdapter.class, JpaAuditingConfiguration.class})
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
  @DisplayName("取出四类候选并排除不该抓的，按 publication_id 升序")
  void should_select_four_candidate_classes_and_exclude_others() {
    long noRecord = publication("PMC1", false);
    long failedDue = publication("PMC2", false);
    fullText(failedDue, "PMC2", "FETCH_FAILED", null, 2, PAST);
    long failedExhausted = publication("PMC3", false);
    fullText(failedExhausted, "PMC3", "FETCH_FAILED", null, 8, PAST);
    long failedNotDue = publication("PMC4", false);
    fullText(failedNotDue, "PMC4", "FETCH_FAILED", null, 1, FUTURE);
    long notOpenDue = publication("PMC5", false);
    fullText(notOpenDue, "PMC5", "UNAVAILABLE", "NOT_OPEN_ACCESS", 3, PAST);
    long retracted = publication("PMC6", false);
    fullText(retracted, "PMC6", "UNAVAILABLE", "RETRACTED", 1, null);
    long pmcidChanged = publication("PMC7NEW", false);
    fullText(pmcidChanged, "PMC7OLD", "PARSED", null, 0, null);
    long deleted = publication("PMC8", true);
    long parsed = publication("PMC9", false);
    fullText(parsed, "PMC9", "PARSED", null, 0, null);
    jdbc.update(
        "INSERT INTO cat_publication_date (id, publication_id, date_type, date_value, year, month, day)"
            + " VALUES (?, ?, 'pmcrelease', '2027-01-15', 2027, 1, 15)",
        ++idSeq,
        notOpenDue);

    List<FetchCandidate> candidates = adapter.findFetchCandidates(NOW, 8, 0L, 100);

    assertThat(candidates)
        .extracting(FetchCandidate::publicationId)
        .containsExactly(noRecord, failedDue, notOpenDue, pmcidChanged)
        .doesNotContain(failedExhausted, failedNotDue, retracted, deleted, parsed);
    assertThat(candidates.get(0).currentStatus()).isNull();
    assertThat(candidates.get(1).currentStatus()).isEqualTo(FullTextStatus.FETCH_FAILED);
    assertThat(candidates.get(1).attemptCount()).isEqualTo(2);
    assertThat(candidates.get(2).pmcReleaseDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    assertThat(candidates.get(3).pmcid()).isEqualTo("PMC7NEW");
  }

  @Test
  @DisplayName("keyset 游标与批量大小")
  void should_page_by_last_publication_id() {
    long first = publication("PMC11", false);
    long second = publication("PMC12", false);
    long third = publication("PMC13", false);

    assertThat(adapter.findFetchCandidates(NOW, 8, first, 1))
        .extracting(FetchCandidate::publicationId)
        .containsExactly(second);
    assertThat(adapter.findFetchCandidates(NOW, 8, second, 10))
        .extracting(FetchCandidate::publicationId)
        .containsExactly(third);
  }

  @Test
  @DisplayName("同一篇有两条 pmc 标识符时只出现一次")
  void should_deduplicate_publication_with_two_pmc_identifiers() {
    long id = publication("PMC21", false);
    jdbc.update(
        "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source)"
            + " VALUES (?, ?, 'pmc', 'PMC21', 'Crossref')",
        ++idSeq,
        id);

    assertThat(adapter.findFetchCandidates(NOW, 8, 0L, 10))
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
