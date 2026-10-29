package dev.patra.catalog.infra.portal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.read.FacetCount;
import dev.patra.catalog.domain.portal.model.read.PageResult;
import dev.patra.catalog.domain.portal.model.read.PagingParams;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFacets;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseFilter;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import dev.patra.catalog.domain.portal.model.read.VenueBrowseSort;
import dev.patra.catalog.domain.venue.model.vo.OpenAccessInfo;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.venue.persistence.entity.CasRatingEntity;
import dev.patra.catalog.infra.venue.persistence.entity.JcrRatingEntity;
import dev.patra.catalog.infra.venue.persistence.entity.VenueEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.util.List;
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

/// `VenueBrowseReadAdapter` 期刊浏览/检索集成测试。
///
/// 覆盖多值筛选（subjects/jcrQuartiles/casQuartiles/countryCodes）、
/// 布尔筛选（isOpenAccess/doaj/casTop）及 facets drill-down 聚合语义。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({VenueBrowseReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("VenueBrowseReadAdapter 期刊浏览查询集成测试")
class VenueBrowseReadAdapterIT {

  @Autowired private VenueBrowseReadAdapter adapter;
  @Autowired private TestEntityManager em;
  @Autowired private JdbcTemplate jdbc;

  private static final PagingParams FIRST_PAGE = PagingParams.of(1, 20);

  // ===== 排序测试 =====

