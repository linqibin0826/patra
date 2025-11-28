package com.patra.catalog.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshConceptRelationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryCombinationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// MeSH 主题词仓储实现集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试批量保存操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法独立
///   - TestContainers：自动启动和停止 MySQL 容器
///   - 测试覆盖：saveBatch()、saveTreeNumbersBatch() 等批量操作
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshDescriptorRepositoryAdapter.class,
  MeshDescriptorConverter.class,
  TestMybatisPlusAutoConfiguration.class
})
@MapperScan("com.patra.catalog.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("MeshDescriptorRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshDescriptorRepositoryAdapterIT {

  @Autowired private MeshDescriptorRepositoryAdapter repository;

  @Autowired private MeshTreeNumberMapper meshTreeNumberMapper;
  @Autowired private MeshEntryTermMapper meshEntryTermMapper;
  @Autowired private MeshConceptMapper meshConceptMapper;
  @Autowired private MeshConceptRelationMapper meshConceptRelationMapper;
  @Autowired private MeshEntryCombinationMapper meshEntryCombinationMapper;
  @Autowired private MeshDescriptorMapper meshDescriptorMapper;

  // ==================== saveTreeNumbersBatch 测试 ====================

  @Nested
  @DisplayName("saveTreeNumbersBatch() 测试")
  class SaveTreeNumbersBatchTest {

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveTreeNumbersBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<MeshTreeNumber> emptyList = List.of();

      // When: 批量保存空列表
      repository.saveTreeNumbersBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshTreeNumberMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("null列表 - 应该不抛出异常，直接返回")
    void saveTreeNumbersBatch_nullList_shouldReturnWithoutError() {
      // Given: null列表
      List<MeshTreeNumber> nullList = null;

      // When: 批量保存null列表
      repository.saveTreeNumbersBatch(nullList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshTreeNumberMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("单条记录 - 应该正确插入到数据库")
    void saveTreeNumbersBatch_singleRecord_shouldInsertSuccessfully() {
      // Given: 单个树形编号
      MeshUI descriptorUi = MeshUI.descriptorOf(1);
      MeshTreeNumber treeNumber = MeshTreeNumber.create(descriptorUi, "C04.557.337", true);

      // When: 批量保存
      repository.saveTreeNumbersBatch(List.of(treeNumber));

      // Then: 数据库应该有1条记录
      long count = meshTreeNumberMapper.selectCount(null);
      assertThat(count).isEqualTo(1);

      // Then: 验证保存的数据
      List<MeshTreeNumberDO> saved = meshTreeNumberMapper.selectList(null);
      assertThat(saved).hasSize(1);

      MeshTreeNumberDO savedDO = saved.get(0);
      assertThat(savedDO.getId()).isNotNull();
      assertThat(savedDO.getDescriptorUi()).isEqualTo("D000001");
      assertThat(savedDO.getTreeNumber()).isEqualTo("C04.557.337");
      assertThat(savedDO.getTreeLevel()).isEqualTo(3);
      assertThat(savedDO.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("多条记录 - 应该正确批量插入")
    void saveTreeNumbersBatch_multipleRecords_shouldInsertAllSuccessfully() {
      // Given: 创建多个树形编号（同一主题词的多个位置）
      MeshUI descriptorUi = MeshUI.descriptorOf(1);
      List<MeshTreeNumber> treeNumbers =
          List.of(
              MeshTreeNumber.create(descriptorUi, "C04.557.337", true),
              MeshTreeNumber.create(descriptorUi, "C08.381.540", false),
              MeshTreeNumber.create(descriptorUi, "C08.381.540.140", false));

      // When: 批量保存
      repository.saveTreeNumbersBatch(treeNumbers);

      // Then: 数据库应该有3条记录
      long count = meshTreeNumberMapper.selectCount(null);
      assertThat(count).isEqualTo(3);

      // Then: 验证所有记录
      List<MeshTreeNumberDO> saved = meshTreeNumberMapper.selectList(null);
      assertThat(saved).hasSize(3);

      // 验证树形编号唯一性
      assertThat(saved)
          .extracting(MeshTreeNumberDO::getTreeNumber)
          .containsExactlyInAnyOrder("C04.557.337", "C08.381.540", "C08.381.540.140");

      // 验证 primary 标记
      assertThat(saved.stream().filter(MeshTreeNumberDO::getIsPrimary).count()).isEqualTo(1);

      // 验证所有记录都有ID
      assertThat(saved).allMatch(tn -> tn.getId() != null);
    }

    @Test
    @DisplayName("不同主题词的树形编号 - 应该正确关联不同的 descriptorUi")
    void saveTreeNumbersBatch_differentDescriptors_shouldLinkCorrectly() {
      // Given: 创建属于不同主题词的树形编号
      List<MeshTreeNumber> treeNumbers =
          List.of(
              MeshTreeNumber.create(MeshUI.descriptorOf(1), "C04.557.337", true),
              MeshTreeNumber.create(MeshUI.descriptorOf(2), "A01.236.500", true),
              MeshTreeNumber.create(MeshUI.descriptorOf(3), "B01.050.150", true));

      // When: 批量保存
      repository.saveTreeNumbersBatch(treeNumbers);

      // Then: 数据库应该有3条记录
      long count = meshTreeNumberMapper.selectCount(null);
      assertThat(count).isEqualTo(3);

      // Then: 验证 descriptorUi 关联
      List<MeshTreeNumberDO> saved = meshTreeNumberMapper.selectList(null);
      assertThat(saved)
          .extracting(MeshTreeNumberDO::getDescriptorUi)
          .containsExactlyInAnyOrder("D000001", "D000002", "D000003");
    }

    @Test
    @DisplayName("大批量数据 - 应该正确处理（验证 BatchInsertHelper 分片）")
    void saveTreeNumbersBatch_largeDataSet_shouldInsertAllSuccessfully() {
      // Given: 创建 100 个树形编号（模拟真实场景）
      List<MeshTreeNumber> treeNumbers = new ArrayList<>();
      for (int i = 1; i <= 100; i++) {
        MeshUI descriptorUi = MeshUI.descriptorOf(i);
        // 创建层级深度为 3 的树形编号：X01.001.001 格式
        String treeNum =
            String.format(
                "%c%02d.%03d.%03d",
                (char) ('A' + (i % 26)), // A-Z 循环
                (i / 26) + 1, // 01-XX
                i % 1000, // 001-999
                i % 1000 // 001-999
                );
        treeNumbers.add(MeshTreeNumber.create(descriptorUi, treeNum, i % 3 == 0));
      }

      // When: 批量保存
      repository.saveTreeNumbersBatch(treeNumbers);

      // Then: 数据库应该有100条记录
      long count = meshTreeNumberMapper.selectCount(null);
      assertThat(count).isEqualTo(100);

      // 验证所有记录都有ID
      List<MeshTreeNumberDO> saved = meshTreeNumberMapper.selectList(null);
      assertThat(saved).allMatch(tn -> tn.getId() != null);
    }
  }

  // ==================== saveEntryTermsBatch 测试 ====================

  @Nested
  @DisplayName("saveEntryTermsBatch() 测试")
  class SaveEntryTermsBatchTest {

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveEntryTermsBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<MeshEntryTerm> emptyList = List.of();

      // When: 批量保存空列表
      repository.saveEntryTermsBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshEntryTermMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("单条记录 - 应该正确插入到数据库")
    void saveEntryTermsBatch_singleRecord_shouldInsertSuccessfully() {
      // Given: 单个入口术语
      MeshUI descriptorUi = MeshUI.descriptorOf(1);
      MeshUI termUi = MeshUI.of("T000001");
      MeshEntryTerm entryTerm =
          MeshEntryTerm.create(
              descriptorUi, termUi, "Test Term", LexicalTag.NON, true, true, true, false);

      // When: 批量保存
      repository.saveEntryTermsBatch(List.of(entryTerm));

      // Then: 数据库应该有1条记录
      long count = meshEntryTermMapper.selectCount(null);
      assertThat(count).isEqualTo(1);

      // Then: 验证保存的数据
      List<MeshEntryTermDO> saved = meshEntryTermMapper.selectList(null);
      assertThat(saved).hasSize(1);

      MeshEntryTermDO savedDO = saved.get(0);
      assertThat(savedDO.getId()).isNotNull();
      assertThat(savedDO.getDescriptorUi()).isEqualTo("D000001");
      assertThat(savedDO.getTermUi()).isEqualTo("T000001");
      assertThat(savedDO.getTerm()).isEqualTo("Test Term");
    }

    @Test
    @DisplayName("多条记录 - 应该正确批量插入")
    void saveEntryTermsBatch_multipleRecords_shouldInsertAllSuccessfully() {
      // Given: 创建多个入口术语
      MeshUI descriptorUi = MeshUI.descriptorOf(1);
      List<MeshEntryTerm> entryTerms =
          List.of(
              MeshEntryTerm.create(
                  descriptorUi,
                  MeshUI.of("T000001"),
                  "Term 1",
                  LexicalTag.NON,
                  true,
                  true,
                  true,
                  false),
              MeshEntryTerm.create(
                  descriptorUi,
                  MeshUI.of("T000002"),
                  "Term 2",
                  LexicalTag.ABB,
                  false,
                  true,
                  false,
                  false),
              MeshEntryTerm.create(
                  descriptorUi,
                  MeshUI.of("T000003"),
                  "Term 3",
                  LexicalTag.ACR,
                  false,
                  false,
                  false,
                  true));

      // When: 批量保存
      repository.saveEntryTermsBatch(entryTerms);

      // Then: 数据库应该有3条记录
      long count = meshEntryTermMapper.selectCount(null);
      assertThat(count).isEqualTo(3);

      // 验证所有记录都有ID
      List<MeshEntryTermDO> saved = meshEntryTermMapper.selectList(null);
      assertThat(saved).allMatch(et -> et.getId() != null);
    }
  }

  // ==================== saveConceptsBatch 测试 ====================

  @Nested
  @DisplayName("saveConceptsBatch() 测试")
  class SaveConceptsBatchTest {

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveConceptsBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<MeshConcept> emptyList = List.of();

      // When: 批量保存空列表
      repository.saveConceptsBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshConceptMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("单条记录 - 应该正确插入到数据库")
    void saveConceptsBatch_singleRecord_shouldInsertSuccessfully() {
      // Given: 单个概念
      MeshUI descriptorUi = MeshUI.descriptorOf(1);
      MeshUI conceptUi = MeshUI.conceptOf(1);
      MeshConcept concept = MeshConcept.create(descriptorUi, conceptUi, "Test Concept", true);

      // When: 批量保存
      repository.saveConceptsBatch(List.of(concept));

      // Then: 数据库应该有1条记录
      long count = meshConceptMapper.selectCount(null);
      assertThat(count).isEqualTo(1);

      // Then: 验证保存的数据
      List<MeshConceptDO> saved = meshConceptMapper.selectList(null);
      assertThat(saved).hasSize(1);

      MeshConceptDO savedDO = saved.get(0);
      assertThat(savedDO.getId()).isNotNull();
      assertThat(savedDO.getDescriptorUi()).isEqualTo("D000001");
      assertThat(savedDO.getConceptUi()).isEqualTo("M0000001");
      assertThat(savedDO.getConceptName()).isEqualTo("Test Concept");
      assertThat(savedDO.getIsPreferred()).isTrue();
    }

    @Test
    @DisplayName("多条记录 - 应该正确批量插入")
    void saveConceptsBatch_multipleRecords_shouldInsertAllSuccessfully() {
      // Given: 创建多个概念
      MeshUI descriptorUi = MeshUI.descriptorOf(1);
      List<MeshConcept> concepts =
          List.of(
              MeshConcept.create(descriptorUi, MeshUI.conceptOf(1), "Concept 1", true),
              MeshConcept.create(descriptorUi, MeshUI.conceptOf(2), "Concept 2", false),
              MeshConcept.create(descriptorUi, MeshUI.conceptOf(3), "Concept 3", false));

      // When: 批量保存
      repository.saveConceptsBatch(concepts);

      // Then: 数据库应该有3条记录
      long count = meshConceptMapper.selectCount(null);
      assertThat(count).isEqualTo(3);

      // 验证首选概念只有一个
      List<MeshConceptDO> saved = meshConceptMapper.selectList(null);
      assertThat(saved.stream().filter(MeshConceptDO::getIsPreferred).count()).isEqualTo(1);

      // 验证所有记录都有ID
      assertThat(saved).allMatch(c -> c.getId() != null);
    }
  }

  // ==================== saveBatch 测试 ====================

  @Nested
  @DisplayName("saveBatch() 测试")
  class SaveBatchTest {

    @Test
    @DisplayName("空列表 - 应该不抛出异常，直接返回")
    void saveBatch_emptyList_shouldReturnWithoutError() {
      // Given: 空列表
      List<MeshDescriptorAggregate> emptyList = List.of();

      // When: 批量保存空列表
      repository.saveBatch(emptyList);

      // Then: 不抛出异常，数据库应该没有记录
      long count = meshDescriptorMapper.selectCount(null);
      assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("单条记录 - 应该正确插入到数据库")
    void saveBatch_singleRecord_shouldInsertSuccessfully() {
      // Given: 单个主题词聚合根
      MeshDescriptorAggregate descriptor =
          MeshDescriptorAggregate.create(
              MeshUI.descriptorOf(1), "Test Descriptor", DescriptorClass.TOPICAL, "2025");

      // When: 批量保存
      repository.saveBatch(List.of(descriptor));

      // Then: 数据库应该有1条记录
      long count = meshDescriptorMapper.selectCount(null);
      assertThat(count).isEqualTo(1);

      // Then: 验证保存的数据
      List<MeshDescriptorDO> saved = meshDescriptorMapper.selectList(null);
      assertThat(saved).hasSize(1);

      MeshDescriptorDO savedDO = saved.get(0);
      assertThat(savedDO.getId()).isNotNull();
      assertThat(savedDO.getUi()).isEqualTo("D000001");
      assertThat(savedDO.getName()).isEqualTo("Test Descriptor");
      assertThat(savedDO.getDescriptorClass()).isEqualTo("1"); // TOPICAL code = "1" (String type)
      assertThat(savedDO.getMeshVersion()).isEqualTo("2025");
    }

    @Test
    @DisplayName("多条记录 - 应该正确批量插入")
    void saveBatch_multipleRecords_shouldInsertAllSuccessfully() {
      // Given: 创建多个主题词聚合根
      List<MeshDescriptorAggregate> descriptors =
          List.of(
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(1), "Descriptor 1", DescriptorClass.TOPICAL, "2025"),
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(2), "Descriptor 2", DescriptorClass.PUBLICATION_TYPE, "2025"),
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(3), "Descriptor 3", DescriptorClass.CHECK_TAG, "2025"));

      // When: 批量保存
      repository.saveBatch(descriptors);

      // Then: 数据库应该有3条记录
      long count = meshDescriptorMapper.selectCount(null);
      assertThat(count).isEqualTo(3);

      // Then: 验证所有记录
      List<MeshDescriptorDO> saved = meshDescriptorMapper.selectList(null);
      assertThat(saved).hasSize(3);

      // 验证UI唯一性
      assertThat(saved)
          .extracting(MeshDescriptorDO::getUi)
          .containsExactlyInAnyOrder("D000001", "D000002", "D000003");

      // 验证所有记录都有ID
      assertThat(saved).allMatch(d -> d.getId() != null);
    }
  }

  // ==================== truncateAll 测试 ====================

  @Nested
  @DisplayName("truncateAll() 测试")
  class TruncateAllTest {

    @Test
    @DisplayName("空表 - 应该不抛出异常")
    void truncateAll_emptyTables_shouldNotThrowException() {
      // Given: 所有表都是空的
      assertThat(meshDescriptorMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshTreeNumberMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshEntryTermMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshConceptMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshConceptRelationMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshEntryCombinationMapper.selectCount(null)).isEqualTo(0);

      // When: 清空空表
      repository.truncateAll();

      // Then: 不抛出异常，所有表仍然为空
      assertThat(meshDescriptorMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshTreeNumberMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshEntryTermMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshConceptMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshConceptRelationMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshEntryCombinationMapper.selectCount(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("有数据 - 应该清空所有关联表")
    void truncateAll_withData_shouldDeleteAllRecords() {
      // Given: 插入测试数据到各表
      // 1. 先插入主表数据（Descriptor）
      List<MeshDescriptorAggregate> descriptors =
          List.of(
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(1), "Descriptor 1", DescriptorClass.TOPICAL, "2025"),
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(2), "Descriptor 2", DescriptorClass.TOPICAL, "2025"));
      repository.saveBatch(descriptors);

      // 2. 插入子表数据
      List<MeshTreeNumber> treeNumbers =
          List.of(
              MeshTreeNumber.create(MeshUI.descriptorOf(1), "C04.557.337", true),
              MeshTreeNumber.create(MeshUI.descriptorOf(2), "A01.236.500", true));
      repository.saveTreeNumbersBatch(treeNumbers);

      List<MeshEntryTerm> entryTerms =
          List.of(
              MeshEntryTerm.create(
                  MeshUI.descriptorOf(1),
                  MeshUI.termOf(1),
                  "Entry Term 1",
                  LexicalTag.PEF,
                  false,
                  false,
                  false,
                  false),
              MeshEntryTerm.create(
                  MeshUI.descriptorOf(2),
                  MeshUI.termOf(2),
                  "Entry Term 2",
                  LexicalTag.NON,
                  false,
                  false,
                  false,
                  false));
      repository.saveEntryTermsBatch(entryTerms);

      List<MeshConcept> concepts =
          List.of(
              MeshConcept.create(MeshUI.descriptorOf(1), MeshUI.conceptOf(1), "Concept 1", true),
              MeshConcept.create(MeshUI.descriptorOf(2), MeshUI.conceptOf(2), "Concept 2", true));
      repository.saveConceptsBatch(concepts);

      // 验证数据已插入
      assertThat(meshDescriptorMapper.selectCount(null)).isEqualTo(2);
      assertThat(meshTreeNumberMapper.selectCount(null)).isEqualTo(2);
      assertThat(meshEntryTermMapper.selectCount(null)).isEqualTo(2);
      assertThat(meshConceptMapper.selectCount(null)).isEqualTo(2);

      // When: 清空所有表
      repository.truncateAll();

      // Then: 所有表都应该为空
      assertThat(meshDescriptorMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshTreeNumberMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshEntryTermMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshConceptMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshConceptRelationMapper.selectCount(null)).isEqualTo(0);
      assertThat(meshEntryCombinationMapper.selectCount(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("清空后重新插入 - 应该能正常保存新数据")
    void truncateAll_thenSaveBatch_shouldSaveNewDataSuccessfully() {
      // Given: 插入初始数据
      List<MeshDescriptorAggregate> oldDescriptors =
          List.of(
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(1), "Old Descriptor 1", DescriptorClass.TOPICAL, "2024"),
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(2), "Old Descriptor 2", DescriptorClass.TOPICAL, "2024"));
      repository.saveBatch(oldDescriptors);

      List<MeshTreeNumber> oldTreeNumbers =
          List.of(MeshTreeNumber.create(MeshUI.descriptorOf(1), "C04.557.337", true));
      repository.saveTreeNumbersBatch(oldTreeNumbers);

      // 验证数据已插入
      assertThat(meshDescriptorMapper.selectCount(null)).isEqualTo(2);
      assertThat(meshTreeNumberMapper.selectCount(null)).isEqualTo(1);

      // When: 清空所有表后插入新数据
      repository.truncateAll();

      List<MeshDescriptorAggregate> newDescriptors =
          List.of(
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(10), "New Descriptor 1", DescriptorClass.TOPICAL, "2025"),
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(20), "New Descriptor 2", DescriptorClass.GEOGRAPHICALS, "2025"),
              MeshDescriptorAggregate.create(
                  MeshUI.descriptorOf(30), "New Descriptor 3", DescriptorClass.TOPICAL, "2025"));
      repository.saveBatch(newDescriptors);

      List<MeshTreeNumber> newTreeNumbers =
          List.of(
              MeshTreeNumber.create(MeshUI.descriptorOf(10), "A01.236.500", true),
              MeshTreeNumber.create(MeshUI.descriptorOf(20), "B01.050.150", false));
      repository.saveTreeNumbersBatch(newTreeNumbers);

      // Then: 应该只有新数据
      assertThat(meshDescriptorMapper.selectCount(null)).isEqualTo(3);
      assertThat(meshTreeNumberMapper.selectCount(null)).isEqualTo(2);

      List<MeshDescriptorDO> savedDescriptors = meshDescriptorMapper.selectList(null);
      assertThat(savedDescriptors)
          .extracting(MeshDescriptorDO::getName)
          .containsExactlyInAnyOrder("New Descriptor 1", "New Descriptor 2", "New Descriptor 3");
      assertThat(savedDescriptors).allMatch(d -> "2025".equals(d.getMeshVersion()));

      List<MeshTreeNumberDO> savedTreeNumbers = meshTreeNumberMapper.selectList(null);
      assertThat(savedTreeNumbers)
          .extracting(MeshTreeNumberDO::getTreeNumber)
          .containsExactlyInAnyOrder("A01.236.500", "B01.050.150");
    }
  }
}
