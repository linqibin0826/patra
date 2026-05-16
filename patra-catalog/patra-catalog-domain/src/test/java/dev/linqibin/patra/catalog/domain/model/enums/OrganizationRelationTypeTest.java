package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/// OrganizationRelationType 枚举测试。
///
/// 基于 ROR Schema v2.0 的机构关系类型定义：parent, child, related, successor, predecessor
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationRelationType 枚举测试")
class OrganizationRelationTypeTest {

  @Nested
  @DisplayName("枚举值验证")
  class EnumValuesTest {

    @Test
    @DisplayName("应包含 ROR 定义的 5 种关系类型")
    void shouldContainAllRorRelationTypes() {
      assertThat(OrganizationRelationType.values()).hasSize(5);
      assertThat(OrganizationRelationType.values())
          .extracting(OrganizationRelationType::name)
          .containsExactlyInAnyOrder("PARENT", "CHILD", "RELATED", "SUCCESSOR", "PREDECESSOR");
    }

    @ParameterizedTest
    @CsvSource({
      "PARENT, parent, 上级机构",
      "CHILD, child, 下级机构",
      "RELATED, related, 相关机构",
      "SUCCESSOR, successor, 后继机构",
      "PREDECESSOR, predecessor, 前身机构"
    })
    @DisplayName("每个枚举值应有正确的 code 和 description")
    void shouldHaveCorrectCodeAndDescription(
        String enumName, String expectedCode, String expectedDescription) {
      OrganizationRelationType type = OrganizationRelationType.valueOf(enumName);
      assertThat(type.getCode()).isEqualTo(expectedCode);
      assertThat(type.getDescription()).isEqualTo(expectedDescription);
    }
  }

  @Nested
  @DisplayName("fromCode() 方法测试")
  class FromCodeTest {

    @ParameterizedTest
    @CsvSource({
      "parent, PARENT",
      "PARENT, PARENT",
      "Parent, PARENT",
      "child, CHILD",
      "successor, SUCCESSOR"
    })
    @DisplayName("应支持大小写不敏感的代码解析")
    void shouldParseCodeCaseInsensitively(String code, String expectedEnum) {
      assertThat(OrganizationRelationType.fromCode(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowExceptionForBlankValue(String code) {
      assertThatThrownBy(() -> OrganizationRelationType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownCode() {
      assertThatThrownBy(() -> OrganizationRelationType.fromCode("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的机构关系类型");
    }
  }

  @Nested
  @DisplayName("逆向关系测试")
  class InverseRelationTest {

    @Test
    @DisplayName("PARENT 的逆向关系应为 CHILD")
    void parentInverseShouldBeChild() {
      assertThat(OrganizationRelationType.PARENT.getInverse())
          .isEqualTo(OrganizationRelationType.CHILD);
    }

    @Test
    @DisplayName("CHILD 的逆向关系应为 PARENT")
    void childInverseShouldBeParent() {
      assertThat(OrganizationRelationType.CHILD.getInverse())
          .isEqualTo(OrganizationRelationType.PARENT);
    }

    @Test
    @DisplayName("SUCCESSOR 的逆向关系应为 PREDECESSOR")
    void successorInverseShouldBePredecessor() {
      assertThat(OrganizationRelationType.SUCCESSOR.getInverse())
          .isEqualTo(OrganizationRelationType.PREDECESSOR);
    }

    @Test
    @DisplayName("PREDECESSOR 的逆向关系应为 SUCCESSOR")
    void predecessorInverseShouldBeSuccessor() {
      assertThat(OrganizationRelationType.PREDECESSOR.getInverse())
          .isEqualTo(OrganizationRelationType.SUCCESSOR);
    }

    @Test
    @DisplayName("RELATED 的逆向关系应为自身")
    void relatedInverseShouldBeSelf() {
      assertThat(OrganizationRelationType.RELATED.getInverse())
          .isEqualTo(OrganizationRelationType.RELATED);
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isHierarchical() 应正确识别层级关系")
    void shouldIdentifyHierarchical() {
      assertThat(OrganizationRelationType.PARENT.isHierarchical()).isTrue();
      assertThat(OrganizationRelationType.CHILD.isHierarchical()).isTrue();
      assertThat(OrganizationRelationType.RELATED.isHierarchical()).isFalse();
      assertThat(OrganizationRelationType.SUCCESSOR.isHierarchical()).isFalse();
    }

    @Test
    @DisplayName("isTemporal() 应正确识别时序关系")
    void shouldIdentifyTemporal() {
      assertThat(OrganizationRelationType.SUCCESSOR.isTemporal()).isTrue();
      assertThat(OrganizationRelationType.PREDECESSOR.isTemporal()).isTrue();
      assertThat(OrganizationRelationType.PARENT.isTemporal()).isFalse();
      assertThat(OrganizationRelationType.RELATED.isTemporal()).isFalse();
    }
  }
}
