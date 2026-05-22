package dev.linqibin.patra.catalog.infra.batch.mesh;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import dev.linqibin.patra.catalog.domain.model.entity.MeshConcept;
import dev.linqibin.patra.catalog.domain.model.entity.MeshEntryTerm;
import dev.linqibin.patra.catalog.domain.model.entity.MeshTreeNumber;
import dev.linqibin.patra.catalog.domain.model.enums.DescriptorClass;
import dev.linqibin.patra.catalog.domain.model.enums.LexicalTag;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.EntryCombination;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshUI;
import dev.linqibin.patra.catalog.infra.adapter.persistence.MeshDescriptorRepositoryAdapter;
import dev.linqibin.patra.catalog.infra.config.CatalogITPostgreSQLContainerInitializer;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptRelationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshDescriptorDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshEntryCombinationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshEntryTermDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshTreeNumberDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshConceptEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshConceptRelationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshDescriptorEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshEntryCombinationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshEntryTermEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshTreeNumberEntity;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// MeSH 主题词批量写入器集成测试（JPA 版本）。
///
/// 使用 Testcontainers + PostgreSQL 17 测试批量写入操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 PostgreSQL 数据库
/// - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
/// - TestContainers：自动启动和停止 PostgreSQL 容器
/// - 测试覆盖：write() 的各种场景
///
/// **重点测试场景**：
///
/// - write() 单个 Descriptor：验证主表和子表数据正确写入
/// - write() 批量 Descriptor：验证批量写入正确性
/// - write() 空 Chunk：验证空数据处理
/// - write() 含全部关联实体：验证 TreeNumber/Concept/EntryTerm 完整写入
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = CatalogITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshDescriptorItemWriter.class,
  MeshDescriptorRepositoryAdapter.class,
  JpaAuditingConfig.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "dev.linqibin.patra.catalog.infra.persistence.converter")
@ActiveProfiles("test")
@DisplayName("MeshDescriptorItemWriter 集成测试（JPA）")
class MeshDescriptorItemWriterIT {

  @Autowired private MeshDescriptorItemWriter meshDescriptorItemWriter;

  @Autowired private MeshDescriptorDao descriptorDao;
  @Autowired private MeshTreeNumberDao treeNumberDao;
  @Autowired private MeshConceptDao conceptDao;
  @Autowired private MeshConceptRelationDao conceptRelationDao;
  @Autowired private MeshEntryTermDao entryTermDao;
  @Autowired private MeshEntryCombinationDao entryCombinationDao;

  @Test
  @DisplayName("write() 空 Chunk - 应该不抛出异常，直接返回")
  void write_emptyChunk_shouldReturnWithoutError() throws Exception {
    // Given: 空 Chunk
    org.springframework.batch.infrastructure.item.Chunk<MeshDescriptorAggregate> emptyChunk =
        new org.springframework.batch.infrastructure.item.Chunk<>();

    // When: 写入空 Chunk
    meshDescriptorItemWriter.write(emptyChunk);

    // Then: 不抛出异常，数据库应该没有记录
    long count = descriptorDao.count();
    assertThat(count).isEqualTo(0);
  }

