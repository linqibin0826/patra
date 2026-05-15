package dev.linqibin.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// HeadingMappedTo 值对象单元测试。
///
/// 测试 SCR 到 Descriptor 的映射关系值对象。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("HeadingMappedTo 值对象测试")
class HeadingMappedToTest {

  private static final MeshUI DESCRIPTOR_UI = MeshUI.of("D000001");
  private static final MeshUI QUALIFIER_UI = MeshUI.of("Q000002");

  @Nested
  @DisplayName("创建测试")
  class CreationTests {

    @Test
    @DisplayName("应该成功创建只有 descriptorUi 的映射")
    void shouldCreateWithDescriptorOnly() {
      // when
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI);

      // then
      assertThat(mapping.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(mapping.qualifierUi()).isNull();
      assertThat(mapping.hasQualifier()).isFalse();
      assertThat(mapping.majorTopic()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建带 qualifierUi 的映射")
    void shouldCreateWithQualifier() {
      // when
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI, QUALIFIER_UI);

      // then
      assertThat(mapping.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(mapping.qualifierUi()).isEqualTo(QUALIFIER_UI);
      assertThat(mapping.hasQualifier()).isTrue();
      assertThat(mapping.majorTopic()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建主要主题词映射")
    void shouldCreateWithMajorTopic() {
      // when
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI, null, true);

      // then
      assertThat(mapping.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(mapping.qualifierUi()).isNull();
      assertThat(mapping.majorTopic()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建带限定词的主要主题词映射")
    void shouldCreateWithQualifierAndMajorTopic() {
      // when
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI, QUALIFIER_UI, true);

      // then
      assertThat(mapping.descriptorUi()).isEqualTo(DESCRIPTOR_UI);
      assertThat(mapping.qualifierUi()).isEqualTo(QUALIFIER_UI);
      assertThat(mapping.majorTopic()).isTrue();
    }

    @Test
    @DisplayName("descriptorUi 为 null 应该抛出异常")
    void shouldRejectNullDescriptor() {
      assertThatThrownBy(() -> HeadingMappedTo.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("主题词UI不能为空");
    }

    @Test
    @DisplayName("应该验证 descriptorUi 类型")
    void shouldValidateDescriptorType() {
      // given - 用 Concept UI 代替 Descriptor UI
      MeshUI conceptUi = MeshUI.of("M0000001");

      // when & then
      assertThatThrownBy(() -> HeadingMappedTo.of(conceptUi))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须以D开头");
    }

    @Test
    @DisplayName("应该验证 qualifierUi 类型")
    void shouldValidateQualifierType() {
      // given - 用 Concept UI 代替 Qualifier UI
      MeshUI conceptUi = MeshUI.of("M0000001");

      // when & then
      assertThatThrownBy(() -> HeadingMappedTo.of(DESCRIPTOR_UI, conceptUi))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须以Q开头");
    }
  }

  @Nested
  @DisplayName("toString 测试")
  class ToStringTests {

    @Test
    @DisplayName("普通映射 toString 应该不带星号")
    void toStringShouldNotHaveAsteriskForNonMajor() {
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI);

      assertThat(mapping.toString()).isEqualTo("HeadingMappedTo[D000001]");
    }

    @Test
    @DisplayName("主要主题词 toString 应该带星号前缀")
    void toStringShouldHaveAsteriskForMajor() {
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI, null, true);

      assertThat(mapping.toString()).isEqualTo("HeadingMappedTo[*D000001]");
    }

    @Test
    @DisplayName("带限定词的主要主题词 toString 应该正确格式化")
    void toStringShouldFormatCorrectlyWithQualifierAndMajor() {
      HeadingMappedTo mapping = HeadingMappedTo.of(DESCRIPTOR_UI, QUALIFIER_UI, true);

      assertThat(mapping.toString()).isEqualTo("HeadingMappedTo[*D000001/Q000002]");
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTests {

    @Test
    @DisplayName("相同映射应该相等")
    void sameMappingShouldBeEqual() {
      HeadingMappedTo mapping1 = HeadingMappedTo.of(DESCRIPTOR_UI, QUALIFIER_UI);
      HeadingMappedTo mapping2 = HeadingMappedTo.of(DESCRIPTOR_UI, QUALIFIER_UI);

      assertThat(mapping1).isEqualTo(mapping2);
      assertThat(mapping1.hashCode()).isEqualTo(mapping2.hashCode());
    }

    @Test
    @DisplayName("不同映射应该不相等")
    void differentMappingShouldNotBeEqual() {
      HeadingMappedTo mapping1 = HeadingMappedTo.of(DESCRIPTOR_UI);
      HeadingMappedTo mapping2 = HeadingMappedTo.of(MeshUI.of("D000002"));

      assertThat(mapping1).isNotEqualTo(mapping2);
    }

    @Test
    @DisplayName("majorTopic 不同的映射应该不相等")
    void differentMajorTopicShouldNotBeEqual() {
      HeadingMappedTo mapping1 = HeadingMappedTo.of(DESCRIPTOR_UI, null, false);
      HeadingMappedTo mapping2 = HeadingMappedTo.of(DESCRIPTOR_UI, null, true);

      assertThat(mapping1).isNotEqualTo(mapping2);
    }
  }
}
