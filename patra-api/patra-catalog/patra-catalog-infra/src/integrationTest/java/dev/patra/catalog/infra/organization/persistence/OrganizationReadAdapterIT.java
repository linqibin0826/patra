package dev.patra.catalog.infra.organization.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.organization.persistence.entity.OrganizationEntity;
import dev.patra.catalog.infra.organization.persistence.entity.OrganizationLocationEntity;
import dev.patra.catalog.infra.organization.persistence.entity.OrganizationNameEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// `OrganizationReadAdapter` 集成测试：在真实 PostgreSQL（Flyway 跑到 `V9`）上验证匹配、排序、
/// 去重、状态过滤、别名表与按 ROR ID 查询。
///
/// ROR ID 一律用 `0aaaaaaaN` 这种虚构形状，避免撞上种子文件里的真实 ID。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrganizationReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("OrganizationReadAdapter 机构建议与查询集成测试")
class OrganizationReadAdapterIT {

  private static final Set<String> HEALTHCARE = Set.of("healthcare");
  private static final Set<String> EDUCATION = Set.of("education");

  @Autowired private OrganizationReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("「协和」子串命中，医院排在学院前")
  void should_match_chinese_substring_and_rank_hospitals_before_colleges() {
    saveChineseHospital("0aaaaaaa1", "Peking Union Medical College Hospital", "北京协和医院", "Beijing");
    saveChineseHospital("0aaaaaaa2", "Wuhan Union Hospital", "武汉协和医院", "Wuhan");
    Long college = saveOrganization("0aaaaaaa3", "Shandong Xiehe University", "active", EDUCATION);
    saveName(college, "Shandong Xiehe University", "ror_display", "en");
    saveName(college, "山东协和学院", "label", "zh");
    saveLocation(college, "CN", "Jinan");
    flush();

    List<OrganizationSuggestion> items = adapter.suggest(SuggestQuery.of("协和", 10));

    assertThat(items)
        .extracting(s -> s.organization().rorId())
        .containsExactly("0aaaaaaa1", "0aaaaaaa2", "0aaaaaaa3");
    assertThat(items.getFirst().matchedName()).isEqualTo("北京协和医院");
    assertThat(items.getFirst().organization().zhName()).isEqualTo("北京协和医院");
  }

  @Test
  @DisplayName("同样精确命中时中国机构排前面")
  void should_rank_china_before_other_countries() {
    Long us = saveOrganization("0aaaaaaa1", "Union Hospital", "active", HEALTHCARE);
    saveName(us, "Union Hospital", "ror_display", "en");
    saveLocation(us, "US", "Terre Haute");
    Long cn = saveOrganization("0aaaaaaa2", "Union Hospital", "active", HEALTHCARE);
    saveName(cn, "Union Hospital", "ror_display", "en");
    saveLocation(cn, "CN", "Fuzhou");
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Union Hospital", 10)))
        .extracting(s -> s.organization().rorId())
        .containsExactly("0aaaaaaa2", "0aaaaaaa1");
  }

  @Test
  @DisplayName("精确 > 前缀 > 词首")
  void should_rank_exact_above_prefix_above_word_start() {
    saveUsOrganization("0aaaaaaa1", "Mayo");
    saveUsOrganization("0aaaaaaa2", "Mayo Clinic");
    saveUsOrganization("0aaaaaaa3", "Clinica Mayo");
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Mayo", 10)))
        .extracting(OrganizationSuggestion::matchedName)
        .containsExactly("Mayo", "Mayo Clinic", "Clinica Mayo");
  }

  @Test
  @DisplayName("缩写命中时 matchedName 是缩写，zhName 仍是中文名")
  void should_match_acronym_and_return_it_as_matched_name() {
    Long pumch =
        saveChineseHospital(
            "0aaaaaaa1", "Peking Union Medical College Hospital", "北京协和医院", "Beijing");
    saveName(pumch, "PUMCH", "acronym", null);
    flush();

    OrganizationSuggestion suggestion = adapter.suggest(SuggestQuery.of("PUMCH", 10)).getFirst();

    assertThat(suggestion.matchedName()).isEqualTo("PUMCH");
    assertThat(suggestion.organization().zhName()).isEqualTo("北京协和医院");
  }

