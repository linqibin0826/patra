package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import dev.linqibin.patra.catalog.domain.model.entity.MeshConcept;
import dev.linqibin.patra.catalog.domain.model.entity.MeshEntryTerm;
import dev.linqibin.patra.catalog.domain.model.entity.MeshTreeNumber;
import dev.linqibin.patra.catalog.domain.model.enums.DescriptorClass;
import dev.linqibin.patra.catalog.domain.model.enums.LexicalTag;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshUI;
import dev.linqibin.patra.catalog.infra.config.CatalogPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptRelationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshDescriptorDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshEntryCombinationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshEntryTermDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshTreeNumberDao;
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

/// MeSH 主题词仓储实现集成测试（JPA 版本）。
///
/// 使用 Testcontainers + MySQL 8 测试批量保存操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 MySQL 容器
/// - 测试覆盖：hasAnyData、insertAll（聚合根批量插入）
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshDescriptorRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "dev.linqibin.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("MeshDescriptorRepositoryAdapter 集成测试（JPA）")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshDescriptorRepositoryAdapterIT {

  @Autowired private MeshDescriptorRepositoryAdapter repository;

  @Autowired private MeshDescriptorDao descriptorDao;
  @Autowired private MeshTreeNumberDao treeNumberDao;
  @Autowired private MeshConceptDao conceptDao;
  @Autowired private MeshConceptRelationDao conceptRelationDao;
  @Autowired private MeshEntryTermDao entryTermDao;
  @Autowired private MeshEntryCombinationDao entryCombinationDao;

  // ========== hasAnyData() 测试 ==========

  @Nested
  @DisplayName("hasAnyData() 测试")
  class HasAnyDataTests {

    @Test
    @DisplayName("空表 - 应该返回 false")
    void hasAnyData_emptyTable_shouldReturnFalse() {
      // Given: 空表
      long count = descriptorDao.count();
      assertThat(count).isEqualTo(0);

      // When & Then
      assertThat(repository.hasAnyData()).isFalse();
    }

    @Test
    @DisplayName("有数据 - 应该返回 true")
    void hasAnyData_withData_shouldReturnTrue() {
      // Given: 通过 insertAll 插入数据
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Test Descriptor");
      repository.insertAll(List.of(descriptor));

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
      assertThat(descriptorDao.count()).isZero();
    }

    @Test
    @DisplayName("应该正确插入单个聚合根（仅主表）")
    void insertAll_singleDescriptor_shouldInsertMain() {
      // Given
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Test Descriptor");

      // When
      repository.insertAll(List.of(descriptor));

      // Then: 主表有 1 条记录
      assertThat(descriptorDao.count()).isEqualTo(1);

      // Then: 子表为空（没有添加子实体）
      assertThat(treeNumberDao.count()).isZero();
      assertThat(conceptDao.count()).isZero();
      assertThat(entryTermDao.count()).isZero();
    }

    @Test
    @DisplayName("应该正确插入聚合根及所有子表")
    void insertAll_withChildren_shouldInsertAllTables() {
      // Given: 创建带子实体的聚合根
      MeshDescriptorAggregate descriptor = createDescriptorWithChildren();

      // When
      repository.insertAll(List.of(descriptor));

      // Then: 验证主表
      assertThat(descriptorDao.count()).isEqualTo(1);

      // Then: 验证子表
      assertThat(treeNumberDao.count()).isEqualTo(2);
      assertThat(conceptDao.count()).isEqualTo(1);
      assertThat(entryTermDao.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确插入多个聚合根")
    void insertAll_multipleDescriptors_shouldInsertAll() {
      // Given
      MeshDescriptorAggregate descriptor1 = createDescriptor("D000001", "Descriptor 1");
      MeshDescriptorAggregate descriptor2 = createDescriptor("D000002", "Descriptor 2");
      MeshDescriptorAggregate descriptor3 = createDescriptor("D000003", "Descriptor 3");

      // When
      repository.insertAll(List.of(descriptor1, descriptor2, descriptor3));

      // Then: 主表有 3 条记录
      assertThat(descriptorDao.count()).isEqualTo(3);

      // Then: 验证 UI 唯一性
      var saved = descriptorDao.findAll();
      assertThat(saved)
          .extracting(d -> d.getUi())
          .containsExactlyInAnyOrder("D000001", "D000002", "D000003");
    }

    @Test
    @DisplayName("子表应正确关联到主表（通过 descriptorUi）")
    void insertAll_shouldSetCorrectDescriptorUi() {
      // Given
      MeshDescriptorAggregate descriptor = createDescriptorWithChildren();

      // When
      repository.insertAll(List.of(descriptor));

      // Then: 获取主表 descriptorUi
      var savedDescriptor = descriptorDao.findAll().get(0);
      String descriptorUi = savedDescriptor.getUi();
      assertThat(descriptorUi).isEqualTo("D000001");

      // Then: 验证树形编号的外键
      var treeNumbers = treeNumberDao.findAll();
      assertThat(treeNumbers).allMatch(tn -> tn.getDescriptorUi().equals(descriptorUi));

      // Then: 验证概念的外键
      var concepts = conceptDao.findAll();
      assertThat(concepts).allMatch(c -> c.getOwnerUi().equals(descriptorUi));

      // Then: 验证入口术语的外键
      var entryTerms = entryTermDao.findAll();
      assertThat(entryTerms).allMatch(et -> et.getOwnerUi().equals(descriptorUi));
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
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Cardiovascular Diseases");
      repository.insertAll(List.of(descriptor));

      // When
      Map<String, String> result = repository.findAllByNameIn(Set.of("Cardiovascular Diseases"));

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("Cardiovascular Diseases")).isEqualTo("D000001");
    }

    @Test
    @DisplayName("多个名称匹配 - 应该返回所有匹配项")
    void findAllByNameIn_multipleMatches_shouldReturnAllMappings() {
      // Given: 插入多条测试数据
      MeshDescriptorAggregate descriptor1 = createDescriptor("D000001", "Cardiovascular Diseases");
      MeshDescriptorAggregate descriptor2 = createDescriptor("D000002", "Neoplasms");
      MeshDescriptorAggregate descriptor3 = createDescriptor("D000003", "Diabetes Mellitus");
      repository.insertAll(List.of(descriptor1, descriptor2, descriptor3));

      // When
      Map<String, String> result =
          repository.findAllByNameIn(
              Set.of("Cardiovascular Diseases", "Neoplasms", "Diabetes Mellitus"));

      // Then
      assertThat(result).hasSize(3);
      assertThat(result.get("Cardiovascular Diseases")).isEqualTo("D000001");
      assertThat(result.get("Neoplasms")).isEqualTo("D000002");
      assertThat(result.get("Diabetes Mellitus")).isEqualTo("D000003");
    }

    @Test
    @DisplayName("部分名称不存在 - 应该只返回存在的映射")
    void findAllByNameIn_partialMatch_shouldReturnOnlyExistingMappings() {
      // Given: 只插入部分数据
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Cardiovascular Diseases");
      repository.insertAll(List.of(descriptor));

      // When: 查询包含存在和不存在的名称
      Map<String, String> result =
          repository.findAllByNameIn(
              Set.of("Cardiovascular Diseases", "NonExistent Disease", "Another Missing"));

      // Then: 只返回存在的映射
      assertThat(result).hasSize(1);
      assertThat(result.get("Cardiovascular Diseases")).isEqualTo("D000001");
      assertThat(result).doesNotContainKey("NonExistent Disease");
      assertThat(result).doesNotContainKey("Another Missing");
    }

    @Test
    @DisplayName("所有名称都不存在 - 应该返回空 Map")
    void findAllByNameIn_noMatches_shouldReturnEmptyMap() {
      // Given: 插入不相关的数据
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Cardiovascular Diseases");
      repository.insertAll(List.of(descriptor));

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
      MeshDescriptorAggregate descriptor = createDescriptor("D000001", "Cardiovascular Diseases");
      repository.insertAll(List.of(descriptor));

      // When: 使用不同大小写查询
      Map<String, String> result =
          repository.findAllByNameIn(
              Set.of(
                  "cardiovascular diseases", // 全小写
                  "CARDIOVASCULAR DISEASES", // 全大写
                  "Cardiovascular Diseases" // 正确大小写
                  ));

      // PG `C` collation（spec §4.22）大小写敏感，确定匹配单个
      assertThat(result).hasSize(1);
      assertThat(result).containsKey("Cardiovascular Diseases");
    }
  }

  /// 创建测试用的 MeshDescriptorAggregate（仅主表）。
  private MeshDescriptorAggregate createDescriptor(String ui, String name) {
    return MeshDescriptorAggregate.create(MeshUI.of(ui), name, DescriptorClass.TOPICAL, "2025");
  }

  /// 创建带子实体的 MeshDescriptorAggregate。
  private MeshDescriptorAggregate createDescriptorWithChildren() {
    MeshUI descriptorUi = MeshUI.of("D000001");

    MeshDescriptorAggregate descriptor =
        MeshDescriptorAggregate.create(
            descriptorUi, "Test Descriptor", DescriptorClass.TOPICAL, "2025");

    // 添加树形编号
    descriptor.addTreeNumber(MeshTreeNumber.create(descriptorUi, "C04.557.337", true));
    descriptor.addTreeNumber(MeshTreeNumber.create(descriptorUi, "C08.381.540", false));

    // 添加概念
    MeshConcept concept =
        MeshConcept.create(descriptorUi, MeshUI.conceptOf(1), "Test Concept", true);
    descriptor.addConcept(concept);

    // 添加入口术语
    MeshEntryTerm entryTerm =
        MeshEntryTerm.create(
            descriptorUi,
            MeshUI.of("T000001"),
            "Test Term",
            LexicalTag.NON,
            true,
            true,
            true,
            false);
    descriptor.addEntryTerm(entryTerm);

    return descriptor;
  }
}
