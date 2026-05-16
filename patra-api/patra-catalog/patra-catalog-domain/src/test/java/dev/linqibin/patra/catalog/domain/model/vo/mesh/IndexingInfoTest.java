package dev.linqibin.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// IndexingInfo 值对象单元测试。
///
/// 测试 SCR 索引信息值对象。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("IndexingInfo 值对象测试")
class IndexingInfoTest {

  private static final MeshUI DESCRIPTOR_UI = MeshUI.of("D000001");
  private static final MeshUI QUALIFIER_UI = MeshUI.of("Q000002");
  private static final MeshUI CHEMICAL_UI = MeshUI.of("C000003");

  @Nested
  @DisplayName("创建测试")
  class CreationTests {

    @Test
    @DisplayName("应该成功创建只有 descriptorUi 的索引信息")
    void shouldCreateWithDescriptorOnly() {
      // when
      IndexingInfo info = IndexingInfo.ofDescriptor(DESCRIPTOR_UI);

      // then
      assertThat(info.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(info.qualifierUi()).isNull();
      assertThat(info.chemicalUi()).isNull();
    }

    @Test
    @DisplayName("应该成功创建带 qualifierUi 的索引信息")
    void shouldCreateWithQualifier() {
      // when
      IndexingInfo info = IndexingInfo.ofDescriptorWithQualifier(DESCRIPTOR_UI, QUALIFIER_UI);

      // then
      assertThat(info.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(info.qualifierUi()).isEqualTo(QUALIFIER_UI);
      assertThat(info.chemicalUi()).isNull();
    }

    @Test
    @DisplayName("应该成功创建只有 chemicalUi 的索引信息")
    void shouldCreateWithChemicalOnly() {
      // when
      IndexingInfo info = IndexingInfo.ofChemical(CHEMICAL_UI);

      // then
      assertThat(info.descriptorUi()).isNull();
      assertThat(info.qualifierUi()).isNull();
      assertThat(info.chemicalUi()).isEqualTo(CHEMICAL_UI);
    }

    @Test
    @DisplayName("应该成功创建完整的索引信息")
    void shouldCreateComplete() {
      // when
      IndexingInfo info = IndexingInfo.of(DESCRIPTOR_UI, QUALIFIER_UI, CHEMICAL_UI);

      // then
      assertThat(info.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(info.qualifierUi()).isEqualTo(QUALIFIER_UI);
      assertThat(info.chemicalUi()).isEqualTo(CHEMICAL_UI);
    }

    @Test
    @DisplayName("全部为 null 应该抛出异常")
    void shouldRejectAllNull() {
      assertThatThrownBy(() -> IndexingInfo.of(null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("至少需要一个有效的 UI");
    }

    @Test
    @DisplayName("应该验证 descriptorUi 类型")
    void shouldValidateDescriptorType() {
      MeshUI conceptUi = MeshUI.of("M0000001");

      assertThatThrownBy(() -> IndexingInfo.ofDescriptor(conceptUi))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须以D开头");
    }

    @Test
    @DisplayName("应该验证 qualifierUi 类型")
    void shouldValidateQualifierType() {
      MeshUI conceptUi = MeshUI.of("M0000001");

      assertThatThrownBy(() -> IndexingInfo.ofDescriptorWithQualifier(DESCRIPTOR_UI, conceptUi))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须以Q开头");
    }

    @Test
    @DisplayName("应该验证 chemicalUi 类型")
    void shouldValidateChemicalType() {
      MeshUI conceptUi = MeshUI.of("M0000001");

      assertThatThrownBy(() -> IndexingInfo.ofChemical(conceptUi))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须以C开头");
    }
  }

  @Nested
  @DisplayName("辅助方法测试")
  class HelperMethodTests {

    @Test
    @DisplayName("hasDescriptor 应该正确判断")
    void hasDescriptorShouldWork() {
      assertThat(IndexingInfo.ofDescriptor(DESCRIPTOR_UI).hasDescriptor()).isTrue();
      assertThat(IndexingInfo.ofChemical(CHEMICAL_UI).hasDescriptor()).isFalse();
    }

    @Test
    @DisplayName("hasQualifier 应该正确判断")
    void hasQualifierShouldWork() {
      assertThat(IndexingInfo.ofDescriptorWithQualifier(DESCRIPTOR_UI, QUALIFIER_UI).hasQualifier())
          .isTrue();
      assertThat(IndexingInfo.ofDescriptor(DESCRIPTOR_UI).hasQualifier()).isFalse();
    }

    @Test
    @DisplayName("hasChemical 应该正确判断")
    void hasChemicalShouldWork() {
      assertThat(IndexingInfo.ofChemical(CHEMICAL_UI).hasChemical()).isTrue();
      assertThat(IndexingInfo.ofDescriptor(DESCRIPTOR_UI).hasChemical()).isFalse();
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTests {

    @Test
    @DisplayName("相同索引信息应该相等")
    void sameInfoShouldBeEqual() {
      IndexingInfo info1 = IndexingInfo.of(DESCRIPTOR_UI, QUALIFIER_UI, null);
      IndexingInfo info2 = IndexingInfo.of(DESCRIPTOR_UI, QUALIFIER_UI, null);

      assertThat(info1).isEqualTo(info2);
      assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }
  }
}