  @Test
  @DisplayName("拉丁查询不命中词中间")
  void should_not_match_inside_a_word_for_latin_query() {
    saveUsOrganization("0aaaaaaa1", "Mayo Clinic");
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("ayo", 10))).isEmpty();
  }

  @Test
  @DisplayName("一家机构多条名称命中只出现一次，取最好的那条")
  void should_return_each_organization_once_with_its_best_name() {
    Long pku = saveOrganization("0aaaaaaa1", "Peking University", "active", EDUCATION);
    saveName(pku, "Peking University", "ror_display", "en");
    saveName(pku, "Peking Univ", "alias", null);
    saveLocation(pku, "CN", "Beijing");
    flush();

    List<OrganizationSuggestion> items = adapter.suggest(SuggestQuery.of("Peking", 10));

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().matchedName()).isEqualTo("Peking University");
  }

  @Test
  @DisplayName("inactive、withdrawn、软删除的机构不出现")
  void should_exclude_inactive_withdrawn_and_soft_deleted() {
    Long inactive = saveOrganization("0aaaaaaa1", "Ghost Institute A", "inactive", EDUCATION);
    saveName(inactive, "Ghost Institute A", "ror_display", "en");
    Long withdrawn = saveOrganization("0aaaaaaa2", "Ghost Institute B", "withdrawn", EDUCATION);
    saveName(withdrawn, "Ghost Institute B", "ror_display", "en");
    Long deleted = saveOrganization("0aaaaaaa3", "Ghost Institute C", "active", EDUCATION);
    saveName(deleted, "Ghost Institute C", "ror_display", "en");
    flush();
    softDelete(deleted);

    assertThat(adapter.suggest(SuggestQuery.of("Ghost", 10))).isEmpty();
  }

  @Test
  @DisplayName("zhName 取 label 不取 alias")
  void should_prefer_zh_label_over_zh_alias_for_zh_name() {
    Long cams =
        saveOrganization(
            "0aaaaaaa1",
            "Chinese Academy of Medical Sciences & Peking Union Medical College",
            "active",
            EDUCATION);
    saveName(
        cams,
        "Chinese Academy of Medical Sciences & Peking Union Medical College",
        "ror_display",
        "en");
    saveName(cams, "北京协和医学院", "alias", "zh");
    saveName(cams, "中国医学科学院北京协和医学院", "label", "zh");
    saveLocation(cams, "CN", "Beijing");
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("协和医学院", 10)).getFirst().organization().zhName())
        .isEqualTo("中国医学科学院北京协和医学院");
  }

  @Test
  @DisplayName("查询里的 % 与 _ 当字面量")
  void should_treat_like_metacharacters_as_literals() {
    saveUsOrganization("0aaaaaaa1", "100% Health");
    saveUsOrganization("0aaaaaaa2", "1000 Health");
    saveUsOrganization("0aaaaaaa3", "A_B Lab");
    saveUsOrganization("0aaaaaaa4", "AXB Lab");
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("100%", 10)))
        .extracting(OrganizationSuggestion::matchedName)
        .containsExactly("100% Health");
    assertThat(adapter.suggest(SuggestQuery.of("A_B", 10)))
        .extracting(OrganizationSuggestion::matchedName)
        .containsExactly("A_B Lab");
  }

  @Test
  @DisplayName("types 为 NULL 的机构照样返回，types 为空列表")
  void should_return_empty_types_when_organization_types_is_null() {
    Long typeless = saveOrganization("0aaaaaaa1", "Typeless Institute", "active", null);
    saveName(typeless, "Typeless Institute", "ror_display", "en");
    flush();

    List<OrganizationSuggestion> items = adapter.suggest(SuggestQuery.of("Typeless", 10));

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().organization().types()).isEmpty();
  }

  @Test
  @DisplayName("limit 生效")
  void should_respect_limit() {
    for (int i = 1; i <= 5; i++) {
      saveUsOrganization("0aaaaaab" + i, "Lim Institute " + i);
    }
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Lim", 2))).hasSize(2);
  }

  @Test
  @DisplayName("按 ROR ID 查到 active 机构的摘要")
  void should_find_active_organization_by_ror_id() {
    Long pumch =
        saveOrganization(
            "0aaaaaaa1",
            "Peking Union Medical College Hospital",
            "active",
            Set.of("funder", "healthcare"));
    saveName(pumch, "Peking Union Medical College Hospital", "ror_display", "en");
    saveName(pumch, "北京协和医院", "label", "zh");
    saveLocation(pumch, "CN", "Beijing");
    flush();

    OrganizationSummary summary = adapter.findByRorId("0aaaaaaa1").orElseThrow();

    assertThat(summary.displayName()).isEqualTo("Peking Union Medical College Hospital");
    assertThat(summary.zhName()).isEqualTo("北京协和医院");
    assertThat(summary.countryCode()).isEqualTo("CN");
    assertThat(summary.cityName()).isEqualTo("Beijing");
    assertThat(summary.types()).containsExactlyInAnyOrder("funder", "healthcare");
  }

  @Test
  @DisplayName("不存在或非 active 的 ROR ID 查不到")
  void should_return_empty_for_unknown_or_inactive_ror_id() {
    Long inactive = saveOrganization("0aaaaaaa7", "Closed Institute", "inactive", EDUCATION);
    saveName(inactive, "Closed Institute", "ror_display", "en");
    flush();

    assertThat(adapter.findByRorId("0zzzzzzz9")).isEmpty();
    assertThat(adapter.findByRorId("0aaaaaaa7")).isEmpty();
  }

  @Test
  @DisplayName("别名表的别名能命中，LABEL 别名当 zhName")
  void should_match_patra_alias_and_use_seed_label_as_zh_name() {
    Long huaxi =
        saveOrganization(
            "0aaaaaaa4", "West China Hospital of Sichuan University", "active", HEALTHCARE);
    saveName(huaxi, "West China Hospital of Sichuan University", "ror_display", "en");
    saveLocation(huaxi, "CN", "Chengdu");
    flush();
    saveAlias("0aaaaaaa4", "四川大学华西医院", "zh", "LABEL");
    saveAlias("0aaaaaaa4", "华西", "zh", "ALIAS");

    OrganizationSuggestion suggestion = adapter.suggest(SuggestQuery.of("华西", 10)).getFirst();

    assertThat(suggestion.organization().rorId()).isEqualTo("0aaaaaaa4");
    assertThat(suggestion.matchedName()).isEqualTo("华西");
    assertThat(suggestion.organization().zhName()).isEqualTo("四川大学华西医院");
  }

  @Test
  @DisplayName("别名表的 LABEL 压过 ROR 自带的中文 label")
  void should_let_seed_label_override_ror_zh_label() {
    Long anzhen = saveOrganization("0aaaaaaa5", "Beijing Anzhen Hospital", "active", HEALTHCARE);
    saveName(anzhen, "Beijing Anzhen Hospital", "ror_display", "en");
    saveName(anzhen, "北京市心肺血管疾病研究所", "label", "zh");
    saveLocation(anzhen, "CN", "Beijing");
    flush();
    saveAlias("0aaaaaaa5", "北京安贞医院", "zh", "LABEL");

    assertThat(adapter.findByRorId("0aaaaaaa5").orElseThrow().zhName()).isEqualTo("北京安贞医院");
  }

  @Test
  @DisplayName("可重复迁移的种子文件已把别名灌进表里")
  void should_load_alias_seed_from_repeatable_migration() {
    Object count =
        em.getEntityManager()
            .createNativeQuery(
                "SELECT count(*) FROM cat_organization_alias WHERE source IN ('SEED', 'WIKIDATA')")
            .getSingleResult();

    assertThat(((Number) count).longValue()).isGreaterThan(100L);
  }

  @Test
  @DisplayName("同一机构第二条中文 LABEL 别名被唯一索引拒绝（一家机构只有一个中文显示名）")
  void should_reject_second_zh_label_alias_for_same_organization() {
    saveAlias("0aaaaaaa6", "甲医院", "zh", "LABEL");

    assertThatThrownBy(() -> saveAlias("0aaaaaaa6", "乙医院", "zh", "LABEL"))
        .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
  }

  // ===== 造数据 =====

  private Long saveChineseHospital(String rorId, String displayName, String zhLabel, String city) {
    Long id = saveOrganization(rorId, displayName, "active", HEALTHCARE);
    saveName(id, displayName, "ror_display", "en");
    saveName(id, zhLabel, "label", "zh");
    saveLocation(id, "CN", city);
    return id;
  }

  private Long saveUsOrganization(String rorId, String displayName) {
    Long id = saveOrganization(rorId, displayName, "active", HEALTHCARE);
    saveName(id, displayName, "ror_display", "en");
    saveLocation(id, "US", "Rochester");
    return id;
  }

  private Long saveOrganization(
      String rorId, String displayName, String status, Set<String> types) {
    OrganizationEntity o = new OrganizationEntity();
    o.setId(SnowflakeIdGenerator.getId());
    o.setRorId(rorId);
    o.setDisplayName(displayName);
    o.setStatus(status);
    o.setTypes(types);
    em.persist(o);
    return o.getId();
  }

  private void saveName(Long orgId, String value, String type, String lang) {
    OrganizationNameEntity n = new OrganizationNameEntity();
    n.setId(SnowflakeIdGenerator.getId());
    n.setOrgId(orgId);
    n.setValue(value);
    n.setTypes(Set.of(type));
    n.setLang(lang);
    em.persist(n);
  }

  private void saveLocation(Long orgId, String countryCode, String city) {
    OrganizationLocationEntity l = new OrganizationLocationEntity();
    l.setId(SnowflakeIdGenerator.getId());
    l.setOrgId(orgId);
    l.setCountryCode(countryCode);
    l.setCityName(city);
    em.persist(l);
  }

  private void saveAlias(String rorId, String alias, String lang, String aliasType) {
    em.getEntityManager()
        .createNativeQuery(
            "INSERT INTO cat_organization_alias (ror_id, alias, lang, alias_type, source)"
                + " VALUES (?1, ?2, ?3, ?4, 'SEED')")
        .setParameter(1, rorId)
        .setParameter(2, alias)
        .setParameter(3, lang)
        .setParameter(4, aliasType)
        .executeUpdate();
  }

  private void softDelete(Long orgId) {
    em.getEntityManager()
        .createNativeQuery("UPDATE cat_organization SET deleted_at = now() WHERE id = ?1")
        .setParameter(1, orgId)
        .executeUpdate();
  }

  private void flush() {
    em.flush();
    em.clear();
  }
}
