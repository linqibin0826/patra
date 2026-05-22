package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import dev.linqibin.patra.catalog.domain.model.entity.MeshConcept;
import dev.linqibin.patra.catalog.domain.model.enums.ScrClass;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshUI;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.ScrSource;
import dev.linqibin.patra.catalog.infra.config.CatalogPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrHeadingMappedToDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrIndexingInfoDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrPharmacologicalActionDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrSourceDao;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// MeSH SCR 仓储实现集成测试（JPA 版本）。
///
/// 使用 Testcontainers + PostgreSQL 17 测试批量保存操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 PostgreSQL 容器
/// - 测试覆盖：hasAnyData、insertAll（聚合根批量插入）、findAllByNameIn
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MeshScrRepositoryAdapter.class, JpaAuditingConfig.class, JacksonAutoConfiguration.class})
@ComponentScan(basePackages = "dev.linqibin.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("MeshScrRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshScrRepositoryAdapterIT {

  @Autowired private MeshScrRepositoryAdapter repository;

  @Autowired private MeshScrDao scrDao;
  @Autowired private MeshScrHeadingMappedToDao headingMappedToDao;
  @Autowired private MeshConceptDao conceptDao;
  @Autowired private MeshScrSourceDao sourceDao;
  @Autowired private MeshScrIndexingInfoDao indexingInfoDao;
  @Autowired private MeshScrPharmacologicalActionDao pharmacologicalActionDao;

  // ========== hasAnyData() 测试 ==========

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = scrDao.count();
      assertThat(count).isEqualTo(0);

      // When & Then
      assertThat(repository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 通过 insertAll 插入数据
      MeshScrAggregate scr = createScr("C000001", "Test Chemical");
      repository.insertAll(List.of(scr));

      // When & Then
      assertThat(repository.hasAnyData()).isTrue();
    }
  }

  // ========== insertAll() 测试 ==========

  @Nested
  @DisplayName("insertAll() 测试")
  class InsertAllTests {

    @Test
    @DisplayName("空列表不应抛出异常")
    void insertAll_emptyList_shouldNotThrow() {
      // When & Then
      assertThatCode(() -> repository.insertAll(List.of())).doesNotThrowAnyException();

      // 验证没有数据插入
      assertThat(scrDao.count()).isZero();
    }

    @Test
    @DisplayName("应该正确插入单个聚合根（仅主表）")
    void insertAll_singleScr_shouldInsertMain() {
      // Given
      MeshScrAggregate scr = createScr("C000001", "Test Chemical");

      // When
      repository.insertAll(List.of(scr));

      // Then: 主表有 1 条记录
      assertThat(scrDao.count()).isEqualTo(1);

      // Then: 子表为空（没有添加子实体）
      assertThat(headingMappedToDao.count()).isZero();
      assertThat(conceptDao.count()).isZero();
      assertThat(sourceDao.count()).isZero();
      assertThat(indexingInfoDao.count()).isZero();
      assertThat(pharmacologicalActionDao.count()).isZero();
    }

    @Test
    @DisplayName("应该正确插入聚合根及所有子表")
    void insertAll_withChildren_shouldInsertAllTables() {
      // Given: 创建带子实体的聚合根
      MeshScrAggregate scr = createScrWithChildren();

      // When
      repository.insertAll(List.of(scr));

      // Then: 验证主表
      assertThat(scrDao.count()).isEqualTo(1);

      // Then: 验证子表
      assertThat(headingMappedToDao.count()).isEqualTo(2);
      assertThat(conceptDao.count()).isEqualTo(1);
      assertThat(sourceDao.count()).isEqualTo(2);
      assertThat(indexingInfoDao.count()).isEqualTo(1);
      assertThat(pharmacologicalActionDao.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确插入多个聚合根")
    void insertAll_multipleScrs_shouldInsertAll() {
      // Given
      MeshScrAggregate scr1 = createScr("C000001", "Chemical 1");
      MeshScrAggregate scr2 = createScr("C000002", "Chemical 2");
      MeshScrAggregate scr3 = createScr("C000003", "Disease 1", ScrClass.DISEASE);

      // When
      repository.insertAll(List.of(scr1, scr2, scr3));

      // Then: 主表有 3 条记录
      assertThat(scrDao.count()).isEqualTo(3);

      // Then: 验证 UI 唯一性
      var saved = scrDao.findAll();
      assertThat(saved)
          .extracting(s -> s.getUi())
          .containsExactlyInAnyOrder("C000001", "C000002", "C000003");
    }

    @Test
    @DisplayName("子表应正确关联到主表（通过 scrUi）")
    void insertAll_shouldSetCorrectScrUi() {
      // Given
      MeshScrAggregate scr = createScrWithChildren();

      // When
      repository.insertAll(List.of(scr));

      // Then: 获取主表 scrUi
      var savedScr = scrDao.findAll().get(0);
      String scrUi = savedScr.getUi();
      assertThat(scrUi).isEqualTo("C000001");

      // Then: 验证 HeadingMappedTo 的外键
      var headingMappedTos = headingMappedToDao.findAll();
      assertThat(headingMappedTos).allMatch(h -> h.getScrUi().equals(scrUi));

      // Then: 验证 Concept 的外键
      var concepts = conceptDao.findAll();
      assertThat(concepts).allMatch(c -> c.getOwnerUi().equals(scrUi));

      // Then: 验证 Source 的外键
      var sources = sourceDao.findAll();
      assertThat(sources).allMatch(s -> s.getScrUi().equals(scrUi));

      // Then: 验证 IndexingInfo 的外键
      var indexingInfos = indexingInfoDao.findAll();
      assertThat(indexingInfos).allMatch(i -> i.getScrUi().equals(scrUi));

      // Then: 验证 PharmacologicalAction 的外键
      var pharmacologicalActions = pharmacologicalActionDao.findAll();
      assertThat(pharmacologicalActions).allMatch(p -> p.getScrUi().equals(scrUi));
    }

    @Test
    @DisplayName("Source 排序号应正确保存")
    void insertAll_sourceShouldPreserveOrderNum() {
      // Given
      MeshScrAggregate scr = createScr("C000001", "Test Chemical");
      scr.addSource(ScrSource.of("NCI2004_11_17", 0));
      scr.addSource(ScrSource.of("FDA SRS (2023)", 1));
      scr.addSource(ScrSource.of("DrugBank", 2));

      // When
      repository.insertAll(List.of(scr));

      // Then: 验证排序号
      var sources = sourceDao.findAll();
      assertThat(sources).extracting(s -> s.getOrderNum()).containsExactlyInAnyOrder(0, 1, 2);
    }
  }

  // ========== findAllByNameIn() 测试 ==========

  @Nested
  @DisplayName("findAllByNameIn() 测试")
  class FindAllByNameInTests {

    @Test
    @DisplayName("空集合输入 - 应该返回空 Map")
    void findAllByNameIn_emptyCollection_shouldReturnEmptyMap() {
      // Given: 空集合
      Set<String> emptyNames = Set.of();

      // When
      Map<String, String> result = repository.findAllByNameIn(emptyNames);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 输入 - 应该返回空 Map")
    void findAllByNameIn_nullInput_shouldReturnEmptyMap() {
      // When
      Map<String, String> result = repository.findAllByNameIn(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("单个名称匹配 - 应该返回正确的 name → ui 映射")
    void findAllByNameIn_singleMatch_shouldReturnCorrectMapping() {
      // Given: 插入测试数据
      MeshScrAggregate scr = createScr("C000001", "Aspirin");
      repository.insertAll(List.of(scr));

      // When
      Map<String, String> result = repository.findAllByNameIn(Set.of("Aspirin"));

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("Aspirin")).isEqualTo("C000001");
    }

    @Test
    @DisplayName("多个名称匹配 - 应该返回所有匹配项")
    void findAllByNameIn_multipleMatches_shouldReturnAllMappings() {
      // Given: 插入多条测试数据
      MeshScrAggregate scr1 = createScr("C000001", "Aspirin");
      MeshScrAggregate scr2 = createScr("C000002", "Ibuprofen");
      MeshScrAggregate scr3 = createScr("C000003", "Acetaminophen");
      repository.insertAll(List.of(scr1, scr2, scr3));

      // When
      Map<String, String> result =
          repository.findAllByNameIn(Set.of("Aspirin", "Ibuprofen", "Acetaminophen"));

      // Then
      assertThat(result).hasSize(3);
      assertThat(result.get("Aspirin")).isEqualTo("C000001");
      assertThat(result.get("Ibuprofen")).isEqualTo("C000002");
      assertThat(result.get("Acetaminophen")).isEqualTo("C000003");
    }

    @Test
    @DisplayName("部分名称不存在 - 应该只返回存在的映射")
    void findAllByNameIn_partialMatch_shouldReturnOnlyExistingMappings() {
      // Given: 只插入部分数据
      MeshScrAggregate scr = createScr("C000001", "Aspirin");
      repository.insertAll(List.of(scr));

      // When: 查询包含存在和不存在的名称
      Map<String, String> result =
          repository.findAllByNameIn(Set.of("Aspirin", "NonExistent Drug", "Another Missing"));

      // Then: 只返回存在的映射
      assertThat(result).hasSize(1);
      assertThat(result.get("Aspirin")).isEqualTo("C000001");
      assertThat(result).doesNotContainKey("NonExistent Drug");
      assertThat(result).doesNotContainKey("Another Missing");
    }

    @Test
    @DisplayName("所有名称都不存在 - 应该返回空 Map")
    void findAllByNameIn_noMatches_shouldReturnEmptyMap() {
      // Given: 插入不相关的数据
      MeshScrAggregate scr = createScr("C000001", "Aspirin");
      repository.insertAll(List.of(scr));

      // When: 查询不存在的名称
      Map<String, String> result =
          repository.findAllByNameIn(Set.of("NonExistent 1", "NonExistent 2"));

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("大小写敏感性 - 名称匹配应该区分大小写")
    void findAllByNameIn_caseSensitivity_shouldBeCaseSensitive() {
      // Given: 插入数据
      MeshScrAggregate scr = createScr("C000001", "Aspirin");
      repository.insertAll(List.of(scr));

      // When: 使用不同大小写查询
      Map<String, String> result =
          repository.findAllByNameIn(
              Set.of(
                  "aspirin", // 全小写
                  "ASPIRIN", // 全大写
                  "Aspirin" // 正确大小写
                  ));

      // Then: PG `C` collation（spec §4.22）大小写敏感，确定匹配单个
      assertThat(result).hasSize(1);
      assertThat(result).containsKey("Aspirin");
    }
  }

  /// 创建测试用的 MeshScrAggregate（仅主表）。
  private MeshScrAggregate createScr(String ui, String name) {
    return MeshScrAggregate.create(MeshUI.of(ui), name);
  }

  /// 创建测试用的 MeshScrAggregate（指定类别）。
  private MeshScrAggregate createScr(String ui, String name, ScrClass scrClass) {
    return MeshScrAggregate.create(MeshUI.of(ui), name, scrClass);
  }

  /// 创建带子实体的 MeshScrAggregate。
  private MeshScrAggregate createScrWithChildren() {
    MeshUI scrUi = MeshUI.of("C000001");

    MeshScrAggregate scr = MeshScrAggregate.create(scrUi, "Test Chemical", ScrClass.CHEMICAL);

    // 添加 HeadingMappedTo（映射到 Descriptor）
    scr.addHeadingMappedTo(HeadingMappedTo.of(MeshUI.of("D000001")));
    scr.addHeadingMappedTo(HeadingMappedTo.of(MeshUI.of("D000002"), MeshUI.of("Q000001")));

    // 添加 Concept
    MeshConcept concept = MeshConcept.create(scrUi, MeshUI.conceptOf(1), "Test SCR Concept", true);
    scr.addConcept(concept);

    // 添加 Source
    scr.addSource(ScrSource.of("NCI2004_11_17", 0));
    scr.addSource(ScrSource.of("FDA SRS (2023)", 1));

    // 添加 IndexingInfo
    scr.addIndexingInfo(IndexingInfo.ofDescriptor(MeshUI.of("D000003")));

    // 添加 PharmacologicalAction
    scr.addPharmacologicalAction(
        PharmacologicalAction.of(MeshUI.of("D000004"), "Anti-Inflammatory Agents"));

    return scr;
  }
}
