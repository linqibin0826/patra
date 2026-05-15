package dev.linqibin.patra.catalog.domain.model.vo.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// EntryCombination 值对象单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试工厂方法参数验证逻辑
/// - 测试 hasEcoutQualifier() 方法
///
/// **DTD 定义**：
///
/// ```xml
/// <!ELEMENT ECIN (DescriptorReferredTo, QualifierReferredTo)>
/// <!ELEMENT ECOUT (DescriptorReferredTo, QualifierReferredTo?)>
/// ```
///
/// - ECIN: Descriptor 和 Qualifier 都是必填的
/// - ECOUT: Descriptor 必填，Qualifier 可选
///
/// @author linqibin
/// @since 0.2.1
@DisplayName("EntryCombination 单元测试")
class EntryCombinationTest {

  // 有效的测试数据
  private static final MeshUI VALID_ECIN_DESCRIPTOR = MeshUI.of("D005128");
  private static final MeshUI VALID_ECIN_QUALIFIER = MeshUI.of("Q000188");
  private static final MeshUI VALID_ECOUT_DESCRIPTOR = MeshUI.of("D005128");
  private static final MeshUI VALID_ECOUT_QUALIFIER = MeshUI.of("Q000628");

  @Nested
  @DisplayName("工厂方法 of() 正常情况测试")
  class FactoryMethodSuccessTests {

    @Test
    @DisplayName("完整参数应该正确创建 EntryCombination")
    void shouldCreateWithAllParameters() {
      // When
      EntryCombination combination =
          EntryCombination.of(
              VALID_ECIN_DESCRIPTOR,
              VALID_ECIN_QUALIFIER,
              VALID_ECOUT_DESCRIPTOR,
              VALID_ECOUT_QUALIFIER);

      // Then
      assertThat(combination.ecinDescriptorUi()).isEqualTo(VALID_ECIN_DESCRIPTOR);
      assertThat(combination.ecinQualifierUi()).isEqualTo(VALID_ECIN_QUALIFIER);
      assertThat(combination.ecoutDescriptorUi()).isEqualTo(VALID_ECOUT_DESCRIPTOR);
      assertThat(combination.ecoutQualifierUi()).isEqualTo(VALID_ECOUT_QUALIFIER);
    }

    @Test
    @DisplayName("无 ECOUT Qualifier 应该正确创建 EntryCombination")
    void shouldCreateWithoutEcoutQualifier() {
      // When
      EntryCombination combination =
          EntryCombination.of(VALID_ECIN_DESCRIPTOR, VALID_ECIN_QUALIFIER, VALID_ECOUT_DESCRIPTOR);

      // Then
      assertThat(combination.ecinDescriptorUi()).isEqualTo(VALID_ECIN_DESCRIPTOR);
      assertThat(combination.ecinQualifierUi()).isEqualTo(VALID_ECIN_QUALIFIER);
      assertThat(combination.ecoutDescriptorUi()).isEqualTo(VALID_ECOUT_DESCRIPTOR);
      assertThat(combination.ecoutQualifierUi()).isNull();
    }

    @Test
    @DisplayName("四参数工厂方法传 null 作为 ECOUT Qualifier 应该正确创建")
    void shouldCreateWithNullEcoutQualifier() {
      // When
      EntryCombination combination =
          EntryCombination.of(
              VALID_ECIN_DESCRIPTOR, VALID_ECIN_QUALIFIER, VALID_ECOUT_DESCRIPTOR, null);

      // Then
      assertThat(combination.ecoutQualifierUi()).isNull();
    }
  }

  @Nested
  @DisplayName("ECIN 参数验证测试")
  class EcinValidationTests {

    @Test
    @DisplayName("ECIN Descriptor 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcinDescriptorIsNull() {
      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      null, VALID_ECIN_QUALIFIER, VALID_ECOUT_DESCRIPTOR, VALID_ECOUT_QUALIFIER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECIN 主题词 UI 不能为空");
    }

