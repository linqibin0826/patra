package dev.patra.catalog.infra.portal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.CasRatingView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.IdentifierView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.JcrRatingView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.ScopusRatingView;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel.YearlyStatView;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.venue.persistence.entity.JcrRatingEntity;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueDetailReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("VenueDetailReadAdapter 期刊详情查询集成测试")
class VenueDetailReadAdapterIT {

  @Autowired private VenueDetailReadAdapter adapter;
  @Autowired private TestEntityManager em;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("按 ID 查询期刊详情，返回 present + 标量字段正确")
  void shouldReturnVenueDetailWhenExists() {
    Long venueId = saveJournal("Nature", "Nat");
    saveJcr(venueId, 2024, new BigDecimal("60.0"), "Q1");
    saveJcr(venueId, 2025, new BigDecimal("69.5"), "Q1");
    em.flush();
    em.clear();

    Optional<VenueDetailReadModel> result = adapter.findById(venueId);

    assertThat(result).isPresent();
    VenueDetailReadModel model = result.get();
    assertThat(model.title()).isEqualTo("Nature");
    assertThat(model.abbreviatedTitle()).isEqualTo("Nat");
    assertThat(model.venueType()).isEqualTo("JOURNAL");
  }

  @Test
  @DisplayName("最新年 JCR 影响因子写入 impactFactor 标量字段")
  void shouldUseLatestJcrImpactFactor() {
    Long venueId = saveJournal("Science", "Sci");
    saveJcr(venueId, 2024, new BigDecimal("50.0"), "Q1");
    saveJcr(venueId, 2025, new BigDecimal("56.9"), "Q1"); // 最新年应取这条
    em.flush();
    em.clear();

    VenueDetailReadModel model = adapter.findById(venueId).orElseThrow();

    assertThat(model.impactFactor()).isEqualByComparingTo("56.9");
  }

  @Test
  @DisplayName("jcrRatings 列表按年份降序，包含所有年度记录，quartile 来自 jif_quartile 字段")
  void shouldReturnJcrRatingsSortedByYearDesc() {
    Long venueId = saveJournal("Cell", "Cell");
    saveJcr(venueId, 2023, new BigDecimal("45.0"), "Q1");
    saveJcr(venueId, 2025, new BigDecimal("66.8"), "Q2");
    em.flush();
    em.clear();

    VenueDetailReadModel model = adapter.findById(venueId).orElseThrow();

    assertThat(model.jcrRatings()).hasSize(2);
    assertThat(model.jcrRatings())
        .extracting(JcrRatingView::year)
        .containsExactly(2025, 2023); // DESC
    assertThat(model.jcrRatings().get(0).quartile()).isEqualTo("Q2"); // jif_quartile 字段
  }

