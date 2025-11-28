package com.patra.catalog.infra.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.config.CatalogMySQLContainerInitializer;
import com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshConceptRelationDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryCombinationDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshConceptRelationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryCombinationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// MeSH 主题词批量写入器集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试批量写入操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法独立
///   - TestContainers：自动启动和停止 MySQL 容器
///   - 测试覆盖：write() 的各种场景
///
/// **重点测试场景**：
///
/// - write() 单个 Descriptor：验证主表和子表数据正确写入
///   - write() 批量 Descriptor：验证批量写入正确性
///   - write() 空 Chunk：验证空数据处理
///   - write() 含全部关联实体：验证 TreeNumber/Concept/EntryTerm 完整写入
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshDescriptorItemWriter.class,
  MeshDescriptorConverter.class,
  TestMybatisPlusAutoConfiguration.class
})
@MapperScan("com.patra.catalog.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("MeshDescriptorItemWriter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshDescriptorItemWriterIT {

  @Autowired private MeshDescriptorItemWriter meshDescriptorItemWriter;

  @Autowired private MeshDescriptorMapper descriptorMapper;
  @Autowired private MeshTreeNumberMapper treeNumberMapper;
  @Autowired private MeshConceptMapper conceptMapper;
  @Autowired private MeshConceptRelationMapper conceptRelationMapper;
  @Autowired private MeshEntryTermMapper entryTermMapper;
  @Autowired private MeshEntryCombinationMapper entryCombinationMapper;

  @Test
  @DisplayName("write() 空 Chunk - 应该不抛出异常，直接返回")
  void write_emptyChunk_shouldReturnWithoutError() throws Exception {
    // Given: 空 Chunk
    Chunk<MeshDescriptorAggregate> emptyChunk = new Chunk<>();

    // When: 写入空 Chunk
    meshDescriptorItemWriter.write(emptyChunk);

    // Then: 不抛出异常，数据库应该没有记录
    long count = descriptorMapper.selectCount(null);
    assertThat(count).isEqualTo(0);
  }

  @Test
  @DisplayName("write() 单个 Descriptor - 应该正确写入主表和子表")
  void write_singleDescriptor_shouldInsertAllTables() throws Exception {
    // Given: 创建包含所有关联实体的 Descriptor
    MeshDescriptorAggregate descriptor = createTestDescriptor(1);

    Chunk<MeshDescriptorAggregate> chunk = new Chunk<>(List.of(descriptor));

    // When: 写入 Chunk
    meshDescriptorItemWriter.write(chunk);

    // Then: 验证主表数据
    long descriptorCount = descriptorMapper.selectCount(null);
    assertThat(descriptorCount).isEqualTo(1);

    MeshDescriptorDO savedDescriptor = descriptorMapper.selectList(null).get(0);
    assertThat(savedDescriptor.getUi()).isEqualTo("D000001");
    assertThat(savedDescriptor.getName()).isEqualTo("Test Descriptor 1");
    assertThat(savedDescriptor.getDescriptorClass()).isEqualTo("1");
    assertThat(savedDescriptor.getMeshVersion()).isEqualTo("2025");

    // Then: 验证 TreeNumber 子表
    long treeNumberCount = treeNumberMapper.selectCount(null);
    assertThat(treeNumberCount).isEqualTo(2);

    List<MeshTreeNumberDO> treeNumbers = treeNumberMapper.selectList(null);
    assertThat(treeNumbers)
        .extracting(MeshTreeNumberDO::getTreeNumber)
        .containsExactlyInAnyOrder("C01.001", "D01.001");

    // Then: 验证 Concept 子表
    long conceptCount = conceptMapper.selectCount(null);
    assertThat(conceptCount).isEqualTo(1);

    MeshConceptDO concept = conceptMapper.selectList(null).get(0);
    assertThat(concept.getConceptUi()).isEqualTo("M0000001");
    assertThat(concept.getIsPreferred()).isTrue();
    // 验证 RelatedRegistryNumbers 被正确持久化
    assertThat(concept.getRelatedRegistryNumbers())
        .containsExactlyInAnyOrder("EC 1.1.1.1", "EC 2.2.2.1");

    // Then: 验证 EntryTerm 子表
    long entryTermCount = entryTermMapper.selectCount(null);
    assertThat(entryTermCount).isEqualTo(2);

    List<MeshEntryTermDO> entryTerms = entryTermMapper.selectList(null);
    assertThat(entryTerms)
        .extracting(MeshEntryTermDO::getTerm)
        .containsExactlyInAnyOrder("Synonym 1", "Synonym 2");

    // Then: 验证 EntryCombination 子表
    long entryCombinationCount = entryCombinationMapper.selectCount(null);
    assertThat(entryCombinationCount).isEqualTo(2);

    List<MeshEntryCombinationDO> entryCombinations = entryCombinationMapper.selectList(null);
    // 验证第一个 EntryCombination（有 ECOUT Qualifier）
    MeshEntryCombinationDO ec1 =
        entryCombinations.stream()
            .filter(ec -> ec.getEcinQualifierUi().equals("Q000188"))
            .findFirst()
            .orElseThrow();
    assertThat(ec1.getEcinDescriptorUi()).isEqualTo("D000001");
    assertThat(ec1.getEcoutDescriptorUi()).isEqualTo("D000101");
    assertThat(ec1.getEcoutQualifierUi()).isEqualTo("Q000628");

    // 验证第二个 EntryCombination（无 ECOUT Qualifier）
    MeshEntryCombinationDO ec2 =
        entryCombinations.stream()
            .filter(ec -> ec.getEcinQualifierUi().equals("Q000175"))
            .findFirst()
            .orElseThrow();
    assertThat(ec2.getEcinDescriptorUi()).isEqualTo("D000001");
    assertThat(ec2.getEcoutDescriptorUi()).isEqualTo("D000201");
    assertThat(ec2.getEcoutQualifierUi()).isNull();

    // Then: 验证 ConceptRelation 子表
    long conceptRelationCount = conceptRelationMapper.selectCount(null);
    assertThat(conceptRelationCount).isEqualTo(2);

    List<MeshConceptRelationDO> conceptRelations = conceptRelationMapper.selectList(null);
    // 验证首选概念的关系（relationName = NRW）
    MeshConceptRelationDO cr1 =
        conceptRelations.stream()
            .filter(cr -> "NRW".equals(cr.getRelationName()))
            .findFirst()
            .orElseThrow();
    assertThat(cr1.getConceptUi()).isEqualTo("M0000001");
    assertThat(cr1.getIsPreferred()).isTrue();
    assertThat(cr1.getConcept1Ui()).isEqualTo("M0000001");
    assertThat(cr1.getConcept2Ui()).isEqualTo("M0000002");

    // 验证 relationName 为 null 的关系
    MeshConceptRelationDO cr2 =
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

    Chunk<MeshDescriptorAggregate> chunk = new Chunk<>(descriptors);

    // When: 批量写入
    meshDescriptorItemWriter.write(chunk);

    // Then: 验证主表数据
    long descriptorCount = descriptorMapper.selectCount(null);
    assertThat(descriptorCount).isEqualTo(3);

    List<MeshDescriptorDO> savedDescriptors = descriptorMapper.selectList(null);
    assertThat(savedDescriptors)
        .extracting(MeshDescriptorDO::getUi)
        .containsExactlyInAnyOrder("D000001", "D000002", "D000003");

    // Then: 验证子表数据（每个 Descriptor 2 个 TreeNumber）
    long treeNumberCount = treeNumberMapper.selectCount(null);
    assertThat(treeNumberCount).isEqualTo(6);

    // Then: 验证子表数据（每个 Descriptor 1 个 Concept）
    long conceptCount = conceptMapper.selectCount(null);
    assertThat(conceptCount).isEqualTo(3);

    // Then: 验证子表数据（每个 Descriptor 2 个 EntryTerm）
    long entryTermCount = entryTermMapper.selectCount(null);
    assertThat(entryTermCount).isEqualTo(6);

    // Then: 验证子表数据（每个 Descriptor 2 个 EntryCombination）
    long entryCombinationCount = entryCombinationMapper.selectCount(null);
    assertThat(entryCombinationCount).isEqualTo(6);

    // Then: 验证子表数据（每个 Descriptor 2 个 ConceptRelation）
    long conceptRelationCount = conceptRelationMapper.selectCount(null);
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

    Chunk<MeshDescriptorAggregate> chunk = new Chunk<>(List.of(descriptor));

    // When: 写入
    meshDescriptorItemWriter.write(chunk);

    // Then: 验证主表数据
    long descriptorCount = descriptorMapper.selectCount(null);
    assertThat(descriptorCount).isEqualTo(1);

    MeshDescriptorDO savedDescriptor = descriptorMapper.selectList(null).get(0);
    assertThat(savedDescriptor.getUi()).isEqualTo("D000099");
    assertThat(savedDescriptor.getScopeNote()).isNull();

    // Then: 验证 TreeNumber
    long treeNumberCount = treeNumberMapper.selectCount(null);
    assertThat(treeNumberCount).isEqualTo(1);

    // Then: 验证无 Concept、ConceptRelation、EntryTerm 和 EntryCombination
    long conceptCount = conceptMapper.selectCount(null);
    assertThat(conceptCount).isEqualTo(0);

    long conceptRelationCount = conceptRelationMapper.selectCount(null);
    assertThat(conceptRelationCount).isEqualTo(0);

    long entryTermCount = entryTermMapper.selectCount(null);
    assertThat(entryTermCount).isEqualTo(0);

    long entryCombinationCount = entryCombinationMapper.selectCount(null);
    assertThat(entryCombinationCount).isEqualTo(0);
  }

  @Test
  @DisplayName("write() 子表关联正确性 - 验证 descriptorUi 关联键正确设置")
  void write_shouldSetCorrectRelationKeys() throws Exception {
    // Given: 创建一个 Descriptor
    MeshDescriptorAggregate descriptor = createTestDescriptor(1);

    Chunk<MeshDescriptorAggregate> chunk = new Chunk<>(List.of(descriptor));

    // When: 写入
    meshDescriptorItemWriter.write(chunk);

    // Then: 获取主表 UI
    MeshDescriptorDO savedDescriptor = descriptorMapper.selectList(null).get(0);
    String descriptorUi = savedDescriptor.getUi();
    assertThat(descriptorUi).isNotNull();

    // Then: 验证所有子表的 descriptorUi 关联键
    List<MeshTreeNumberDO> treeNumbers = treeNumberMapper.selectList(null);
    assertThat(treeNumbers).allMatch(tn -> tn.getDescriptorUi().equals(descriptorUi));

    List<MeshConceptDO> concepts = conceptMapper.selectList(null);
    assertThat(concepts).allMatch(c -> c.getDescriptorUi().equals(descriptorUi));

    List<MeshEntryTermDO> entryTerms = entryTermMapper.selectList(null);
    assertThat(entryTerms).allMatch(et -> et.getDescriptorUi().equals(descriptorUi));

    List<MeshEntryCombinationDO> entryCombinations = entryCombinationMapper.selectList(null);
    assertThat(entryCombinations).allMatch(ec -> ec.getDescriptorUi().equals(descriptorUi));

    List<MeshConceptRelationDO> conceptRelations = conceptRelationMapper.selectList(null);
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

    // 添加 EntryTerm（每个 Descriptor 2 个）π
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