    @Test
    @DisplayName("ECIN Descriptor 非 D 开头时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcinDescriptorNotStartsWithD() {
      MeshUI invalidDescriptor = MeshUI.of("Q000188"); // Q 开头是 Qualifier

      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      invalidDescriptor,
                      VALID_ECIN_QUALIFIER,
                      VALID_ECOUT_DESCRIPTOR,
                      VALID_ECOUT_QUALIFIER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECIN 主题词 UI 必须是 Descriptor 类型(D开头)");
    }

    @Test
    @DisplayName("ECIN Qualifier 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcinQualifierIsNull() {
      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      VALID_ECIN_DESCRIPTOR, null, VALID_ECOUT_DESCRIPTOR, VALID_ECOUT_QUALIFIER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECIN 限定词 UI 不能为空");
    }

    @Test
    @DisplayName("ECIN Qualifier 非 Q 开头时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcinQualifierNotStartsWithQ() {
      MeshUI invalidQualifier = MeshUI.of("D005128"); // D 开头是 Descriptor

      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      VALID_ECIN_DESCRIPTOR,
                      invalidQualifier,
                      VALID_ECOUT_DESCRIPTOR,
                      VALID_ECOUT_QUALIFIER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECIN 限定词 UI 必须是 Qualifier 类型(Q开头)");
    }
  }

  @Nested
  @DisplayName("ECOUT 参数验证测试")
  class EcoutValidationTests {

    @Test
    @DisplayName("ECOUT Descriptor 为 null 时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcoutDescriptorIsNull() {
      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      VALID_ECIN_DESCRIPTOR, VALID_ECIN_QUALIFIER, null, VALID_ECOUT_QUALIFIER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECOUT 主题词 UI 不能为空");
    }

    @Test
    @DisplayName("ECOUT Descriptor 非 D 开头时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcoutDescriptorNotStartsWithD() {
      MeshUI invalidDescriptor = MeshUI.of("Q000188"); // Q 开头是 Qualifier

      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      VALID_ECIN_DESCRIPTOR,
                      VALID_ECIN_QUALIFIER,
                      invalidDescriptor,
                      VALID_ECOUT_QUALIFIER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECOUT 主题词 UI 必须是 Descriptor 类型(D开头)");
    }

    @Test
    @DisplayName("ECOUT Qualifier 非 Q 开头时应该抛出 IllegalArgumentException")
    void shouldThrowWhenEcoutQualifierNotStartsWithQ() {
      MeshUI invalidQualifier = MeshUI.of("D005128"); // D 开头是 Descriptor

      assertThatThrownBy(
              () ->
                  EntryCombination.of(
                      VALID_ECIN_DESCRIPTOR,
                      VALID_ECIN_QUALIFIER,
                      VALID_ECOUT_DESCRIPTOR,
                      invalidQualifier))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ECOUT 限定词 UI 必须是 Qualifier 类型(Q开头)");
    }
  }

  @Nested
  @DisplayName("hasEcoutQualifier() 方法测试")
  class HasEcoutQualifierTests {

    @Test
    @DisplayName("有 ECOUT Qualifier 时应该返回 true")
    void shouldReturnTrueWhenEcoutQualifierPresent() {
      // Given
      EntryCombination combination =
          EntryCombination.of(
              VALID_ECIN_DESCRIPTOR,
              VALID_ECIN_QUALIFIER,
              VALID_ECOUT_DESCRIPTOR,
              VALID_ECOUT_QUALIFIER);

      // When & Then
      assertThat(combination.hasEcoutQualifier()).isTrue();
    }

    @Test
    @DisplayName("无 ECOUT Qualifier 时应该返回 false")
    void shouldReturnFalseWhenEcoutQualifierAbsent() {
      // Given
      EntryCombination combination =
          EntryCombination.of(VALID_ECIN_DESCRIPTOR, VALID_ECIN_QUALIFIER, VALID_ECOUT_DESCRIPTOR);

      // When & Then
      assertThat(combination.hasEcoutQualifier()).isFalse();
    }
  }
}
