package com.patra.catalog.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// MeshTreeNumber 实体单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试工厂方法、业务方法、验证逻辑、equals/hashCode
///
/// @author linqibin
/// @since 0.2.1
@DisplayName("MeshTreeNumber 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshTreeNumberTest {

  // ========== 测试数据 ==========

  /// 有效的树形编号示例
  private static final String VALID_TREE_NUMBER_LEVEL_1 = "C04";
  private static final String VALID_TREE_NUMBER_LEVEL_2 = "C04.557";
  private static final String VALID_TREE_NUMBER_LEVEL_3 = "C04.557.337";
  private static final String VALID_TREE_NUMBER_LEVEL_4 = "C04.557.337.428";

  /// 测试用 MeshUI
  private static final MeshUI DESCRIPTOR_UI = MeshUI.of("D000001");

  @Nested
  @DisplayName("create() 工厂方法测试")
  class CreateTests {

    @Test
    @DisplayName("应该正确创建树形编号（无 descriptorUi）")
    void shouldCreateTreeNumberWithoutDescriptorUi() {
      // When
      MeshTreeNumber treeNumber = MeshTreeNumber.create(VALID_TREE_NUMBER_LEVEL_4, true);

      // Then
      assertThat(treeNumber).isNotNull();
      assertThat(treeNumber.getId()).isNull();
      assertThat(treeNumber.getDescriptorUi()).isNull();
      assertThat(treeNumber.getTreeNumber()).isEqualTo(VALID_TREE_NUMBER_LEVEL_4);
      assertThat(treeNumber.getTreeLevel()).isEqualTo(4);
      assertThat(treeNumber.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("应该正确创建树形编号（带 descriptorUi）")
    void shouldCreateTreeNumberWithDescriptorUi() {
      // When
      MeshTreeNumber treeNumber =
          MeshTreeNumber.create(DESCRIPTOR_UI, VALID_TREE_NUMBER_LEVEL_3, false);

      // Then
      assertThat(treeNumber).isNotNull();
      assertThat(treeNumber.getId()).isNull();
      assertThat(treeNumber.getDescriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(treeNumber.getTreeNumber()).isEqualTo(VALID_TREE_NUMBER_LEVEL_3);
      assertThat(treeNumber.getTreeLevel()).isEqualTo(3);
      assertThat(treeNumber.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("应该自动计算层级深度（1层）")
    void shouldCalculateLevelForLevel1() {
      // When
      MeshTreeNumber treeNumber = MeshTreeNumber.create(VALID_TREE_NUMBER_LEVEL_1, true);

      // Then
      assertThat(treeNumber.getTreeLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该自动计算层级深度（4层）")
    void shouldCalculateLevelForLevel4() {
      // When
      MeshTreeNumber treeNumber = MeshTreeNumber.create(VALID_TREE_NUMBER_LEVEL_4, true);

      // Then
      assertThat(treeNumber.getTreeLevel()).isEqualTo(4);
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法测试")
  class RestoreTests {

    @Test
    @DisplayName("应该从持久化状态重建实体")
    void shouldRestoreEntityFromPersistedState() {
      // Given
      Long id = 12345L;
      int treeLevel = 4;
      boolean isPrimary = true;

      // When
      MeshTreeNumber treeNumber =
          MeshTreeNumber.restore(
              id, DESCRIPTOR_UI, VALID_TREE_NUMBER_LEVEL_4, treeLevel, isPrimary);

      // Then
      assertThat(treeNumber.getId()).isEqualTo(id);
      assertThat(treeNumber.getDescriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(treeNumber.getTreeNumber()).isEqualTo(VALID_TREE_NUMBER_LEVEL_4);
      assertThat(treeNumber.getTreeLevel()).isEqualTo(treeLevel);
      assertThat(treeNumber.isPrimary()).isTrue();
    }
  }

  @Nested
  @DisplayName("验证逻辑测试")
  class ValidationTests {

    @Test
    @DisplayName("树形编号为空时应该抛出异常")
    void shouldThrowWhenTreeNumberIsBlank() {
      assertThatThrownBy(() -> MeshTreeNumber.create("", true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("树形编号不能为空");
    }

    @Test
    @DisplayName("树形编号为 null 时应该抛出异常")
    void shouldThrowWhenTreeNumberIsNull() {
      assertThatThrownBy(() -> MeshTreeNumber.create(null, true))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("树形编号格式无效时应该抛出异常（小写字母）")
    void shouldThrowWhenTreeNumberHasLowercaseLetter() {
      assertThatThrownBy(() -> MeshTreeNumber.create("c04.557", true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("树形编号格式无效");
    }

    @Test
    @DisplayName("树形编号格式无效时应该抛出异常（错误数字位数）")
    void shouldThrowWhenTreeNumberHasWrongDigitCount() {
      // 首段应该是2位数字，不是1位
      assertThatThrownBy(() -> MeshTreeNumber.create("C4.557", true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("树形编号格式无效");
    }

    @Test
    @DisplayName("树形编号格式无效时应该抛出异常（子段位数超过3位）")
    void shouldThrowWhenTreeNumberHasMoreThanThreeDigitsInSubsection() {
      // 子段最多3位数字，不能是4位
      assertThatThrownBy(() -> MeshTreeNumber.create("C04.5570", true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("树形编号格式无效");
    }

    @Test
    @DisplayName("有效的树形编号格式应该通过验证（包括 MeSH 2026 新格式）")
    void shouldPassValidationForValidFormats() {
      // 传统格式（每段3位数字）
      assertThat(MeshTreeNumber.create("A01", true)).isNotNull();
      assertThat(MeshTreeNumber.create("C04.557", true)).isNotNull();
      assertThat(MeshTreeNumber.create("D12.776.124", true)).isNotNull();
      assertThat(MeshTreeNumber.create("E05.318.740.600.800", true)).isNotNull();

      // MeSH 2026 新格式（支持 1-2 位数字段）
      assertThat(MeshTreeNumber.create("B04.820.578.688.2.150", true)).isNotNull();
      assertThat(MeshTreeNumber.create("C04.1.2.3", true)).isNotNull();
      assertThat(MeshTreeNumber.create("D12.77.12", true)).isNotNull();
    }

    @Test
    @DisplayName("MeSH 2026 新格式应该正确计算层级深度")
    void shouldCalculateLevelForMesh2026Format() {
      // B04.820.578.688.2.150 有 6 个段，层级应该是 6
      MeshTreeNumber treeNumber = MeshTreeNumber.create("B04.820.578.688.2.150", true);
      assertThat(treeNumber.getTreeLevel()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("assignId() 方法测试")
  class AssignIdTests {

    @Test
    @DisplayName("应该正确设置 ID")
    void shouldAssignId() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create(VALID_TREE_NUMBER_LEVEL_4, true);
      Long newId = 99999L;

      // When
      treeNumber.assignId(newId);

      // Then
      assertThat(treeNumber.getId()).isEqualTo(newId);
    }
  }

  @Nested
  @DisplayName("getRootCategory() 方法测试")
  class GetRootCategoryTests {

    @Test
    @DisplayName("应该返回树形编号的根分类")
    void shouldReturnRootCategory() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337.428", true);

      // When
      String rootCategory = treeNumber.getRootCategory();

      // Then
      assertThat(rootCategory).isEqualTo("C");
    }

    @Test
    @DisplayName("顶层节点也应该返回正确的根分类")
    void shouldReturnRootCategoryForTopLevel() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("D12", true);

      // When
      String rootCategory = treeNumber.getRootCategory();

      // Then
      assertThat(rootCategory).isEqualTo("D");
    }
  }

  @Nested
  @DisplayName("getTopLevelCategory() 方法测试")
  class GetTopLevelCategoryTests {

    @Test
    @DisplayName("应该返回树形编号的顶层分类")
    void shouldReturnTopLevelCategory() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337.428", true);

      // When
      String topLevelCategory = treeNumber.getTopLevelCategory();

      // Then
      assertThat(topLevelCategory).isEqualTo("C04");
    }

    @Test
    @DisplayName("顶层节点应该返回自身")
    void shouldReturnSelfForTopLevel() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("D12", true);

      // When
      String topLevelCategory = treeNumber.getTopLevelCategory();

      // Then
      assertThat(topLevelCategory).isEqualTo("D12");
    }
  }

  @Nested
  @DisplayName("getParentTreeNumber() 方法测试")
  class GetParentTreeNumberTests {

    @Test
    @DisplayName("应该返回父级树形编号")
    void shouldReturnParentTreeNumber() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337.428", true);

      // When
      String parent = treeNumber.getParentTreeNumber();

      // Then
      assertThat(parent).isEqualTo("C04.557.337");
    }

    @Test
    @DisplayName("二级节点应该返回顶层分类")
    void shouldReturnTopLevelForSecondLevel() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557", true);

      // When
      String parent = treeNumber.getParentTreeNumber();

      // Then
      assertThat(parent).isEqualTo("C04");
    }

    @Test
    @DisplayName("顶层节点应该返回 null")
    void shouldReturnNullForTopLevel() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04", true);

      // When
      String parent = treeNumber.getParentTreeNumber();

      // Then
      assertThat(parent).isNull();
    }
  }

  @Nested
  @DisplayName("isTopLevel() 方法测试")
  class IsTopLevelTests {

    @Test
    @DisplayName("顶层节点应该返回 true")
    void shouldReturnTrueForTopLevel() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04", true);

      // When & Then
      assertThat(treeNumber.isTopLevel()).isTrue();
    }

    @Test
    @DisplayName("非顶层节点应该返回 false")
    void shouldReturnFalseForNonTopLevel() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557", true);

      // When & Then
      assertThat(treeNumber.isTopLevel()).isFalse();
    }
  }

  @Nested
  @DisplayName("belongsToBranch() 方法测试")
  class BelongsToBranchTests {

    @Test
    @DisplayName("属于指定分支时应该返回 true")
    void shouldReturnTrueWhenBelongsToBranch() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337.428", true);

      // When & Then
      assertThat(treeNumber.belongsToBranch("C04")).isTrue();
      assertThat(treeNumber.belongsToBranch("C04.557")).isTrue();
      assertThat(treeNumber.belongsToBranch("C04.557.337")).isTrue();
    }

    @Test
    @DisplayName("不属于指定分支时应该返回 false")
    void shouldReturnFalseWhenNotBelongsToBranch() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337.428", true);

      // When & Then
      assertThat(treeNumber.belongsToBranch("C08")).isFalse();
      assertThat(treeNumber.belongsToBranch("D04")).isFalse();
    }

    @Test
    @DisplayName("完全匹配时应该返回 true")
    void shouldReturnTrueForExactMatch() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557", true);

      // When & Then
      assertThat(treeNumber.belongsToBranch("C04.557")).isTrue();
    }
  }

  @Nested
  @DisplayName("equals() 和 hashCode() 方法测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同树形编号（无 descriptorUi）应该相等")
    void shouldBeEqualForSameTreeNumberWithoutDescriptorUi() {
      // Given
      MeshTreeNumber treeNumber1 = MeshTreeNumber.create("C04.557.337", true);
      MeshTreeNumber treeNumber2 = MeshTreeNumber.create("C04.557.337", false);

      // When & Then
      assertThat(treeNumber1).isEqualTo(treeNumber2);
      assertThat(treeNumber1.hashCode()).isEqualTo(treeNumber2.hashCode());
    }

    @Test
    @DisplayName("相同树形编号 + 相同 descriptorUi 应该相等")
    void shouldBeEqualForSameTreeNumberAndSameDescriptorUi() {
      // Given
      MeshUI descriptorUi = MeshUI.of("D000001");
      MeshTreeNumber treeNumber1 = MeshTreeNumber.create(descriptorUi, "C04.557.337", true);
      MeshTreeNumber treeNumber2 = MeshTreeNumber.create(descriptorUi, "C04.557.337", false);

      // When & Then
      assertThat(treeNumber1).isEqualTo(treeNumber2);
      assertThat(treeNumber1.hashCode()).isEqualTo(treeNumber2.hashCode());
    }

    @Test
    @DisplayName("相同树形编号 + 不同 descriptorUi 应该不相等（MeSH 2026 共享树形位置）")
    void shouldNotBeEqualForSameTreeNumberButDifferentDescriptorUi() {
      // Given - MeSH 2026 允许同一个树形编号属于多个 Descriptor
      MeshTreeNumber treeNumber1 =
          MeshTreeNumber.create(MeshUI.of("D000001"), "B03.300.390.400.001", true);
      MeshTreeNumber treeNumber2 =
          MeshTreeNumber.create(MeshUI.of("D000002"), "B03.300.390.400.001", true);

      // When & Then
      assertThat(treeNumber1).isNotEqualTo(treeNumber2);
    }

    @Test
    @DisplayName("相同树形编号，一方有 descriptorUi 一方无，应该相等（聚合内部场景）")
    void shouldBeEqualWhenOneHasDescriptorUiAndOtherDoesNot() {
      // Given - 聚合内部比较场景
      MeshTreeNumber treeNumber1 = MeshTreeNumber.create("C04.557.337", true);
      MeshTreeNumber treeNumber2 =
          MeshTreeNumber.create(MeshUI.of("D000001"), "C04.557.337", false);

      // When & Then
      assertThat(treeNumber1).isEqualTo(treeNumber2);
    }

    @Test
    @DisplayName("不同树形编号应该不相等")
    void shouldNotBeEqualForDifferentTreeNumber() {
      // Given
      MeshTreeNumber treeNumber1 = MeshTreeNumber.create("C04.557.337", true);
      MeshTreeNumber treeNumber2 = MeshTreeNumber.create("C04.557.338", true);

      // When & Then
      assertThat(treeNumber1).isNotEqualTo(treeNumber2);
    }

    @Test
    @DisplayName("与 null 比较应该不相等")
    void shouldNotBeEqualToNull() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337", true);

      // When & Then
      assertThat(treeNumber).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与自身比较应该相等")
    void shouldBeEqualToSelf() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337", true);

      // When & Then
      assertThat(treeNumber).isEqualTo(treeNumber);
    }
  }

  @Nested
  @DisplayName("toString() 方法测试")
  class ToStringTests {

    @Test
    @DisplayName("应该返回有意义的字符串表示")
    void shouldReturnMeaningfulString() {
      // Given
      MeshTreeNumber treeNumber = MeshTreeNumber.create("C04.557.337", true);

      // When
      String result = treeNumber.toString();

      // Then
      assertThat(result).contains("C04.557.337");
      assertThat(result).contains("level=3");
      assertThat(result).contains("primary=true");
    }
  }
}
