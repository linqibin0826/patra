package com.patra.catalog.infra.adapter.parser.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ReferredTo Record 单元测试。
///
/// 测试引用实体解析结果的各种行为，包括 Major Topic（星号前缀）的处理。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ReferredTo 单元测试")
class ReferredToTest {

  @Nested
  @DisplayName("empty() 测试")
  class EmptyTests {

    @Test
    @DisplayName("应该创建 UI 和 Name 均为 null 的空引用")
    void shouldCreateEmptyReferredTo() {
      // when
      ReferredTo empty = ReferredTo.empty();

      // then
      assertThat(empty.ui()).isNull();
      assertThat(empty.name()).isNull();
    }
  }

  @Nested
  @DisplayName("isValid() 测试")
  class IsValidTests {

    @Test
    @DisplayName("UI 和 Name 均非空时应该返回 true")
    void shouldReturnTrueWhenBothUiAndNamePresent() {
      // given
      ReferredTo ref = new ReferredTo("D000001", "Test Descriptor");

      // when & then
      assertThat(ref.isValid()).isTrue();
    }

    @Test
    @DisplayName("UI 为空时应该返回 false")
    void shouldReturnFalseWhenUiIsNull() {
      // given
      ReferredTo ref = new ReferredTo(null, "Test Name");

      // when & then
      assertThat(ref.isValid()).isFalse();
    }

    @Test
    @DisplayName("Name 为空时应该返回 false")
    void shouldReturnFalseWhenNameIsNull() {
      // given
      ReferredTo ref = new ReferredTo("D000001", null);

      // when & then
      assertThat(ref.isValid()).isFalse();
    }