  @Test
  @DisplayName("write() 单个 Descriptor - 应该正确写入主表和子表")
  void write_singleDescriptor_shouldInsertAllTables() throws Exception {
    // Given: 创建包含所有关联实体的 Descriptor
    MeshDescriptorAggregate descriptor = createTestDescriptor(1);

    org.springframework.batch.infrastructure.item.Chunk<MeshDescriptorAggregate> chunk =
        new org.springframework.batch.infrastructure.item.Chunk<>(List.of(descriptor));

    // When: 写入 Chunk
    meshDescriptorItemWriter.write(chunk);

    // Then: 验证主表数据
    long descriptorCount = descriptorDao.count();
    assertThat(descriptorCount).isEqualTo(1);

    MeshDescriptorEntity savedDescriptor = descriptorDao.findAll().get(0);
    assertThat(savedDescriptor.getUi()).isEqualTo("D000001");
    assertThat(savedDescriptor.getName()).isEqualTo("Test Descriptor 1");
    assertThat(savedDescriptor.getDescriptorClass()).isEqualTo("1");
    assertThat(savedDescriptor.getMeshVersion()).isEqualTo("2025");

    // Then: 验证 TreeNumber 子表
    long treeNumberCount = treeNumberDao.count();
    assertThat(treeNumberCount).isEqualTo(2);

    List<MeshTreeNumberEntity> treeNumbers = treeNumberDao.findAll();
    assertThat(treeNumbers)
        .extracting(MeshTreeNumberEntity::getTreeNumber)
        .containsExactlyInAnyOrder("C01.001", "D01.001");

    // Then: 验证 Concept 子表
    long conceptCount = conceptDao.count();
    assertThat(conceptCount).isEqualTo(1);

    MeshConceptEntity concept = conceptDao.findAll().get(0);
    assertThat(concept.getConceptUi()).isEqualTo("M0000001");
    assertThat(concept.getIsPreferred()).isTrue();
    // 验证 RelatedRegistryNumbers 被正确持久化
    assertThat(concept.getRelatedRegistryNumbers())
        .containsExactlyInAnyOrder("EC 1.1.1.1", "EC 2.2.2.1");

    // Then: 验证 EntryTerm 子表
    long entryTermCount = entryTermDao.count();
    assertThat(entryTermCount).isEqualTo(2);

    List<MeshEntryTermEntity> entryTerms = entryTermDao.findAll();
    assertThat(entryTerms)
        .extracting(MeshEntryTermEntity::getTerm)
        .containsExactlyInAnyOrder("Synonym 1", "Synonym 2");

    // Then: 验证 EntryCombination 子表
    long entryCombinationCount = entryCombinationDao.count();
    assertThat(entryCombinationCount).isEqualTo(2);

    List<MeshEntryCombinationEntity> entryCombinations = entryCombinationDao.findAll();
    // 验证第一个 EntryCombination（有 ECOUT Qualifier）
    MeshEntryCombinationEntity ec1 =
        entryCombinations.stream()
            .filter(ec -> ec.getEcinQualifierUi().equals("Q000188"))
            .findFirst()
            .orElseThrow();
    assertThat(ec1.getEcinDescriptorUi()).isEqualTo("D000001");
    assertThat(ec1.getEcoutDescriptorUi()).isEqualTo("D000101");
    assertThat(ec1.getEcoutQualifierUi()).isEqualTo("Q000628");

    // 验证第二个 EntryCombination（无 ECOUT Qualifier）
    MeshEntryCombinationEntity ec2 =
        entryCombinations.stream()
            .filter(ec -> ec.getEcinQualifierUi().equals("Q000175"))
            .findFirst()
            .orElseThrow();
    assertThat(ec2.getEcinDescriptorUi()).isEqualTo("D000001");
    assertThat(ec2.getEcoutDescriptorUi()).isEqualTo("D000201");
    assertThat(ec2.getEcoutQualifierUi()).isNull();

    // Then: 验证 ConceptRelation 子表
    long conceptRelationCount = conceptRelationDao.count();
    assertThat(conceptRelationCount).isEqualTo(2);

    List<MeshConceptRelationEntity> conceptRelations = conceptRelationDao.findAll();
    // 验证首选概念的关系（relationName = NRW）
    MeshConceptRelationEntity cr1 =
        conceptRelations.stream()
            .filter(cr -> "NRW".equals(cr.getRelationName()))
            .findFirst()
            .orElseThrow();
    assertThat(cr1.getConceptUi()).isEqualTo("M0000001");
    assertThat(cr1.getIsPreferred()).isTrue();
    assertThat(cr1.getConcept1Ui()).isEqualTo("M0000001");
    assertThat(cr1.getConcept2Ui()).isEqualTo("M0000002");

    // 验证 relationName 为 null 的关系
    MeshConceptRelationEntity cr2 =
        conceptRelations.stream()
            .filter(cr -> cr.getRelationName() == null)
            .findFirst()
            .orElseThrow();
    assertThat(cr2.getConceptUi()).isEqualTo("M0000001");
    assertThat(cr2.getConcept1Ui()).isEqualTo("M0000001");
    assertThat(cr2.getConcept2Ui()).isEqualTo("M0000003");
  }

