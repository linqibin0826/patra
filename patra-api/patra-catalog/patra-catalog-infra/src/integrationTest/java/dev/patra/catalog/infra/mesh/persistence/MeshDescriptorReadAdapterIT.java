package dev.patra.catalog.infra.mesh.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.model.enums.MeshRecordType;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.patra.catalog.infra.mesh.persistence.entity.MeshDescriptorEntity;
import dev.patra.catalog.infra.mesh.persistence.entity.MeshEntryTermEntity;
import dev.patra.catalog.infra.mesh.persistence.entity.MeshTreeNumberEntity;
import dev.patra.tool.jpa.autoconfigure.JpaAuditingConfiguration;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
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

/// `MeshDescriptorReadAdapter` 集成测试：在真实 PostgreSQL（Flyway 跑到 `V9`）上验证主题词名与
/// 入口词的匹配、排序、去重、class 过滤、树号顺序与按 UI 查询。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MeshDescriptorReadAdapter.class, JpaAuditingConfiguration.class})
@ActiveProfiles("test")
@DisplayName("MeshDescriptorReadAdapter 主题词建议与查询集成测试")
class MeshDescriptorReadAdapterIT {

  private static final String TOPICAL = "1";
  private static final String PUBLICATION_TYPE = "2";

  @Autowired private MeshDescriptorReadAdapter adapter;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("主题词名精确命中排第一")
  void should_rank_exact_name_first() {
    saveDescriptor("D009371", "Neoplasms by Site", TOPICAL);
    saveDescriptor("D009369", "Neoplasms", TOPICAL);
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Neoplasms", 10)))
        .extracting(s -> s.descriptor().ui())
        .containsExactly("D009369", "D009371");
  }

  @Test
  @DisplayName("入口词精确命中返回主题词，排在名称前缀命中之前")
  void should_return_descriptor_via_exact_entry_term_before_prefix_names() {
    saveDescriptor("D009369", "Neoplasms", TOPICAL);
    saveEntryTerm("D009369", "Cancer", false);
    saveDescriptor("D000072716", "Cancer Pain", TOPICAL);
    flush();

    List<MeshDescriptorSuggestion> items = adapter.suggest(SuggestQuery.of("Cancer", 10));

    assertThat(items).extracting(s -> s.descriptor().ui()).containsExactly("D009369", "D000072716");
    assertThat(items.get(0).matchedTerm()).isEqualTo("Cancer");
    assertThat(items.get(1).matchedTerm()).isNull();
  }

  @Test
  @DisplayName("入口词等于主题词名时 matchedTerm 为空")
  void should_null_matched_term_when_entry_term_equals_name() {
    saveDescriptor("D003920", "Diabetes Mellitus", TOPICAL);
    saveEntryTerm("D003920", "Diabetes Mellitus", false);
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Diabetes Mellitus", 10)).getFirst().matchedTerm())
        .isNull();
  }

  @Test
  @DisplayName("排列入口词与 SCR 入口词不参与匹配")
  void should_ignore_permuted_and_scr_terms() {
    saveDescriptor("D003920", "Diabetes Mellitus", TOPICAL);
    saveEntryTerm("D003920", "Mellitus, Diabetes", true);
    saveScrTerm("C000001", "Mellitus Compound");
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Mellitus,", 10))).isEmpty();
    assertThat(adapter.suggest(SuggestQuery.of("Mellitus Compound", 10))).isEmpty();
  }

  @Test
  @DisplayName("非主题类的主题词（出版类型等）不出现")
  void should_exclude_non_topical_descriptor_classes() {
    saveDescriptor("D016454", "Review", PUBLICATION_TYPE);
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Review", 10))).isEmpty();
  }

  @Test
  @DisplayName("词首命中")
  void should_match_word_start() {
    saveDescriptor("D003920", "Diabetes Mellitus", TOPICAL);
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("mellitus", 10)).getFirst().descriptor().name())
        .isEqualTo("Diabetes Mellitus");
  }

  @Test
  @DisplayName("树号主树号在前")
  void should_order_tree_numbers_primary_first() {
    saveDescriptor("D003920", "Diabetes Mellitus", TOPICAL);
    saveTreeNumber("D003920", "C19.246", 2, false);
    saveTreeNumber("D003920", "C18.452.394.750", 4, true);
    flush();

    assertThat(adapter.findByUi("D003920").orElseThrow().treeNumbers())
        .containsExactly("C18.452.394.750", "C19.246");
  }

  @Test
  @DisplayName("中文查询返回空列表而不是报错")
  void should_return_empty_for_cjk_query() {
    saveDescriptor("D003920", "Diabetes Mellitus", TOPICAL);
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("糖尿病", 10))).isEmpty();
  }

  @Test
  @DisplayName("按 UI 查不限 class，不存在为空")
  void should_find_descriptor_by_ui_regardless_of_class_and_return_empty_for_unknown() {
    saveDescriptor("D016454", "Review", PUBLICATION_TYPE);
    flush();

    assertThat(adapter.findByUi("D016454").orElseThrow().name()).isEqualTo("Review");
    assertThat(adapter.findByUi("D999999")).isEmpty();
  }

  @Test
  @DisplayName("limit 生效")
  void should_respect_limit() {
    for (int i = 1; i <= 5; i++) {
      saveDescriptor("D00000" + i, "Lim Term " + i, TOPICAL);
    }
    flush();

    assertThat(adapter.suggest(SuggestQuery.of("Lim", 3))).hasSize(3);
  }

  // ===== 造数据 =====

  private void saveDescriptor(String ui, String name, String descriptorClass) {
    MeshDescriptorEntity d = new MeshDescriptorEntity();
    d.setId(SnowflakeIdGenerator.getId());
    d.setUi(ui);
    d.setName(name);
    d.setDescriptorClass(descriptorClass);
    d.setActiveStatus(true);
    em.persist(d);
  }

  private void saveEntryTerm(String ownerUi, String term, boolean permuted) {
    saveTerm(MeshRecordType.DESCRIPTOR, ownerUi, term, permuted);
  }

  private void saveScrTerm(String ownerUi, String term) {
    saveTerm(MeshRecordType.SCR, ownerUi, term, false);
  }

  private void saveTerm(MeshRecordType recordType, String ownerUi, String term, boolean permuted) {
    MeshEntryTermEntity t = new MeshEntryTermEntity();
    t.setId(SnowflakeIdGenerator.getId());
    t.setRecordType(recordType);
    t.setOwnerUi(ownerUi);
    t.setTerm(term);
    t.setIsPrintFlag(true);
    t.setIsPermutedTerm(permuted);
    t.setIsConceptPreferred(false);
    em.persist(t);
  }

  private void saveTreeNumber(String ui, String treeNumber, int level, boolean primary) {
    MeshTreeNumberEntity tn = new MeshTreeNumberEntity();
    tn.setId(SnowflakeIdGenerator.getId());
    tn.setDescriptorUi(ui);
    tn.setTreeNumber(treeNumber);
    tn.setTreeLevel(level);
    tn.setIsPrimary(primary);
    em.persist(tn);
  }

  private void flush() {
    em.flush();
    em.clear();
  }
}