  @Test
  @DisplayName("sort=TITLE 按期刊全称字母升序")
  void shouldSortByTitle() {
    saveJournal("Zoo Journal", "ZJ", null, null, null);
    saveJournal("Alpha Journal", "AJ", null, null, null);
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().sort(VenueBrowseSort.TITLE).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items())
        .extracting(VenueBrowseReadModel::name)
        .containsExactly("Alpha Journal", "Zoo Journal");
  }

  @Test
  @DisplayName("sort=CAS_QUARTILE Q1 在 Q2 之前")
  void shouldSortByCasQuartileAscending() {
    Long q2 = saveJournal("B Journal", "BJ", null, null, null);
    saveCas(q2, 2024, "Q2");
    Long q1 = saveJournal("A Journal", "AJ", null, null, null);
    saveCas(q1, 2024, "Q1");
    em.flush();
    em.clear();

    VenueBrowseFilter filter =
        VenueBrowseFilter.builder().sort(VenueBrowseSort.CAS_QUARTILE).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items()).extracting(VenueBrowseReadModel::id).containsExactly(q1, q2);
  }

  // ===== keyword 过滤 =====

  @Test
  @DisplayName("keyword 前缀命中：'Nature' 匹配 'Nature Medicine'，不匹配 'Cell'")
  void shouldFilterByKeywordPrefix() {
    saveJournal("Nature Medicine", "NM", null, null, null);
    saveJournal("Cell", "Cell", null, null, null);
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().keyword("Nature").build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items())
        .extracting(VenueBrowseReadModel::name)
        .containsExactly("Nature Medicine");
  }

  @Test
  @DisplayName("keyword 含 '%' 被当作字面量处理")
  void shouldTreatPercentInKeywordAsLiteral() {
    saveJournal("100% Health", "100H", null, null, null);
    saveJournal("1000 Health", "1000H", null, null, null);
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().keyword("100%").build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items())
        .extracting(VenueBrowseReadModel::name)
        .containsExactly("100% Health");
  }

  // ===== 多值 subjects 过滤 =====

  @Test
  @DisplayName("subjects=[ONCOLOGY,CARDIOLOGY] OR 命中两条记录")
  void shouldFilterByMultipleSubjects() {
    Long oncology = saveJournal("Oncology Journal", "OJ", null, null, null);
    saveJcr(oncology, 2024, new BigDecimal("10.0"), "Q1", "ONCOLOGY");
    Long cardio = saveJournal("Cardiology Journal", "CJ", null, null, null);
    saveJcr(cardio, 2024, new BigDecimal("8.0"), "Q2", "CARDIOLOGY");
    Long neuro = saveJournal("Neurology Journal", "NJ", null, null, null);
    saveJcr(neuro, 2024, new BigDecimal("5.0"), "Q3", "NEUROLOGY");
    em.flush();
    em.clear();

    VenueBrowseFilter filter =
        VenueBrowseFilter.builder().subjects(List.of("ONCOLOGY", "CARDIOLOGY")).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items())
        .extracting(VenueBrowseReadModel::id)
        .containsExactlyInAnyOrder(oncology, cardio);
  }

  @Test
  @DisplayName("subjects=[] 空列表不过滤，返回全部")
  void shouldNotFilterWhenSubjectsEmpty() {
    Long j1 = saveJournal("Journal A", "JA", null, null, null);
    saveJcr(j1, 2024, new BigDecimal("5.0"), "Q1", "MEDICINE");
    Long j2 = saveJournal("Journal B", "JB", null, null, null);
    saveJcr(j2, 2024, new BigDecimal("3.0"), "Q2", "BIOLOGY");
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().subjects(List.of()).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.total()).isEqualTo(2);
  }

  // ===== 多值 jcrQuartiles 过滤 =====

  @Test
  @DisplayName("jcrQuartiles=[Q1,Q2] OR 命中，Q3 不命中")
  void shouldFilterByMultipleJcrQuartiles() {
    Long q1j = saveJournal("Q1 Journal", "Q1J", null, null, null);
    saveJcr(q1j, 2024, new BigDecimal("15.0"), "Q1", "MEDICINE");
    Long q2j = saveJournal("Q2 Journal", "Q2J", null, null, null);
    saveJcr(q2j, 2024, new BigDecimal("8.0"), "Q2", "MEDICINE");
    Long q3j = saveJournal("Q3 Journal", "Q3J", null, null, null);
    saveJcr(q3j, 2024, new BigDecimal("3.0"), "Q3", "MEDICINE");
    em.flush();
    em.clear();

    VenueBrowseFilter filter =
        VenueBrowseFilter.builder().jcrQuartiles(List.of("Q1", "Q2")).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items())
        .extracting(VenueBrowseReadModel::id)
        .containsExactlyInAnyOrder(q1j, q2j);
  }

  // ===== 多值 casQuartiles 过滤 =====

  @Test
  @DisplayName("casQuartiles=[Q1] 只命中 CAS 一区")
  void shouldFilterByCasQuartiles() {
    Long cas1 = saveJournal("CAS1 Journal", "C1J", null, null, null);
    saveCas(cas1, 2024, "Q1");
    Long cas2 = saveJournal("CAS2 Journal", "C2J", null, null, null);
    saveCas(cas2, 2024, "Q2");
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().casQuartiles(List.of("Q1")).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items()).extracting(VenueBrowseReadModel::id).containsExactly(cas1);
  }

  // ===== 多值 countryCodes 过滤 =====

  @Test
  @DisplayName("countryCodes=[US,GB] OR 命中，CN 不命中")
  void shouldFilterByMultipleCountryCodes() {
    Long us = saveJournal("US Journal", "USJ", "US", null, null);
    Long gb = saveJournal("GB Journal", "GBJ", "GB", null, null);
    saveJournal("CN Journal", "CNJ", "CN", null, null);
    em.flush();
    em.clear();

    VenueBrowseFilter filter =
        VenueBrowseFilter.builder().countryCodes(List.of("US", "GB")).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items())
        .extracting(VenueBrowseReadModel::id)
        .containsExactlyInAnyOrder(us, gb);
  }

  // ===== 布尔过滤 =====

  @Test
  @DisplayName("isOpenAccess=true 只返回 OA 期刊")
  void shouldFilterByIsOpenAccess() {
    Long oa = saveJournal("OA Journal", "OAJ", null, true, null);
    saveJournal("Closed Journal", "CLJ", null, false, null);
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().isOpenAccess(true).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items()).extracting(VenueBrowseReadModel::id).containsExactly(oa);
  }

  @Test
  @DisplayName("doaj=true 只返回收录于 DOAJ 的期刊")
  void shouldFilterByDoaj() {
    Long doajJ = saveJournal("DOAJ Journal", "DOAJJ", null, null, true);
    saveJournal("Non-DOAJ Journal", "NDJ", null, null, false);
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().doaj(true).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items()).extracting(VenueBrowseReadModel::id).containsExactly(doajJ);
  }

  @Test
  @DisplayName("casTop=true 只返回 CAS 顶刊")
  void shouldFilterByCasTop() {
    Long topJ = saveJournal("Top Journal", "TJ", null, null, null);
    saveCasWithTop(topJ, 2024, "Q1", true);
    Long normalJ = saveJournal("Normal Journal", "NJ", null, null, null);
    saveCasWithTop(normalJ, 2024, "Q2", false);
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().casTop(true).build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, FIRST_PAGE);

    assertThat(result.items()).extracting(VenueBrowseReadModel::id).containsExactly(topJ);
  }

  // ===== 分页 =====

  @Test
  @DisplayName("分页：total/items/page 字段正确")
  void shouldReturnCorrectPagination() {
    for (int i = 1; i <= 5; i++) {
      saveJournal("Journal " + i, "J" + i, null, null, null);
    }
    em.flush();
    em.clear();

    PagingParams paging = PagingParams.of(2, 2);
    VenueBrowseFilter filter = VenueBrowseFilter.builder().build();
    PageResult<VenueBrowseReadModel> result = adapter.search(filter, paging);

    assertThat(result.total()).isEqualTo(5);
    assertThat(result.items()).hasSize(2);
    assertThat(result.page()).isEqualTo(2);
  }

  // ===== facets drill-down 测试 =====

  @Test
  @DisplayName("空库时所有 facet 列表为空，布尔计数为 0")
  void facets_returns_empty_when_no_data() {
    VenueBrowseFilter filter = VenueBrowseFilter.builder().build();
    VenueBrowseFacets facets = adapter.facets(filter);

    assertThat(facets.subjects()).isEmpty();
    assertThat(facets.jcrQuartiles()).isEmpty();
    assertThat(facets.casQuartiles()).isEmpty();
    assertThat(facets.countries()).isEmpty();
    assertThat(facets.casTop()).isZero();
    assertThat(facets.openAccess()).isZero();
    assertThat(facets.doaj()).isZero();
  }

  @Test
  @DisplayName("facets 返回各维度可选值及计数")
  void facets_returns_dimension_counts() {
    Long j1 = saveJournal("J1", "J1", "US", true, false);
    saveJcr(j1, 2024, new BigDecimal("10.0"), "Q1", "MEDICINE");
    saveCasWithTop(j1, 2024, "Q1", true);

    Long j2 = saveJournal("J2", "J2", "CN", false, true);
    saveJcr(j2, 2024, new BigDecimal("5.0"), "Q2", "BIOLOGY");
    saveCas(j2, 2024, "Q2");

    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().build();
    VenueBrowseFacets facets = adapter.facets(filter);

    assertThat(facets.subjects())
        .extracting(FacetCount::value)
        .containsExactlyInAnyOrder("MEDICINE", "BIOLOGY");
    assertThat(facets.jcrQuartiles())
        .extracting(FacetCount::value)
        .containsExactlyInAnyOrder("Q1", "Q2");
    assertThat(facets.casQuartiles())
        .extracting(FacetCount::value)
        .containsExactlyInAnyOrder("Q1", "Q2");
    assertThat(facets.countries())
        .extracting(FacetCount::value)
        .containsExactlyInAnyOrder("US", "CN");
    assertThat(facets.casTop()).isEqualTo(1);
    assertThat(facets.openAccess()).isEqualTo(1);
    assertThat(facets.doaj()).isEqualTo(1);
  }

  @Test
  @DisplayName("drill-down: 选 jcrQuartiles=[Q1] 后 facets().jcrQuartiles 仍含 Q2/Q3（忽略自身维度）")
  void facets_jcrQuartiles_ignores_own_dimension_filter() {
    Long j1 = saveJournal("J1", "J1", "US", null, null);
    saveJcr(j1, 2024, new BigDecimal("15.0"), "Q1", "MEDICINE");

    Long j2 = saveJournal("J2", "J2", "CN", null, null);
    saveJcr(j2, 2024, new BigDecimal("8.0"), "Q2", "MEDICINE");

    Long j3 = saveJournal("J3", "J3", "GB", null, null);
    saveJcr(j3, 2024, new BigDecimal("3.0"), "Q3", "MEDICINE");

    em.flush();
    em.clear();

    // 选了 Q1，但 jcrQuartiles facet 仍应包含 Q2/Q3
    VenueBrowseFilter filter = VenueBrowseFilter.builder().jcrQuartiles(List.of("Q1")).build();
    VenueBrowseFacets facets = adapter.facets(filter);

    assertThat(facets.jcrQuartiles())
        .extracting(FacetCount::value)
        .containsExactlyInAnyOrder("Q1", "Q2", "Q3");
  }

  @Test
  @DisplayName("drill-down: casQuartiles facet 受 jcrQuartiles=Q1 约束")
  void facets_casQuartiles_constrained_by_jcr_filter() {
    // j1: JCR Q1 + CAS Q1
    Long j1 = saveJournal("J1", "J1", null, null, null);
    saveJcr(j1, 2024, new BigDecimal("15.0"), "Q1", "MEDICINE");
    saveCas(j1, 2024, "Q1");

    // j2: JCR Q2 + CAS Q2 -- jcr=Q1 过滤后此条不应出现在 casQuartiles facet
    Long j2 = saveJournal("J2", "J2", null, null, null);
    saveJcr(j2, 2024, new BigDecimal("8.0"), "Q2", "MEDICINE");
    saveCas(j2, 2024, "Q2");

    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().jcrQuartiles(List.of("Q1")).build();
    VenueBrowseFacets facets = adapter.facets(filter);

    // casQuartiles 受 jcr=Q1 约束，只有 Q1
    assertThat(facets.casQuartiles()).extracting(FacetCount::value).containsExactly("Q1");
    // jcrQuartiles 忽略自身，仍含 Q1 和 Q2
    assertThat(facets.jcrQuartiles())
        .extracting(FacetCount::value)
        .containsExactlyInAnyOrder("Q1", "Q2");
  }

  @Test
  @DisplayName("drill-down: keyword 约束所有维度 facet 计数")
  void facets_all_dimensions_constrained_by_keyword() {
    Long j1 = saveJournal("Nature Medicine", "NM", "US", null, null);
    saveJcr(j1, 2024, new BigDecimal("15.0"), "Q1", "MEDICINE");

    Long j2 = saveJournal("Cell Biology", "CB", "CN", null, null);
    saveJcr(j2, 2024, new BigDecimal("8.0"), "Q1", "BIOLOGY");

    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().keyword("Nature").build();
    VenueBrowseFacets facets = adapter.facets(filter);

    // keyword 过滤后只有 j1，所以 subjects 只有 MEDICINE，countries 只有 US
    assertThat(facets.subjects()).extracting(FacetCount::value).containsExactly("MEDICINE");
    assertThat(facets.countries()).extracting(FacetCount::value).containsExactly("US");
  }

  @Test
  @DisplayName("facets 只返回 present-options（值为 null 的行不出现）")
  void facets_only_present_options() {
    // j1 无 JCR 评级 -> jcr_quartile IS NULL，不应出现在 jcrQuartiles facet
    saveJournal("J1", "J1", null, null, null);
    Long j2 = saveJournal("J2", "J2", null, null, null);
    saveJcr(j2, 2024, new BigDecimal("5.0"), "Q2", "BIOLOGY");
    em.flush();
    em.clear();

    VenueBrowseFilter filter = VenueBrowseFilter.builder().build();
    VenueBrowseFacets facets = adapter.facets(filter);

    // j1 无 JCR，所以 jcrQuartiles 中应只有 Q2（1条），不含 null
    assertThat(facets.jcrQuartiles()).hasSize(1);
    assertThat(facets.jcrQuartiles().get(0).value()).isEqualTo("Q2");
    assertThat(facets.jcrQuartiles().get(0).count()).isEqualTo(1);
  }

  // ===== 按 ID 批量读卡片（PAP-117） =====

  @Test
  @DisplayName("findCardsByIds：只返回存在且未软删的期刊，带主 NLM 与最新年 JCR；非期刊、不存在的不返回")
  void should_return_cards_by_ids_excluding_soft_deleted_and_non_journals() {
    Long swiss = saveJournalWithNlm("Schweizerische medizinische Wochenschrift", "0404401");
    Long lancet = saveJournalWithNlm("Lancet (London, England)", "2985213R");
    Long gone = saveJournalWithNlm("Gone Journal", "0000003R");
    Long repository = saveVenueOfType("REPOSITORY", "Some repository");
    saveJcr(lancet, 2023, new BigDecimal("98.4"), "Q1", "MEDICINE, GENERAL & INTERNAL");
    saveJcr(lancet, 2024, new BigDecimal("88.5"), "Q1", "MEDICINE, GENERAL & INTERNAL");
    em.flush();
    softDelete(gone);
    em.clear();

    List<VenueBrowseReadModel> cards =
        adapter.findCardsByIds(List.of(swiss, lancet, gone, repository, 999L));

    assertThat(cards).extracting(VenueBrowseReadModel::id).containsExactlyInAnyOrder(swiss, lancet);
    assertThat(cards)
        .filteredOn(c -> c.id().equals(lancet))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.nlmId()).isEqualTo("2985213R");
              assertThat(c.impactFactor()).isEqualByComparingTo("88.5");
            });
    assertThat(cards)
        .filteredOn(c -> c.id().equals(swiss))
        .singleElement()
        .extracting(VenueBrowseReadModel::nlmId)
        .isEqualTo("0404401");
  }

  @Test
  @DisplayName("findCardsByIds：空入参返回空列表")
  void should_return_no_cards_for_empty_ids() {
    assertThat(adapter.findCardsByIds(List.of())).isEmpty();
  }

  @Test
  @DisplayName("浏览结果的卡片带主 NLM")
  void browse_items_carry_primary_nlm() {
    saveJournalWithNlm("Alpha Journal", "0404401");
    em.flush();
    em.clear();

    PageResult<VenueBrowseReadModel> result =
        adapter.search(VenueBrowseFilter.builder().sort(VenueBrowseSort.TITLE).build(), FIRST_PAGE);

    assertThat(result.items()).extracting(VenueBrowseReadModel::nlmId).containsExactly("0404401");
  }

  @Test
  @DisplayName("分页边界：末页不满一页与越过末页时 total 不变，越过末页 items 为空")
  void should_keep_total_on_partial_and_out_of_range_pages() {
    for (int i = 1; i <= 5; i++) {
      saveJournal("Journal " + i, "J" + i, null, null, null);
    }
    em.flush();
    em.clear();
    VenueBrowseFilter filter = VenueBrowseFilter.builder().sort(VenueBrowseSort.TITLE).build();

    PageResult<VenueBrowseReadModel> last = adapter.search(filter, PagingParams.of(3, 2));
    PageResult<VenueBrowseReadModel> beyond = adapter.search(filter, PagingParams.of(4, 2));

    assertThat(last.items()).extracting(VenueBrowseReadModel::name).containsExactly("Journal 5");
    assertThat(last.total()).isEqualTo(5);
    assertThat(last.totalPages()).isEqualTo(3);
    assertThat(beyond.items()).isEmpty();
    assertThat(beyond.total()).isEqualTo(5);
  }

  @Test
  @DisplayName("全字段卡片：检索与按 ID 读卡片的每一列都按列读出；最新年 CAS 取版本升序第一条")
  void should_map_every_card_column_in_search_and_find_cards() {
    long id = SnowflakeIdGenerator.getId();
    jdbc.update(
        """
        INSERT INTO cat_venue (id, venue_type, title, provenance_code, issn_l, nlm_id,
          abbreviated_title, country_code, image_object_key, cited_by_count,
          publication_profile, open_access)
        VALUES (?, 'JOURNAL', 'Nature', 'OPENALEX', '0028-0836', '0410462', 'Nat', 'GB',
          'venue/cover.jpg', 987, '{"publicationHistory":{"startYear":1869}}',
          '{"isOa":false,"isInDoaj":true}')
        """,
        id);
    jdbc.update(
        "INSERT INTO cat_venue_jcr_rating (id, venue_id, year, impact_factor, jif_quartile, subject)"
            + " VALUES (?, ?, 2025, 69.5, 'Q1', 'MULTIDISCIPLINARY')",
        SnowflakeIdGenerator.getId(),
        id);
    String cas =
        "INSERT INTO cat_venue_cas_rating (id, venue_id, year, edition, major_category,"
            + " major_quartile, is_top_journal) VALUES (?, ?, 2025, ?, '综合', ?, ?)";
    jdbc.update(cas, SnowflakeIdGenerator.getId(), id, "upgrade", "2区", false);
    jdbc.update(cas, SnowflakeIdGenerator.getId(), id, "base", "1区", true);
    long bare = SnowflakeIdGenerator.getId();
    jdbc.update(
        "INSERT INTO cat_venue (id, venue_type, title, provenance_code)"
            + " VALUES (?, 'JOURNAL', 'Zeta', 'OPENALEX')",
        bare);

    VenueBrowseReadModel full =
        VenueBrowseReadModel.builder()
            .id(id)
            .name("Nature")
            .abbr("Nat")
            .coverObjectKey("venue/cover.jpg")
            .impactFactor(new BigDecimal("69.5000"))
            .jcrQuartile("Q1")
            .jcrSubject("MULTIDISCIPLINARY")
            .casMajorCategory("综合")
            .casMajorQuartile("1区")
            .casIsTop(true)
            .countryCode("GB")
            .citedByCount(987)
            .foundedYear(1869)
            .isOpenAccess(false)
            .isInDoaj(true)
            .issnL("0028-0836")
            .nlmId("0410462")
            .build();
    VenueBrowseReadModel empty = VenueBrowseReadModel.builder().id(bare).name("Zeta").build();

    PageResult<VenueBrowseReadModel> page =
        adapter.search(VenueBrowseFilter.builder().sort(VenueBrowseSort.TITLE).build(), FIRST_PAGE);

    assertThat(page.items()).containsExactly(full, empty);
    assertThat(adapter.findCardsByIds(List.of(id, bare))).containsExactlyInAnyOrder(full, empty);
  }

  @Test
  @DisplayName("所有筛选同时给值：检索、facets 各维度与三个布尔计数都只数命中的那本")
  void should_apply_every_filter_at_once() {
    Long hit = saveJournal("Nature Medicine", "Nat Med", "US", true, true);
    saveJcr(hit, 2025, new BigDecimal("82.9"), "Q1", "MEDICINE");
    saveCasWithTop(hit, 2025, "1区", true);
    Long miss = saveJournal("Nature Plants", "Nat Plants", "US", true, true);
    saveJcr(miss, 2025, new BigDecimal("15.8"), "Q1", "PLANT SCIENCES");
    saveCasWithTop(miss, 2025, "1区", true);
    em.flush();
    em.clear();
    VenueBrowseFilter filter =
        VenueBrowseFilter.builder()
            .keyword("Nature")
            .sort(VenueBrowseSort.CITED_BY)
            .subjects(List.of("MEDICINE"))
            .jcrQuartiles(List.of("Q1"))
            .casQuartiles(List.of("1区"))
            .casTop(true)
            .isOpenAccess(true)
            .doaj(true)
            .countryCodes(List.of("US"))
            .build();

    PageResult<VenueBrowseReadModel> page = adapter.search(filter, FIRST_PAGE);
    VenueBrowseFacets facets = adapter.facets(filter);

    assertThat(page.items()).extracting(VenueBrowseReadModel::id).containsExactly(hit);
    assertThat(page.total()).isEqualTo(1);
    assertThat(facets.subjects())
        .containsExactlyInAnyOrder(
            FacetCount.of("MEDICINE", 1), FacetCount.of("PLANT SCIENCES", 1));
    assertThat(facets.jcrQuartiles()).containsExactly(FacetCount.of("Q1", 1));
    assertThat(facets.casQuartiles()).containsExactly(FacetCount.of("1区", 1));
    assertThat(facets.countries()).containsExactly(FacetCount.of("US", 1));
    assertThat(facets.casTop()).isEqualTo(1);
    assertThat(facets.openAccess()).isEqualTo(1);
    assertThat(facets.doaj()).isEqualTo(1);
  }

  // ===== 测试数据构建助手 =====

  /// 保存带主 NLM 的期刊。
  ///
  /// @param title 期刊全称
  /// @param nlmId 主 NLM
  /// @return 期刊 ID
  private Long saveJournalWithNlm(String title, String nlmId) {
    Long id = saveJournal(title, null, null, null, null);
    em.find(VenueEntity.class, id).setNlmId(nlmId);
    return id;
  }

  /// 保存指定类型的载体（非期刊类型用）。
  ///
  /// @param type 载体类型
  /// @param title 全称
  /// @return 载体 ID
  private Long saveVenueOfType(String type, String title) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType(type);
    v.setTitle(title);
    v.setProvenanceCode("OPENALEX");
    em.persist(v);
    return v.getId();
  }

  /// 软删一个载体：实体走 Hibernate `@SoftDelete` 会被过滤，测试直接改列。
  ///
  /// @param id 载体 ID
  private void softDelete(Long id) {
    em.getEntityManager()
        .createNativeQuery("UPDATE cat_venue SET deleted_at = now() WHERE id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }

  private Long saveJournal(
      String title, String abbr, String countryCode, Boolean isOa, Boolean isInDoaj) {
    VenueEntity v = new VenueEntity();
    v.setId(SnowflakeIdGenerator.getId());
    v.setVenueType("JOURNAL");
    v.setTitle(title);
    v.setAbbreviatedTitle(abbr);
    v.setProvenanceCode("OPENALEX");
    if (countryCode != null) {
      v.setCountryCode(countryCode);
    }
    if (isOa != null || isInDoaj != null) {
      v.setOpenAccess(
          OpenAccessInfo.ofOaStatus(
              Boolean.TRUE.equals(isOa), Boolean.TRUE.equals(isInDoaj), null));
    }
    em.persist(v);
    return v.getId();
  }

  private void saveJcr(
      Long venueId, int year, BigDecimal impactFactor, String quartile, String subject) {
    JcrRatingEntity r = new JcrRatingEntity();
    r.setId(SnowflakeIdGenerator.getId());
    r.setVenueId(venueId);
    r.setYear((short) year);
    r.setImpactFactor(impactFactor);
    r.setJifQuartile(quartile);
    r.setSubject(subject);
    em.persist(r);
  }

  private void saveCas(Long venueId, int year, String majorQuartile) {
    saveCasWithTop(venueId, year, majorQuartile, null);
  }

  private void saveCasWithTop(Long venueId, int year, String majorQuartile, Boolean isTop) {
    CasRatingEntity r = new CasRatingEntity();
    r.setId(SnowflakeIdGenerator.getId());
    r.setVenueId(venueId);
    r.setYear((short) year);
    r.setEdition("2024");
    r.setMajorQuartile(majorQuartile);
    if (isTop != null) {
      r.setIsTopJournal(isTop);
    }
    em.persist(r);
  }
}