  @Test
  @DisplayName("write() 批量 Descriptor - 应该正确批量写入")
  void write_multipleDescriptors_shouldInsertAllSuccessfully() throws Exception {
    // Given: 创建 3 个 Descriptor
    List<MeshDescriptorAggregate> descriptors =
        List.of(createTestDescriptor(1), createTestDescriptor(2), createTestDescriptor(3));

    org.springframework.batch.infrastructure.item.Chunk<MeshDescriptorAggregate> chunk =
        new org.springframework.batch.infrastructure.item.Chunk<>(descriptors);

    // When: 批量写入
    meshDescriptorItemWriter.write(chunk);

    // Then: 验证主表数据
    long descriptorCount = descriptorDao.count();
    assertThat(descriptorCount).isEqualTo(3);

    List<MeshDescriptorEntity> savedDescriptors = descriptorDao.findAll();
    assertThat(savedDescriptors)
        .extracting(MeshDescriptorEntity::getUi)
        .containsExactlyInAnyOrder("D000001", "D000002", "D000003");

    // Then: 验证子表数据（每个 Descriptor 2 个 TreeNumber）
    long treeNumberCount = treeNumberDao.count();
    assertThat(treeNumberCount).isEqualTo(6);

    // Then: 验证子表数据（每个 Descriptor 1 个 Concept）
    long conceptCount = conceptDao.count();
    assertThat(conceptCount).isEqualTo(3);

    // Then: 验证子表数据（每个 Descriptor 2 个 EntryTerm）
    long entryTermCount = entryTermDao.count();
    assertThat(entryTermCount).isEqualTo(6);

    // Then: 验证子表数据（每个 Descriptor 2 个 EntryCombination）
    long entryCombinationCount = entryCombinationDao.count();
    assertThat(entryCombinationCount).isEqualTo(6);

    // Then: 验证子表数据（每个 Descriptor 2 个 ConceptRelation）
    long conceptRelationCount = conceptRelationDao.count();
    assertThat(conceptRelationCount).isEqualTo(6);
  }

  @Test
  @DisplayName("write() 只有必填字段的 Descriptor - 应该正确写入")
  void write_minimalDescriptor_shouldInsertSuccessfully() throws Exception {
    // Given: 只包含必填字段的 Descriptor（至少一个 TreeNumber）
    MeshDescriptorAggregate descriptor =
        MeshDescriptorAggregate.create(
                MeshUI.descriptorOf(99), "Minimal Descriptor", DescriptorClass.TOPICAL, "2025")
            .addTreeNumber(MeshTreeNumber.create("A01.001", true));

    org.springframework.batch.infrastructure.item.Chunk<MeshDescriptorAggregate> chunk =
        new org.springframework.batch.infrastructure.item.Chunk<>(List.of(descriptor));

    // When: 写入
    meshDescriptorItemWriter.write(chunk);

    // Then: 验证主表数据
    long descriptorCount = descriptorDao.count();
    assertThat(descriptorCount).isEqualTo(1);

    MeshDescriptorEntity savedDescriptor = descriptorDao.findAll().get(0);
    assertThat(savedDescriptor.getUi()).isEqualTo("D000099");
    assertThat(savedDescriptor.getScopeNote()).isNull();

    // Then: 验证 TreeNumber
    long treeNumberCount = treeNumberDao.count();
    assertThat(treeNumberCount).isEqualTo(1);

    // Then: 验证无 Concept、ConceptRelation、EntryTerm 和 EntryCombination
    long conceptCount = conceptDao.count();
    assertThat(conceptCount).isEqualTo(0);

    long conceptRelationCount = conceptRelationDao.count();
    assertThat(conceptRelationCount).isEqualTo(0);

    long entryTermCount = entryTermDao.count();
    assertThat(entryTermCount).isEqualTo(0);

    long entryCombinationCount = entryCombinationDao.count();
    assertThat(entryCombinationCount).isEqualTo(0);
  }

