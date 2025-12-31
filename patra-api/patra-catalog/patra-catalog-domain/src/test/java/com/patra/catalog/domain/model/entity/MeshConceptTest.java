package com.patra.catalog.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.MeshRecordType;
import com.patra.catalog.domain.model.vo.mesh.ConceptRelation;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshConcept 实体单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 ConceptRelation 相关方法的业务逻辑
///
/// @author linqibin
/// @since 0.2.1
@DisplayName("MeshConcept 单元测试")
class MeshConceptTest {

  private MeshConcept concept;

  // 测试数据
  private static final MeshUI CONCEPT_UI = MeshUI.of("M0000001");
  private static final String CONCEPT_NAME = "Calcimycin";
  private static final boolean IS_PREFERRED = true;

  // ConceptRelation 测试数据
  private static final MeshUI CONCEPT_1_UI = MeshUI.of("M0000001");
  private static final MeshUI CONCEPT_2_UI = MeshUI.of("M0353609");
  private static final MeshUI CONCEPT_3_UI = MeshUI.of("M0000002");
  private static final MeshUI CONCEPT_4_UI = MeshUI.of("M0000003");

  @BeforeEach
  void setUp() {
    concept = MeshConcept.create(CONCEPT_UI, CONCEPT_NAME, IS_PREFERRED);
  }

  @Nested
  @DisplayName("addConceptRelation() 方法测试")
  class AddConceptRelationTests {

    @Test
    @DisplayName("应该正确添加概念关系")
    void shouldAddConceptRelation() {
      // Given
      ConceptRelation relation =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);

      // When
      MeshConcept result = concept.addConceptRelation(relation);

      // Then
      assertThat(result).isSameAs(concept); // 链式调用
      assertThat(concept.getConceptRelations()).hasSize(1);
      assertThat(concept.getConceptRelations().get(0)).isEqualTo(relation);
    }

    @Test
    @DisplayName("应该去重相同的概念关系（基于 relationName + concept1Ui + concept2Ui）")
    void shouldDeduplicateSameConceptRelation() {
      // Given - 两个完全相同的关系
      ConceptRelation relation1 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      ConceptRelation relation2 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);

      // When
      concept.addConceptRelation(relation1);
      concept.addConceptRelation(relation2);

