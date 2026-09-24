package dev.linqibin.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.FacetCount;
import dev.linqibin.patra.catalog.domain.model.read.portal.PortalPaperReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.PublicationSearchSort;
import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import dev.linqibin.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationAuthorEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationTypeEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `PublicationSearchReadAdapter` 文献检索集成测试：列表组装 / 排序 / 各筛选 / facets drill-down。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PublicationSearchReadAdapter.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("PublicationSearchReadAdapter 文献检索集成测试")
class PublicationSearchReadAdapterIT {

  @Autowired private PublicationSearchReadAdapter adapter;
  @Autowired private TestEntityManager em;

  private static final PagingParams FIRST_PAGE = PagingParams.of(1, 20);
  private static final PublicationSearchFilter NO_FILTER =
      PublicationSearchFilter.builder().build();
  private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T2 = Instant.parse("2026-05-01T00:00:00Z");

  // ===== 任务 7：列表组装与排序 =====

  @Test
  @DisplayName("无筛选：按 last_synced_at 降序，组装 venue / venueId / authors / kind / evidence / snippet")
  void noFilter_assemblesAndSortsByLatest() {
    Long venueId = saveVenue("N Engl J Med");
    Long older = persist(publication("Older paper").venueId(venueId).lastSyncedAt(T1));
    Long newer =
        persist(publication("Newer paper").venueId(venueId).citationCount(5).lastSyncedAt(T2));
    saveAuthors(newer, "Perkovic V.", "Tuttle K. R.");
    savePublicationType(newer, "Journal Article", 1);
    savePublicationType(newer, "Meta-Analysis", 2);
    saveAbstract(newer, "BACKGROUND: CO<sub>2</sub> matters &amp; more.");
    flush();

    PageResult<PortalPaperReadModel> page = adapter.search(NO_FILTER, FIRST_PAGE);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(PortalPaperReadModel::id).containsExactly(newer, older);
    PortalPaperReadModel head = page.items().get(0);
    assertThat(head.venueId()).isEqualTo(venueId);
    assertThat(head.venueName()).isEqualTo("N Engl J Med");
    assertThat(head.authors()).containsExactly("Perkovic V.", "Tuttle K. R.");
    assertThat(head.studyType()).isEqualTo("Journal Article");
    assertThat(head.evidenceLevel()).isEqualTo(EvidenceLevel.SYSTEMATIC_REVIEW);
    assertThat(head.abstractSnippet()).isEqualTo("BACKGROUND: CO2 matters & more.");
    assertThat(head.citationCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("无作者 / 无类型 / 无 venue / 无摘要时降级：空列表 / null / UNKNOWN / null")
  void degradesGracefully() {
    persist(publication("Lonely paper"));
    flush();

    PortalPaperReadModel only = adapter.search(NO_FILTER, FIRST_PAGE).items().get(0);

    assertThat(only.authors()).isEmpty();
    assertThat(only.studyType()).isNull();
    assertThat(only.evidenceLevel()).isEqualTo(EvidenceLevel.UNKNOWN);
    assertThat(only.venueId()).isNull();
    assertThat(only.venueName()).isNull();
    assertThat(only.abstractSnippet()).isNull();
  }

  @Test
  @DisplayName("sort=YEAR 按 publication_year 降序，同年按 last_synced_at 降序，年份 null 垫底")
  void sortByYear() {
    Long y2024 = persist(publication("2024").publicationYear(2024).lastSyncedAt(T2));
    Long y2026old = persist(publication("2026 old").publicationYear(2026).lastSyncedAt(T1));
    Long y2026new = persist(publication("2026 new").publicationYear(2026).lastSyncedAt(T2));
    flush();

    PageResult<PortalPaperReadModel> page =
        adapter.search(
            PublicationSearchFilter.builder().sort(PublicationSearchSort.YEAR).build(), FIRST_PAGE);

    assertThat(page.items())
        .extracting(PortalPaperReadModel::id)
        .containsExactly(y2026new, y2026old, y2024);
  }

  @Test
  @DisplayName("sort=CITED 按 citation_count 降序；同被引按 id 降序，不看采集时间（与原 feed 一致）")
  void sortByCited() {
    Long low = persist(publication("Low").citationCount(10).lastSyncedAt(T2));
    Long high = persist(publication("High").citationCount(999).lastSyncedAt(T1));
    Long tieOlderIdNewerTime = persist(publication("tie-1").citationCount(50).lastSyncedAt(T2));
    Long tieNewerIdOlderTime = persist(publication("tie-2").citationCount(50).lastSyncedAt(T1));
    flush();

    PageResult<PortalPaperReadModel> page =
        adapter.search(
            PublicationSearchFilter.builder().sort(PublicationSearchSort.CITED).build(),
            FIRST_PAGE);

    assertThat(page.items())
        .extracting(PortalPaperReadModel::id)
        .containsExactly(high, tieNewerIdOlderTime, tieOlderIdNewerTime, low);
  }

  @Test
  @DisplayName("分页：pageSize=1 第二页拿到第二条，total 仍为 2")
  void paginates() {
    Long first = persist(publication("first").lastSyncedAt(T2));
    Long second = persist(publication("second").lastSyncedAt(T1));
    flush();

    PageResult<PortalPaperReadModel> page = adapter.search(NO_FILTER, PagingParams.of(2, 1));

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(PortalPaperReadModel::id).containsExactly(second);
    assertThat(first).isNotNull();
  }

  @Test
  @DisplayName("摘要片段：先取全文再去 annotation / 剥标签 / 解实体，最后截 300 码点；超长 annotation 不露出；纯空白为 null")
  void abstractSnippetDerivation() {
    Long withMath = persist(publication("math").lastSyncedAt(T2));
    saveAbstract(
        withMath,
        "<math><semantics><mi>x</mi><annotation encoding=\"application/x-tex\">"
            + "L".repeat(2500) // 超过任何"预截断"长度：若 SQL 先 left() 再清理，注释会被切开并露出 LLLL…
            + "</annotation></semantics></math> "
            + "a".repeat(400));
    Long blank = persist(publication("blank").lastSyncedAt(T1));
    saveAbstract(blank, "   ");
    flush();

    List<PortalPaperReadModel> items = adapter.search(NO_FILTER, FIRST_PAGE).items();

    assertThat(items.get(0).abstractSnippet())
        .hasSize(300)
        .startsWith("x aaaa")
        .doesNotContain("L");
    assertThat(items.get(1).abstractSnippet()).isNull();
  }

  // ===== fixtures =====

  private void flush() {
    em.flush();
    em.clear();
  }

  private Long saveVenue(String title) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setProvenanceCode("OPENALEX");
    v.setCountryCode("US");
    em.persist(v);
    return v.getId();
  }

  /// 带默认值的文献 builder：2026 年、PUBMED、非 OA、0 被引、T2 采集；测试按需覆盖。
  private PublicationEntity.PublicationEntityBuilder<?, ?> publication(String title) {
    return PublicationEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .provenanceCode("PUBMED")
        .title(title)
        .publicationYear(2026)
        .venueInstanceId(SnowflakeIdGenerator.getId())
        .isOa(false)
        .authorsComplete(true)
        .citationCount(0)
        .lastSyncedAt(T2);
  }

  private Long persist(PublicationEntity.PublicationEntityBuilder<?, ?> builder) {
    PublicationEntity p = builder.build();
    em.persist(p);
    return p.getId();
  }

  /// 只写 cat_publication_author 快照行（author_id 为 null），倒序插入以验证 ORDER BY author_order。
  private void saveAuthors(Long publicationId, String... displayNames) {
    for (int i = displayNames.length - 1; i >= 0; i--) {
      em.persist(
          PublicationAuthorEntity.builder()
              .id(SnowflakeIdGenerator.getId())
              .publicationId(publicationId)
              .displayName(displayNames[i])
              .authorOrder(i + 1)
              .build());
    }
  }

  private void savePublicationType(Long publicationId, String typeValue, int order) {
    em.persist(
        PublicationTypeEntity.builder()
            .id(SnowflakeIdGenerator.getId())
            .publicationId(publicationId)
            .typeValue(typeValue)
            .typeOrder(order)
            .build());
  }

  private void saveAbstract(Long publicationId, String plainText) {
    PublicationAbstractEntity a = new PublicationAbstractEntity();
    a.setId(SnowflakeIdGenerator.getId());
    a.setPublicationId(publicationId);
    a.setPlainText(plainText);
    em.persist(a);
  }

  private static List<String> values(List<FacetCount> facets) {
    return facets.stream().map(FacetCount::value).toList();
  }

  private static long countOf(List<FacetCount> facets, String value) {
    return facets.stream()
        .filter(f -> f.value().equals(value))
        .mapToLong(FacetCount::count)
        .findFirst()
        .orElse(-1L);
  }
}