  @Test
  @DisplayName("write() 子表关联正确性 - 验证 descriptorUi 关联键正确设置")
  void write_shouldSetCorrectRelationKeys() throws Exception {
    // Given: 创建一个 Descriptor
    MeshDescriptorAggregate descriptor = createTestDescriptor(1);

    org.springframework.batch.infrastructure.item.Chunk<MeshDescriptorAggregate> chunk =
        new org.springframework.batch.infrastructure.item.Chunk<>(List.of(descriptor));

    // When: 写入
    meshDescriptorItemWriter.write(chunk);

    // Then: 获取主表 UI
    MeshDescriptorEntity savedDescriptor = descriptorDao.findAll().get(0);
    String descriptorUi = savedDescriptor.getUi();
    assertThat(descriptorUi).isNotNull();

    // Then: 验证所有子表的 descriptorUi 关联键
    List<MeshTreeNumberEntity> treeNumbers = treeNumberDao.findAll();
    assertThat(treeNumbers).allMatch(tn -> tn.getDescriptorUi().equals(descriptorUi));

    List<MeshConceptEntity> concepts = conceptDao.findAll();
    assertThat(concepts).allMatch(c -> c.getOwnerUi().equals(descriptorUi));

    List<MeshEntryTermEntity> entryTerms = entryTermDao.findAll();
    assertThat(entryTerms).allMatch(et -> et.getOwnerUi().equals(descriptorUi));

    List<MeshEntryCombinationEntity> entryCombinations = entryCombinationDao.findAll();
    assertThat(entryCombinations).allMatch(ec -> ec.getDescriptorUi().equals(descriptorUi));

    List<MeshConceptRelationEntity> conceptRelations = conceptRelationDao.findAll();
    assertThat(conceptRelations).allMatch(cr -> cr.getDescriptorUi().equals(descriptorUi));
  }

  /// 创建测试用 MeshDescriptorAggregate。
  ///
  /// @param index 索引号，用于生成唯一的 UI 和名称
  /// @return 测试数据
  private MeshDescriptorAggregate createTestDescriptor(int index) {
    MeshDescriptorAggregate descriptor =
        MeshDescriptorAggregate.create(
            MeshUI.descriptorOf(index),
            "Test Descriptor " + index,
            DescriptorClass.TOPICAL,
            "2025");

    // 添加说明字段
    descriptor.setScopeNote("Scope note for descriptor " + index);
    descriptor.setAnnotation("Annotation for descriptor " + index);

    // 添加 TreeNumber（每个 Descriptor 2 个）
    descriptor.addTreeNumber(MeshTreeNumber.create(String.format("C%02d.00%d", index, 1), true));
    descriptor.addTreeNumber(MeshTreeNumber.create(String.format("D%02d.00%d", index, 1), false));

    // 添加 Concept（每个 Descriptor 1 个首选概念，包含 2 个 ConceptRelation 和 2 个 RelatedRegistryNumber）
    MeshConcept concept =
        MeshConcept.create(MeshUI.conceptOf(index), "Concept " + index, true)
            .withScopeNote("Concept scope note " + index)
            .addConceptRelation(
                ConceptRelation.of(
                    ConceptRelation.NRW, MeshUI.conceptOf(index), MeshUI.conceptOf(index + 1)))
            .addConceptRelation(
                ConceptRelation.ofNullable(
                    MeshUI.conceptOf(index), MeshUI.conceptOf(index + 2), null))
            .addRelatedRegistryNumber("EC 1.1.1." + index)
            .addRelatedRegistryNumber("EC 2.2.2." + index);
    descriptor.addConcept(concept);

    // 添加 EntryTerm（每个 Descriptor 2 个）
    descriptor.addEntryTerm(
        MeshEntryTerm.create(
            MeshUI.termOf(index * 10 + 1), "Synonym 1", LexicalTag.PEF, true, true, true, false));
    descriptor.addEntryTerm(
        MeshEntryTerm.create(
            MeshUI.termOf(index * 10 + 2), "Synonym 2", LexicalTag.NON, false, true, false, false));

    // 添加 EntryCombination（每个 Descriptor 2 个）
    descriptor.addEntryCombination(
        EntryCombination.of(
            MeshUI.descriptorOf(index),
            MeshUI.qualifierOf(188),
            MeshUI.descriptorOf(index + 100),
            MeshUI.qualifierOf(628)));
    descriptor.addEntryCombination(
        EntryCombination.of(
            MeshUI.descriptorOf(index), MeshUI.qualifierOf(175), MeshUI.descriptorOf(index + 200)));

    return descriptor;
  }
}
