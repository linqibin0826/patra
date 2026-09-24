package dev.linqibin.patra.catalog.infra.adapter.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.portal.FacetCount;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseFacets;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseFilter;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseReadModel;
import dev.linqibin.patra.catalog.domain.model.read.portal.VenueBrowseSort;
import dev.linqibin.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import dev.linqibin.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.entity.CasRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
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
@Import({VenueBrowseReadAdapter.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("VenueBrowseReadAdapter 期刊浏览查询集成测试")
class VenueBrowseReadAdapterIT {

  @Autowired private VenueBrowseReadAdapter adapter;
  @Autowired private TestEntityManager em;

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

  // ===== 测试数据构建助手 =====

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
