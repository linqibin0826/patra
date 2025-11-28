package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.vo.mesh.EntryCombination;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshDescriptorAggregate 聚合根单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 EntryCombination 相关方法的业务逻辑
///
/// @author linqibin
/// @since 0.2.1
@DisplayName("MeshDescriptorAggregate 单元测试")
class MeshDescriptorAggregateTest {

  private MeshDescriptorAggregate aggregate;

  // 测试数据
  private static final MeshUI DESCRIPTOR_UI = MeshUI.of("D005128");
  private static final String DESCRIPTOR_NAME = "Eye Diseases";
  private static final DescriptorClass DESCRIPTOR_CLASS = DescriptorClass.TOPICAL;
  private static final String MESH_VERSION = "2025";

  // EntryCombination 测试数据
  private static final MeshUI ECIN_DESCRIPTOR_1 = MeshUI.of("D005128");
  private static final MeshUI ECIN_QUALIFIER_1 = MeshUI.of("Q000188");
  private static final MeshUI ECOUT_DESCRIPTOR_1 = MeshUI.of("D005128");
  private static final MeshUI ECOUT_QUALIFIER_1 = MeshUI.of("Q000628");

  private static final MeshUI ECIN_DESCRIPTOR_2 = MeshUI.of("D006321");
  private static final MeshUI ECIN_QUALIFIER_2 = MeshUI.of("Q000175");
  private static final MeshUI ECOUT_DESCRIPTOR_2 = MeshUI.of("D006321");

  @BeforeEach
  void setUp() {
    aggregate =
        MeshDescriptorAggregate.create(
            DESCRIPTOR_UI, DESCRIPTOR_NAME, DESCRIPTOR_CLASS, MESH_VERSION);
  }

  @Nested
  @DisplayName("addEntryCombination() 方法测试")
  class AddEntryCombinationTests {

    @Test
    @DisplayName("应该正确添加组合条目")
    void shouldAddEntryCombination() {
      // Given
      EntryCombination combination =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);

      // When
      MeshDescriptorAggregate result = aggregate.addEntryCombination(combination);

      // Then
      assertThat(result).isSameAs(aggregate); // 链式调用
      assertThat(aggregate.getEntryCombinations()).hasSize(1);
      assertThat(aggregate.getEntryCombinations().get(0)).isEqualTo(combination);
    }

    @Test
    @DisplayName("应该去重相同的组合条目（基于 ECIN Descriptor + Qualifier）")
    void shouldDeduplicateSameEntryCombination() {
      // Given
      EntryCombination combination1 =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      EntryCombination combination2 =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, null); // 相同 ECIN，不同 ECOUT

      // When
      aggregate.addEntryCombination(combination1);
      aggregate.addEntryCombination(combination2);

      // Then - 只有第一个被添加，因为 ECIN 相同
      assertThat(aggregate.getEntryCombinations()).hasSize(1);
      assertThat(aggregate.getEntryCombinations().get(0)).isEqualTo(combination1);
    }

    @Test
    @DisplayName("应该允许添加不同 ECIN 的组合条目")
    void shouldAllowDifferentEcinCombinations() {
      // Given
      EntryCombination combination1 =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      EntryCombination combination2 =
          EntryCombination.of(ECIN_DESCRIPTOR_2, ECIN_QUALIFIER_2, ECOUT_DESCRIPTOR_2, null);

      // When
      aggregate.addEntryCombination(combination1);
      aggregate.addEntryCombination(combination2);

      // Then
      assertThat(aggregate.getEntryCombinations()).hasSize(2);
    }

    @Test
    @DisplayName("参数为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEntryCombinationIsNull() {
      assertThatThrownBy(() -> aggregate.addEntryCombination(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("组合条目不能为空");
    }
  }

  @Nested
  @DisplayName("addEntryCombinations() 方法测试")
  class AddEntryCombinationsTests {

    @Test
    @DisplayName("应该批量添加组合条目")
    void shouldAddMultipleEntryCombinations() {
      // Given
      EntryCombination combination1 =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      EntryCombination combination2 =
          EntryCombination.of(ECIN_DESCRIPTOR_2, ECIN_QUALIFIER_2, ECOUT_DESCRIPTOR_2, null);
      List<EntryCombination> combinations = Arrays.asList(combination1, combination2);

      // When
      MeshDescriptorAggregate result = aggregate.addEntryCombinations(combinations);

      // Then
      assertThat(result).isSameAs(aggregate); // 链式调用
      assertThat(aggregate.getEntryCombinations()).hasSize(2);
    }

    @Test
    @DisplayName("空列表应该不影响现有数据")
    void shouldHandleEmptyList() {
      // Given
      EntryCombination combination =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      aggregate.addEntryCombination(combination);

      // When
      aggregate.addEntryCombinations(Collections.emptyList());

      // Then
      assertThat(aggregate.getEntryCombinations()).hasSize(1);
    }

    @Test
    @DisplayName("null 列表应该不影响现有数据")
    void shouldHandleNullList() {
      // Given
      EntryCombination combination =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      aggregate.addEntryCombination(combination);

      // When
      aggregate.addEntryCombinations(null);

      // Then
      assertThat(aggregate.getEntryCombinations()).hasSize(1);
    }

    @Test
    @DisplayName("批量添加时应该去重")
    void shouldDeduplicateWhenBatchAdding() {
      // Given - 两个相同 ECIN 的组合条目
      EntryCombination combination1 =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      EntryCombination combination2 =
          EntryCombination.of(ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, null);
      List<EntryCombination> combinations = Arrays.asList(combination1, combination2);

      // When
      aggregate.addEntryCombinations(combinations);

      // Then - 只有第一个被添加
      assertThat(aggregate.getEntryCombinations()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("getEntryCombinations() 方法测试")
  class GetEntryCombinationsTests {

    @Test
    @DisplayName("应该返回不可变视图")
    void shouldReturnUnmodifiableList() {
      // Given
      EntryCombination combination =
          EntryCombination.of(
              ECIN_DESCRIPTOR_1, ECIN_QUALIFIER_1, ECOUT_DESCRIPTOR_1, ECOUT_QUALIFIER_1);
      aggregate.addEntryCombination(combination);

      // When
      List<EntryCombination> combinations = aggregate.getEntryCombinations();

      // Then
      assertThatThrownBy(() -> combinations.add(combination))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("初始状态应该返回空列表")
    void shouldReturnEmptyListInitially() {
      assertThat(aggregate.getEntryCombinations()).isEmpty();
    }
  }
}
