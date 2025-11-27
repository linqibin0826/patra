package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.enums.LexicalTag;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.entity.MeshConceptDO;
import com.patra.catalog.infra.persistence.entity.MeshConceptRelationDO;
import com.patra.catalog.infra.persistence.entity.MeshDescriptorDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryCombinationDO;
import com.patra.catalog.infra.persistence.entity.MeshEntryTermDO;
import com.patra.catalog.infra.persistence.entity.MeshTreeNumberDO;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// MeshDescriptorConverter 单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试各转换方法的正确性
/// - 测试 null 输入处理
/// - 测试可选字段处理
///
/// @author linqibin
/// @since 0.2.1
@DisplayName("MeshDescriptorConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshDescriptorConverterTest {

  private MeshDescriptorConverter converter;

  // 测试数据
  private static final Long DESCRIPTOR_ID = 12345L;
  private static final MeshUI DESCRIPTOR_UI = MeshUI.of("D000001");
  private static final String DESCRIPTOR_NAME = "Test Descriptor";
  private static final DescriptorClass DESCRIPTOR_CLASS = DescriptorClass.TOPICAL;
  private static final String MESH_VERSION = "2025";

  @BeforeEach
  void setUp() {
    converter = new MeshDescriptorConverter();
  }

  @Nested
  @DisplayName("toDescriptorDO() 方法测试")
  class ToDescriptorDOTests {

    @Test
    @DisplayName("应该正确转换聚合根到 DO")
    void shouldConvertAggregateToDO() {
      // Given
      MeshDescriptorAggregate aggregate =
          MeshDescriptorAggregate.create(DESCRIPTOR_UI, DESCRIPTOR_NAME, DESCRIPTOR_CLASS, MESH_VERSION);
      aggregate.setScopeNote("Test scope note");
      aggregate.setAnnotation("Test annotation");
      aggregate.setHistoryNote("Test history note");
      aggregate.setOnlineNote("Test online note");

      // When
      MeshDescriptorDO result = converter.toDescriptorDO(aggregate);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getUi()).isEqualTo("D000001");
      assertThat(result.getName()).isEqualTo(DESCRIPTOR_NAME);
      assertThat(result.getDescriptorClass()).isEqualTo("1");
      assertThat(result.getMeshVersion()).isEqualTo(MESH_VERSION);
      assertThat(result.getScopeNote()).isEqualTo("Test scope note");
      assertThat(result.getAnnotation()).isEqualTo("Test annotation");
      assertThat(result.getHistoryNote()).isEqualTo("Test history note");
      assertThat(result.getOnlineNote()).isEqualTo("Test online note");
      assertThat(result.getActiveStatus()).isTrue();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenAggregateIsNull() {
      assertThat(converter.toDescriptorDO(null)).isNull();
    }
  }

  @Nested
  @DisplayName("toTreeNumberDO() 方法测试")
  class ToTreeNumberDOTests {

    @Test
    @DisplayName("应该正确转换树形编号到 DO")
    void shouldConvertTreeNumberToDO() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("A01.001.002", true);

      // When
      MeshTreeNumberDO result = converter.toTreeNumberDO(treeNumber, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorId()).isEqualTo(DESCRIPTOR_ID);
      assertThat(result.getTreeNumber()).isEqualTo("A01.001.002");
      assertThat(result.getTreeLevel()).isEqualTo(3);
      assertThat(result.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenTreeNumberIsNull() {
      assertThat(converter.toTreeNumberDO(null, DESCRIPTOR_ID)).isNull();
    }
  }

  @Nested
  @DisplayName("toConceptDO() 方法测试")
  class ToConceptDOTests {

    @Test
    @DisplayName("应该正确转换概念到 DO")
    void shouldConvertConceptToDO() {
      // Given
      MeshConcept concept =
          MeshConcept.create(MeshUI.of("M0000001"), "Test Concept", true)
              .withScopeNote("Concept scope note")
              .withCasn1Name("CAS-123456")
              .addRegistryNumber("12345-67-8");

      // When
      MeshConceptDO result = converter.toConceptDO(concept, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorId()).isEqualTo(DESCRIPTOR_ID);
      assertThat(result.getConceptUi()).isEqualTo("M0000001");
      assertThat(result.getConceptName()).isEqualTo("Test Concept");
      assertThat(result.getIsPreferred()).isTrue();
      assertThat(result.getScopeNote()).isEqualTo("Concept scope note");
      assertThat(result.getCasn1Name()).isEqualTo("CAS-123456");
      assertThat(result.getRegistryNumbers()).contains("12345-67-8");
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenConceptIsNull() {
      assertThat(converter.toConceptDO(null, DESCRIPTOR_ID)).isNull();
    }

    @Test
    @DisplayName("应该正确转换 RelatedRegistryNumbers")
    void shouldConvertRelatedRegistryNumbers() {
      // Given
      MeshConcept concept =
          MeshConcept.create(MeshUI.of("M0000001"), "Test Concept", true)
              .addRelatedRegistryNumber("11111-11-1")
              .addRelatedRegistryNumber("22222-22-2");

      // When
      MeshConceptDO result = converter.toConceptDO(concept, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getRelatedRegistryNumbers())
          .containsExactlyInAnyOrder("11111-11-1", "22222-22-2");
    }
  }

  @Nested
  @DisplayName("toEntryTermDO() 方法测试")
  class ToEntryTermDOTests {

    @Test
    @DisplayName("应该正确转换入口术语到 DO")
    void shouldConvertEntryTermToDO() {
      // Given
      MeshEntryTerm entryTerm =
          MeshEntryTerm.create(
                  MeshUI.of("T000001"), "Test Term", LexicalTag.PEF, true, true, true, false)
              .withConceptUi(MeshUI.of("M0000001"))
              .withAbbreviation("TT")
              .withDateCreated("20240101");

      // When
      MeshEntryTermDO result = converter.toEntryTermDO(entryTerm, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorId()).isEqualTo(DESCRIPTOR_ID);
      assertThat(result.getTermUi()).isEqualTo("T000001");
      assertThat(result.getConceptUi()).isEqualTo("M0000001");
      assertThat(result.getTerm()).isEqualTo("Test Term");
      assertThat(result.getLexicalTag()).isEqualTo("PEF");
      assertThat(result.getIsPrintFlag()).isTrue();
      assertThat(result.getRecordPreferred()).isEqualTo("Y");
      assertThat(result.getIsConceptPreferred()).isTrue();
      assertThat(result.getIsPermutedTerm()).isFalse();
      assertThat(result.getAbbreviation()).isEqualTo("TT");
      assertThat(result.getDateCreated()).isEqualTo("20240101");
    }

    @Test
    @DisplayName("可选字段为 null 时应该正确处理")
    void shouldHandleNullOptionalFields() {
      // Given - termUi, conceptUi, lexicalTag 都为 null
      MeshEntryTerm entryTerm =
          MeshEntryTerm.create(null, "Test Term", null, false, true, false, false);

      // When
      MeshEntryTermDO result = converter.toEntryTermDO(entryTerm, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getTermUi()).isNull();
      assertThat(result.getConceptUi()).isNull();
      assertThat(result.getLexicalTag()).isNull();
      assertThat(result.getRecordPreferred()).isEqualTo("N");
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenEntryTermIsNull() {
      assertThat(converter.toEntryTermDO(null, DESCRIPTOR_ID)).isNull();
    }
  }

  @Nested
  @DisplayName("toEntryCombinationDO() 方法测试")
  class ToEntryCombinationDOTests {

    @Test
    @DisplayName("应该正确转换组合条目到 DO（包含 ECOUT Qualifier）")
    void shouldConvertEntryCombinationToDOWithEcoutQualifier() {
      // Given
      EntryCombination entryCombination =
          EntryCombination.of(
              MeshUI.of("D000001"),
              MeshUI.of("Q000188"),
              MeshUI.of("D000002"),
              MeshUI.of("Q000628"));

      // When
      MeshEntryCombinationDO result = converter.toEntryCombinationDO(entryCombination, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorId()).isEqualTo(DESCRIPTOR_ID);
      assertThat(result.getEcinDescriptorUi()).isEqualTo("D000001");
      assertThat(result.getEcinQualifierUi()).isEqualTo("Q000188");
      assertThat(result.getEcoutDescriptorUi()).isEqualTo("D000002");
      assertThat(result.getEcoutQualifierUi()).isEqualTo("Q000628");
    }

    @Test
    @DisplayName("应该正确转换组合条目到 DO（无 ECOUT Qualifier）")
    void shouldConvertEntryCombinationToDOWithoutEcoutQualifier() {
      // Given
      EntryCombination entryCombination =
          EntryCombination.of(MeshUI.of("D000001"), MeshUI.of("Q000188"), MeshUI.of("D000002"));

      // When
      MeshEntryCombinationDO result = converter.toEntryCombinationDO(entryCombination, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getEcinDescriptorUi()).isEqualTo("D000001");
      assertThat(result.getEcinQualifierUi()).isEqualTo("Q000188");
      assertThat(result.getEcoutDescriptorUi()).isEqualTo("D000002");
      assertThat(result.getEcoutQualifierUi()).isNull();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenEntryCombinationIsNull() {
      assertThat(converter.toEntryCombinationDO(null, DESCRIPTOR_ID)).isNull();
    }
  }

  @Nested
  @DisplayName("toConceptRelationDO() 方法测试")
  class ToConceptRelationDOTests {

    // 测试数据
    private static final MeshUI CONCEPT_UI = MeshUI.of("M0000001");
    private static final MeshUI CONCEPT1_UI = MeshUI.of("M0000001");
    private static final MeshUI CONCEPT2_UI = MeshUI.of("M0353609");

    @Test
    @DisplayName("应该正确转换 ConceptRelation 到 DO（包含 relationName）")
    void shouldConvertConceptRelationToDO() {
      // Given
      ConceptRelation relation =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT1_UI, CONCEPT2_UI);

      // When
      MeshConceptRelationDO result =
          converter.toConceptRelationDO(relation, CONCEPT_UI, true, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorId()).isEqualTo(DESCRIPTOR_ID);
      assertThat(result.getConceptUi()).isEqualTo("M0000001");
      assertThat(result.getIsPreferred()).isTrue();
      assertThat(result.getRelationName()).isEqualTo("NRW");
      assertThat(result.getConcept1Ui()).isEqualTo("M0000001");
      assertThat(result.getConcept2Ui()).isEqualTo("M0353609");
    }

    @Test
    @DisplayName("relationName 为 null 时应该正确处理")
    void shouldHandleNullRelationName() {
      // Given - DTD 定义 RelationName 为 #IMPLIED（可选）
      ConceptRelation relation =
          ConceptRelation.ofNullable(CONCEPT1_UI, CONCEPT2_UI, null);

      // When
      MeshConceptRelationDO result =
          converter.toConceptRelationDO(relation, CONCEPT_UI, false, DESCRIPTOR_ID);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getConceptUi()).isEqualTo("M0000001");
      assertThat(result.getIsPreferred()).isFalse();
      assertThat(result.getRelationName()).isNull();
      assertThat(result.getConcept1Ui()).isEqualTo("M0000001");
      assertThat(result.getConcept2Ui()).isEqualTo("M0353609");
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenRelationIsNull() {
      assertThat(converter.toConceptRelationDO(null, CONCEPT_UI, true, DESCRIPTOR_ID)).isNull();
    }
  }
}