      // Then - 只有第一个被添加
      assertThat(concept.getConceptRelations()).hasSize(1);
      assertThat(concept.getConceptRelations().get(0)).isEqualTo(relation1);
    }

    @Test
    @DisplayName("应该允许添加不同的概念关系")
    void shouldAllowDifferentConceptRelations() {
      // Given - 不同的关系类型
      ConceptRelation relation1 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      ConceptRelation relation2 =
          ConceptRelation.of(ConceptRelation.BRD, CONCEPT_3_UI, CONCEPT_4_UI);

      // When
      concept.addConceptRelation(relation1);
      concept.addConceptRelation(relation2);

      // Then
      assertThat(concept.getConceptRelations()).hasSize(2);
    }

    @Test
    @DisplayName("相同概念对但不同关系类型时应该分别保留")
    void shouldKeepDifferentRelationTypesForSameConceptPair() {
      // Given - 相同概念对，不同关系类型
      ConceptRelation relation1 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      ConceptRelation relation2 =
          ConceptRelation.of(ConceptRelation.BRD, CONCEPT_1_UI, CONCEPT_2_UI);

      // When
      concept.addConceptRelation(relation1);
      concept.addConceptRelation(relation2);

      // Then - 两个都被添加，因为关系类型不同
      assertThat(concept.getConceptRelations()).hasSize(2);
    }

    @Test
    @DisplayName("参数为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenConceptRelationIsNull() {
      assertThatThrownBy(() -> concept.addConceptRelation(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("概念关系不能为空");
    }
  }

  @Nested
  @DisplayName("addConceptRelations() 方法测试")
  class AddConceptRelationsTests {

    @Test
    @DisplayName("应该批量添加概念关系")
    void shouldAddMultipleConceptRelations() {
      // Given
      ConceptRelation relation1 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      ConceptRelation relation2 =
          ConceptRelation.of(ConceptRelation.BRD, CONCEPT_3_UI, CONCEPT_4_UI);
      List<ConceptRelation> relations = Arrays.asList(relation1, relation2);

      // When
      MeshConcept result = concept.addConceptRelations(relations);

      // Then
      assertThat(result).isSameAs(concept); // 链式调用
      assertThat(concept.getConceptRelations()).hasSize(2);
    }

    @Test
    @DisplayName("空列表应该不影响现有数据")
    void shouldHandleEmptyList() {
      // Given
      ConceptRelation relation =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      concept.addConceptRelation(relation);

      // When
      concept.addConceptRelations(Collections.emptyList());

      // Then
      assertThat(concept.getConceptRelations()).hasSize(1);
    }

    @Test
    @DisplayName("null 列表应该不影响现有数据")
    void shouldHandleNullList() {
      // Given
      ConceptRelation relation =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      concept.addConceptRelation(relation);

      // When
      concept.addConceptRelations(null);

      // Then
      assertThat(concept.getConceptRelations()).hasSize(1);
    }

    @Test
    @DisplayName("批量添加时应该去重")
    void shouldDeduplicateWhenBatchAdding() {
      // Given - 两个相同的概念关系
      ConceptRelation relation1 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      ConceptRelation relation2 =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      List<ConceptRelation> relations = Arrays.asList(relation1, relation2);

      // When
      concept.addConceptRelations(relations);

      // Then - 只有第一个被添加
      assertThat(concept.getConceptRelations()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("getConceptRelations() 方法测试")
  class GetConceptRelationsTests {

    @Test
    @DisplayName("应该返回不可变视图")
    void shouldReturnUnmodifiableList() {
      // Given
      ConceptRelation relation =
          ConceptRelation.of(ConceptRelation.NRW, CONCEPT_1_UI, CONCEPT_2_UI);
      concept.addConceptRelation(relation);

      // When
      List<ConceptRelation> relations = concept.getConceptRelations();

      // Then
      assertThatThrownBy(() -> relations.add(relation))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("初始状态应该返回空列表")
    void shouldReturnEmptyListInitially() {
      assertThat(concept.getConceptRelations()).isEmpty();
    }
  }

  @Nested
  @DisplayName("recordType 和 ownerUi 字段测试")
  class RecordTypeAndOwnerUiTests {

    @Test
    @DisplayName("为 Descriptor 创建概念时应设置正确的 recordType")
    void shouldSetDescriptorRecordType() {
      // given
      MeshUI descriptorUi = MeshUI.of("D000001");
      MeshUI conceptUi = MeshUI.of("M0000001");

      // when
      MeshConcept concept = MeshConcept.create(descriptorUi, conceptUi, "Test Concept", true);

      // then
      assertThat(concept.getOwnerUi()).isEqualTo(descriptorUi);
      assertThat(concept.getRecordType()).isEqualTo(MeshRecordType.DESCRIPTOR);
    }

    @Test
    @DisplayName("为 SCR 创建概念时应设置正确的 recordType")
    void shouldSetScrRecordType() {
      // given
      MeshUI scrUi = MeshUI.of("C000001");
      MeshUI conceptUi = MeshUI.of("M0000001");

      // when
      MeshConcept concept = MeshConcept.create(scrUi, conceptUi, "Test Concept", true);

      // then
      assertThat(concept.getOwnerUi()).isEqualTo(scrUi);
      assertThat(concept.getRecordType()).isEqualTo(MeshRecordType.SCR);
    }

    @Test
    @DisplayName("不指定 ownerUi 创建概念时 recordType 应为 null")
    void shouldHaveNullRecordTypeWhenNoOwnerUi() {
      // when
      MeshConcept concept = MeshConcept.create(CONCEPT_UI, CONCEPT_NAME, IS_PREFERRED);

      // then
      assertThat(concept.getOwnerUi()).isNull();
      assertThat(concept.getRecordType()).isNull();
    }

    @Test
    @DisplayName("restore 应该正确设置 recordType")
    void restoreShouldSetRecordType() {
      // given
      MeshUI descriptorUi = MeshUI.of("D000001");
      MeshUI conceptUi = MeshUI.of("M0000001");

      // when
      MeshConcept concept =
          MeshConcept.restore(
              1L, // id
              descriptorUi,
              conceptUi,
              "Test Concept",
              true,
              null, // casn1Name
              null, // registryNumbers
              null, // scopeNote
              null, // translatorsEnglishScopeNote
              null, // translatorsScopeNote
              null // conceptStatus
              );

      // then
      assertThat(concept.getOwnerUi()).isEqualTo(descriptorUi);
      assertThat(concept.getRecordType()).isEqualTo(MeshRecordType.DESCRIPTOR);
    }

    @Test
    @DisplayName("通过 getDescriptorUi 向后兼容获取 ownerUi")
    void getDescriptorUiShouldReturnOwnerUi() {
      // given
      MeshUI descriptorUi = MeshUI.of("D000001");
      MeshUI conceptUi = MeshUI.of("M0000001");
      MeshConcept concept = MeshConcept.create(descriptorUi, conceptUi, "Test", true);

      // when & then - getDescriptorUi() 应该与 getOwnerUi() 返回相同值
      assertThat(concept.getDescriptorUi()).isEqualTo(concept.getOwnerUi());
    }
  }
}