  @Test
  @DisplayName("ID 不存在时返回 empty")
  void shouldReturnEmptyWhenNotFound() {
    Optional<VenueDetailReadModel> result = adapter.findById(Long.MAX_VALUE);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("非 JOURNAL 类型 venue findById 返回 empty（期刊详情端点不对外暴露非期刊载体）")
  void shouldReturnEmptyForNonJournalVenue() {
    VenueEntity conference = new VenueEntity();
    conference.setId(SnowflakeIdGenerator.getId());
    conference.setVenueType("CONFERENCE");
    conference.setTitle("IEEE Conference");
    conference.setAbbreviatedTitle("IEEE Conf");
    conference.setProvenanceCode("OPENALEX");
    em.persist(conference);
    em.flush();
    em.clear();

    Optional<VenueDetailReadModel> result = adapter.findById(conference.getId());

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("软删除后 findById 返回 empty")
  void shouldReturnEmptyWhenSoftDeleted() {
    Long venueId = saveJournal("PLOS ONE", "PLOS ONE");
    em.flush();
    em.clear();

    VenueEntity venue = em.find(VenueEntity.class, venueId);
    em.remove(venue); // Hibernate @SoftDelete 自动转为 UPDATE deleted_at = now()
    em.flush();
    em.clear();

    Optional<VenueDetailReadModel> result = adapter.findById(venueId);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("全字段：主行每一列与五个子表的值、精度、顺序都按列读出")
  void should_map_every_column_when_venue_is_fully_populated() {
    long id = SnowflakeIdGenerator.getId();
    jdbc.update(
        """
        INSERT INTO cat_venue (id, venue_type, title, provenance_code, issn_l, abbreviated_title,
          primary_language, country_code, image_object_key, cited_by_count,
          publication_profile, citation_metrics, open_access)
        VALUES (?, 'JOURNAL', 'Nature', 'OPENALEX', '0028-0836', 'Nat', 'eng', 'GB',
          'venue/cover.jpg', 987,
          '{"publicationHistory":{"startYear":1869},"frequency":"Weekly",
            "indexingInfo":{"medlineTa":"Nature"}}',
          '{"hIndex":1200,"worksCount":450000}',
          '{"isOa":false,"oaType":"hybrid","apcUsd":11690,"isInDoaj":true}')
        """,
        id);
    String jcr =
        "INSERT INTO cat_venue_jcr_rating (id, venue_id, year, impact_factor, jif_quartile,"
            + " subject, jif_rank, jif_percentile) VALUES (?, ?, ?, ?, ?, 'MULTIDISCIPLINARY', ?, ?)";
    jdbc.update(
        jcr, nextId(), id, 2024, new BigDecimal("60.1"), "Q1", "2/135", new BigDecimal("98.89"));
    jdbc.update(
        jcr, nextId(), id, 2025, new BigDecimal("69.5"), "Q2", "1/134", new BigDecimal("99.63"));
    String cas =
        "INSERT INTO cat_venue_cas_rating (id, venue_id, year, edition, major_category,"
            + " major_quartile, minor_subject, minor_quartile, is_top_journal, is_review_journal)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    jdbc.update(cas, nextId(), id, 2024, "upgrade", "综合", "1区", "综合性期刊", "1区", true, false);
    jdbc.update(cas, nextId(), id, 2025, "upgrade", "综合", "2区", "综合性期刊", "2区", false, null);
    jdbc.update(cas, nextId(), id, 2025, "base", "综合", "1区", "综合性期刊", "1区", true, false);
    String scopus =
        "INSERT INTO cat_venue_scopus_rating (id, venue_id, year, cite_score, sjr, snip, quartile,"
            + " percentile) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    jdbc.update(
        scopus,
        nextId(),
        id,
        2024,
        new BigDecimal("70.2"),
        new BigDecimal("18.5"),
        new BigDecimal("8.1"),
        "Q1",
        new BigDecimal("99.5"));
    jdbc.update(
        scopus,
        nextId(),
        id,
        2025,
        new BigDecimal("75.1"),
        new BigDecimal("20.3"),
        new BigDecimal("9.2"),
        "Q1",
        new BigDecimal("99.9"));
    String stats =
        "INSERT INTO cat_venue_publication_stats (id, venue_id, year, works_count, cited_by_count,"
            + " oa_works_count) VALUES (?, ?, ?, ?, ?, ?)";
    jdbc.update(stats, nextId(), id, 2024, 3000, 90000, null);
    jdbc.update(stats, nextId(), id, 2023, 2900, 85000, 1200);
    String ident =
        "INSERT INTO cat_venue_identifier (id, venue_id, identifier_type, identifier_value,"
            + " is_primary) VALUES (?, ?, ?, ?, ?)";
    jdbc.update(ident, nextId(), id, "EISSN", "1476-4687", false);
    jdbc.update(ident, nextId(), id, "ISSN", "0028-0836", true);

    VenueDetailReadModel expected =
        VenueDetailReadModel.builder()
            .id(id)
            .title("Nature")
            .abbreviatedTitle("Nat")
            .venueType("JOURNAL")
            .issnL("0028-0836")
            .countryCode("GB")
            .primaryLanguage("eng")
            .foundedYear(1869)
            .coverObjectKey("venue/cover.jpg")
            .isOpenAccess(false)
            .impactFactor(new BigDecimal("69.5000"))
            .jcrQuartile("Q2")
            .jcrSubject("MULTIDISCIPLINARY")
            .casMajorCategory("综合")
            .casMajorQuartile("1区")
            .casIsTop(true)
            .citeScore(new BigDecimal("75.1000"))
            .hIndex(1200)
            .citedByCount(987)
            .worksCount(450000)
            .frequency("Weekly")
            .medlineIndexed(true)
            .oaType("hybrid")
            .apcUsd(11690)
            .isInDoaj(true)
            .jcrRatings(
                List.of(
                    new JcrRatingView(
                        2025,
                        new BigDecimal("69.5000"),
                        "Q2",
                        "MULTIDISCIPLINARY",
                        "1/134",
                        new BigDecimal("99.63")),
                    new JcrRatingView(
                        2024,
                        new BigDecimal("60.1000"),
                        "Q1",
                        "MULTIDISCIPLINARY",
                        "2/135",
                        new BigDecimal("98.89"))))
            .casRatings(
                List.of(
                    new CasRatingView(2025, "base", "综合", "1区", "综合性期刊", "1区", true, false),
                    new CasRatingView(2025, "upgrade", "综合", "2区", "综合性期刊", "2区", false, null),
                    new CasRatingView(2024, "upgrade", "综合", "1区", "综合性期刊", "1区", true, false)))
            .scopusRatings(
                List.of(
                    new ScopusRatingView(
                        2025,
                        new BigDecimal("75.1000"),
                        new BigDecimal("20.3000"),
                        new BigDecimal("9.2000"),
                        "Q1",
                        new BigDecimal("99.90")),
                    new ScopusRatingView(
                        2024,
                        new BigDecimal("70.2000"),
                        new BigDecimal("18.5000"),
                        new BigDecimal("8.1000"),
                        "Q1",
                        new BigDecimal("99.50"))))
            .yearlyStats(
                List.of(
                    YearlyStatView.of(2023, 2900, 85000, 1200),
                    YearlyStatView.of(2024, 3000, 90000, null)))
            .identifiers(
                List.of(
                    IdentifierView.of("ISSN", "0028-0836", true),
                    IdentifierView.of("EISSN", "1476-4687", false)))
            .build();

    assertThat(adapter.findById(id)).contains(expected);
  }

  @Test
  @DisplayName("只有必填列：可空列全为 null、未收录 MEDLINE 为 false、子表为空列表")
  void should_leave_optional_columns_null_when_venue_has_only_required_columns() {
    long id = SnowflakeIdGenerator.getId();
    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, provenance_code)"
            + " VALUES (?, 'JOURNAL', 'Bare', 'OPENALEX')",
        id);

    VenueDetailReadModel expected =
        VenueDetailReadModel.builder()
            .id(id)
            .title("Bare")
            .venueType("JOURNAL")
            .medlineIndexed(false)
            .build();

    assertThat(adapter.findById(id)).contains(expected);
  }

  private static long nextId() {
    return SnowflakeIdGenerator.getId();
  }

  // ===== 测试数据构建助手 =====

  private Long saveJournal(String title, String abbr) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setAbbreviatedTitle(abbr);
    v.setProvenanceCode("OPENALEX");
    em.persist(v);
    return v.getId();
  }

  private void saveJcr(Long venueId, int year, BigDecimal impactFactor, String quartile) {
    JcrRatingEntity r = new JcrRatingEntity();
    r.setId(SnowflakeIdGenerator.getId());
    r.setVenueId(venueId);
    r.setYear((short) year);
    r.setImpactFactor(impactFactor);
    r.setJifQuartile(quartile);
    em.persist(r);
  }
}