    @Test
    @DisplayName("空引用应该返回 false")
    void shouldReturnFalseForEmptyReferredTo() {
      // given
      ReferredTo empty = ReferredTo.empty();

      // when & then
      assertThat(empty.isValid()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasUiOnly() 测试")
  class HasUiOnlyTests {

    @Test
    @DisplayName("只有 UI 没有 Name 时应该返回 true")
    void shouldReturnTrueWhenOnlyUiPresent() {
      // given
      ReferredTo ref = new ReferredTo("D000001", null);

      // when & then
      assertThat(ref.hasUiOnly()).isTrue();
    }

    @Test
    @DisplayName("UI 和 Name 都存在时应该返回 false")
    void shouldReturnFalseWhenBothPresent() {
      // given
      ReferredTo ref = new ReferredTo("D000001", "Test Descriptor");

      // when & then
      assertThat(ref.hasUiOnly()).isFalse();
    }

    @Test
    @DisplayName("UI 为空时应该返回 false")
    void shouldReturnFalseWhenUiIsNull() {
      // given
      ReferredTo ref = new ReferredTo(null, null);

      // when & then
      assertThat(ref.hasUiOnly()).isFalse();
    }
  }

  @Nested
  @DisplayName("isMajorTopic() 测试")
  class IsMajorTopicTests {

    @Test
    @DisplayName("UI 以星号开头时应该返回 true")
    void shouldReturnTrueWhenUiStartsWithAsterisk() {
      // given
      ReferredTo ref = new ReferredTo("*D011799", "Quinazolines");

      // when & then
      assertThat(ref.isMajorTopic()).isTrue();
    }

    @Test
    @DisplayName("UI 不以星号开头时应该返回 false")
    void shouldReturnFalseWhenUiDoesNotStartWithAsterisk() {
      // given
      ReferredTo ref = new ReferredTo("D011799", "Quinazolines");

      // when & then
      assertThat(ref.isMajorTopic()).isFalse();
    }

    @Test
    @DisplayName("UI 为空时应该返回 false")
    void shouldReturnFalseWhenUiIsNull() {
      // given
      ReferredTo ref = new ReferredTo(null, "Test Name");

      // when & then
      assertThat(ref.isMajorTopic()).isFalse();
    }

    @Test
    @DisplayName("空引用应该返回 false")
    void shouldReturnFalseForEmptyReferredTo() {
      // given
      ReferredTo empty = ReferredTo.empty();

      // when & then
      assertThat(empty.isMajorTopic()).isFalse();
    }
  }

  @Nested
  @DisplayName("toMeshUI() 测试")
  class ToMeshUITests {

    @Test
    @DisplayName("应该将普通 UI 转换为 MeshUI")
    void shouldConvertNormalUiToMeshUI() {
      // given
      ReferredTo ref = new ReferredTo("D011799", "Quinazolines");

      // when
      MeshUI meshUI = ref.toMeshUI();

      // then
      assertThat(meshUI).isNotNull();
      assertThat(meshUI.ui()).isEqualTo("D011799");
    }

    @Test
    @DisplayName("应该剥离星号前缀并转换为 MeshUI")
    void shouldStripAsteriskAndConvertToMeshUI() {
      // given
      ReferredTo ref = new ReferredTo("*D011799", "Quinazolines");

      // when
      MeshUI meshUI = ref.toMeshUI();

      // then
      assertThat(meshUI).isNotNull();
      assertThat(meshUI.ui()).isEqualTo("D011799");
    }

    @Test
    @DisplayName("UI 为空时应该返回 null")
    void shouldReturnNullWhenUiIsNull() {
      // given
      ReferredTo ref = new ReferredTo(null, "Test Name");

      // when
      MeshUI meshUI = ref.toMeshUI();

      // then
      assertThat(meshUI).isNull();
    }

    @Test
    @DisplayName("空引用应该返回 null")
    void shouldReturnNullForEmptyReferredTo() {
      // given
      ReferredTo empty = ReferredTo.empty();

      // when
      MeshUI meshUI = empty.toMeshUI();

      // then
      assertThat(meshUI).isNull();
    }

    @Test
    @DisplayName("应该正确转换 Qualifier UI")
    void shouldConvertQualifierUI() {
      // given
      ReferredTo ref = new ReferredTo("Q000002", "Test Qualifier");

      // when
      MeshUI meshUI = ref.toMeshUI();

      // then
      assertThat(meshUI).isNotNull();
      assertThat(meshUI.ui()).isEqualTo("Q000002");
    }

    @Test
    @DisplayName("应该正确转换 SCR UI")
    void shouldConvertScrUI() {
      // given
      ReferredTo ref = new ReferredTo("C000001", "Test SCR");

      // when
      MeshUI meshUI = ref.toMeshUI();

      // then
      assertThat(meshUI).isNotNull();
      assertThat(meshUI.ui()).isEqualTo("C000001");
    }
  }

  @Nested
  @DisplayName("Record 相等性测试")
  class EqualityTests {

    @Test
    @DisplayName("相同 UI 和 Name 的引用应该相等")
    void shouldBeEqualWithSameUiAndName() {
      // given
      ReferredTo ref1 = new ReferredTo("D000001", "Test Descriptor");
      ReferredTo ref2 = new ReferredTo("D000001", "Test Descriptor");

      // when & then
      assertThat(ref1).isEqualTo(ref2);
      assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
    }

    @Test
    @DisplayName("不同 UI 的引用应该不相等")
    void shouldNotBeEqualWithDifferentUi() {
      // given
      ReferredTo ref1 = new ReferredTo("D000001", "Test Descriptor");
      ReferredTo ref2 = new ReferredTo("D000002", "Test Descriptor");

      // when & then
      assertThat(ref1).isNotEqualTo(ref2);
    }

    @Test
    @DisplayName("带星号和不带星号的 UI 应该不相等")
    void shouldNotBeEqualWithAndWithoutAsterisk() {
      // given
      ReferredTo ref1 = new ReferredTo("*D011799", "Quinazolines");
      ReferredTo ref2 = new ReferredTo("D011799", "Quinazolines");

      // when & then
      assertThat(ref1).isNotEqualTo(ref2);
    }
  }
}
