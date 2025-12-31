package com.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshUI 值对象单元测试。
///
/// 测试策略：
///
/// - 每种 UI 类型的创建和验证
/// - 类型判断方法的正确性
/// - 边界条件和异常情况
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshUI 值对象测试")
class MeshUITest {

  @Nested
  @DisplayName("SCR UI (C开头)")
  class ScrUITests {

    @Test
    @DisplayName("应该成功创建 SCR UI（旧格式：C + 6位数字）")
    void shouldCreateScrUI_oldFormat() {
      // given
      String ui = "C000001";

      // when
      MeshUI meshUI = MeshUI.of(ui);

      // then
      assertThat(meshUI.ui()).isEqualTo("C000001");
      assertThat(meshUI.isScr()).isTrue();
      assertThat(meshUI.isDescriptor()).isFalse();
      assertThat(meshUI.isConcept()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建 SCR UI（新格式：C + 9位数字）")
    void shouldCreateScrUI_newFormat() {
      // given
      String ui = "C000000123";

      // when
      MeshUI meshUI = MeshUI.of(ui);

      // then
      assertThat(meshUI.ui()).isEqualTo("C000000123");
      assertThat(meshUI.isScr()).isTrue();
    }

    @Test
    @DisplayName("应该自动转换为大写")
    void shouldConvertToUpperCase() {
      // given
      String ui = "c000001";

      // when
      MeshUI meshUI = MeshUI.of(ui);

      // then
      assertThat(meshUI.ui()).isEqualTo("C000001");
    }

    @Test
    @DisplayName("应该通过工厂方法创建 SCR UI")
    void shouldCreateViaScrOfFactory() {
      // when
      MeshUI meshUI = MeshUI.scrOf(1);

      // then
      assertThat(meshUI.ui()).isEqualTo("C000001");
      assertThat(meshUI.isScr()).isTrue();
    }

    @Test
    @DisplayName("工厂方法应该正确格式化编号")
    void shouldFormatNumberCorrectly() {
      // when
      MeshUI meshUI = MeshUI.scrOf(123456);

      // then
      assertThat(meshUI.ui()).isEqualTo("C123456");
    }

    @Test
    @DisplayName("工厂方法编号范围验证 - 最小值")
    void shouldRejectNumberBelowRange() {
      assertThatThrownBy(() -> MeshUI.scrOf(0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1-999999");
    }

    @Test
    @DisplayName("工厂方法编号范围验证 - 最大值")
    void shouldRejectNumberAboveRange() {
      assertThatThrownBy(() -> MeshUI.scrOf(1000000))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1-999999");
    }

    @Test
    @DisplayName("getType() 应该返回 SCR")
    void shouldReturnScrType() {
      // given
      MeshUI meshUI = MeshUI.of("C000001");

      // when
      String type = meshUI.getType();

      // then
      assertThat(type).isEqualTo("SCR");
    }

    @Test
    @DisplayName("getNumber() 应该返回数字部分")
    void shouldReturnNumericPart() {
      // given
      MeshUI meshUI = MeshUI.of("C000123");

      // when
      long number = meshUI.getNumber();

      // then
      assertThat(number).isEqualTo(123L);
    }

    @Test
    @DisplayName("getNumber() 应该正确处理 9 位数字 UI")
    void shouldHandleLargeNumber() {
      // given
      MeshUI meshUI = MeshUI.of("C999999999");

      // when
      long number = meshUI.getNumber();

      // then
      assertThat(number).isEqualTo(999999999L);
    }

    @Test
    @DisplayName("应该拒绝无效的 SCR UI 格式")
    void shouldRejectInvalidFormat() {
      // C 后面只有 5 位数字
      assertThatThrownBy(() -> MeshUI.of("C00001")).isInstanceOf(IllegalArgumentException.class);

      // C 后面有 7 位数字（既不是 6 位也不是 9 位）
      assertThatThrownBy(() -> MeshUI.of("C0000001")).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Descriptor UI (D开头)")
  class DescriptorUITests {

    @Test
    @DisplayName("应该成功创建 Descriptor UI")
    void shouldCreateDescriptorUI() {
      MeshUI meshUI = MeshUI.of("D000001");

      assertThat(meshUI.isDescriptor()).isTrue();
      assertThat(meshUI.isScr()).isFalse();
      assertThat(meshUI.isConcept()).isFalse();
    }

    @Test
    @DisplayName("getType() 应该返回 Descriptor")
    void shouldReturnDescriptorType() {
      MeshUI meshUI = MeshUI.of("D000001");
      assertThat(meshUI.getType()).isEqualTo("Descriptor");
    }
  }

  @Nested
  @DisplayName("Concept UI (M开头)")
  class ConceptUITests {

    @Test
    @DisplayName("应该成功创建 Concept UI")
    void shouldCreateConceptUI() {
      MeshUI meshUI = MeshUI.of("M0000001");

      assertThat(meshUI.isConcept()).isTrue();
      assertThat(meshUI.isScr()).isFalse();
      assertThat(meshUI.isDescriptor()).isFalse();
    }
  }

  @Nested
  @DisplayName("Qualifier UI (Q开头)")
  class QualifierUITests {

    @Test
    @DisplayName("应该成功创建 Qualifier UI")
    void shouldCreateQualifierUI() {
      MeshUI meshUI = MeshUI.of("Q000001");

      assertThat(meshUI.isQualifier()).isTrue();
      assertThat(meshUI.isScr()).isFalse();
    }
  }

  @Nested
  @DisplayName("Term UI (T开头)")
  class TermUITests {

    @Test
    @DisplayName("应该成功创建 Term UI")
    void shouldCreateTermUI() {
      MeshUI meshUI = MeshUI.of("T000001");

      assertThat(meshUI.isTerm()).isTrue();
      assertThat(meshUI.isScr()).isFalse();
    }
  }
}
