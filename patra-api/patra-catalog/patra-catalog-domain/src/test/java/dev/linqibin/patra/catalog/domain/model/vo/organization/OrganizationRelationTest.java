package dev.linqibin.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.enums.OrganizationRelationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OrganizationRelation 值对象单元测试。
///
/// 基于 ROR Schema v2.0 的 relationships 字段定义。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationRelation 值对象")
class OrganizationRelationTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应正确创建机构关系")
    void shouldCreateOrganizationRelation() {
      RorId relatedRorId = RorId.of("https://ror.org/03vek6s52");

      OrganizationRelation relation =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, relatedRorId, "Harvard University");

      assertThat(relation.type()).isEqualTo(OrganizationRelationType.PARENT);
      assertThat(relation.relatedRorId()).isEqualTo(relatedRorId);
      assertThat(relation.relatedLabel()).isEqualTo("Harvard University");
      assertThat(relation.relatedOrgId()).isNull();
      assertThat(relation.id()).isNull();
    }

    @Test
    @DisplayName("应正确创建带 ID 的机构关系")
    void shouldCreateRelationWithId() {
      RorId relatedRorId = RorId.of("https://ror.org/03vek6s52");

      OrganizationRelation relation =
          OrganizationRelation.createWithId(
              123L, OrganizationRelationType.CHILD, relatedRorId, "Medical School", 456L);

      assertThat(relation.id()).isEqualTo(123L);
      assertThat(relation.type()).isEqualTo(OrganizationRelationType.CHILD);
      assertThat(relation.relatedOrgId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("null 类型应抛出异常")
    void shouldThrowWhenTypeIsNull() {
      RorId relatedRorId = RorId.of("https://ror.org/03vek6s52");

      assertThatThrownBy(() -> OrganizationRelation.create(null, relatedRorId, "Label"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("关系类型不能为空");
    }

    @Test
    @DisplayName("null ROR ID 应抛出异常")
    void shouldThrowWhenRorIdIsNull() {
      assertThatThrownBy(
              () -> OrganizationRelation.create(OrganizationRelationType.PARENT, null, "Label"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("关联机构 ROR ID 不能为空");
    }

    @Test
    @DisplayName("空白标签应抛出异常")
    void shouldThrowWhenLabelIsBlank() {
      RorId relatedRorId = RorId.of("https://ror.org/03vek6s52");

      assertThatThrownBy(
              () -> OrganizationRelation.create(OrganizationRelationType.PARENT, relatedRorId, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("关联机构名称不能为空");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isParent() 应正确识别父级关系")
    void shouldIdentifyParent() {
      OrganizationRelation parent = createRelation(OrganizationRelationType.PARENT);
      OrganizationRelation child = createRelation(OrganizationRelationType.CHILD);

      assertThat(parent.isParent()).isTrue();
      assertThat(child.isParent()).isFalse();
    }

    @Test
    @DisplayName("isChild() 应正确识别子级关系")
    void shouldIdentifyChild() {
      OrganizationRelation child = createRelation(OrganizationRelationType.CHILD);
      OrganizationRelation parent = createRelation(OrganizationRelationType.PARENT);

      assertThat(child.isChild()).isTrue();
      assertThat(parent.isChild()).isFalse();
    }

    @Test
    @DisplayName("isRelated() 应正确识别关联关系")
    void shouldIdentifyRelated() {
      OrganizationRelation related = createRelation(OrganizationRelationType.RELATED);
      OrganizationRelation parent = createRelation(OrganizationRelationType.PARENT);

      assertThat(related.isRelated()).isTrue();
      assertThat(parent.isRelated()).isFalse();
    }

    @Test
    @DisplayName("isSuccessor() 应正确识别后继关系")
    void shouldIdentifySuccessor() {
      OrganizationRelation successor = createRelation(OrganizationRelationType.SUCCESSOR);
      OrganizationRelation predecessor = createRelation(OrganizationRelationType.PREDECESSOR);

      assertThat(successor.isSuccessor()).isTrue();
      assertThat(predecessor.isSuccessor()).isFalse();
    }

    @Test
    @DisplayName("isPredecessor() 应正确识别前身关系")
    void shouldIdentifyPredecessor() {
      OrganizationRelation predecessor = createRelation(OrganizationRelationType.PREDECESSOR);
      OrganizationRelation successor = createRelation(OrganizationRelationType.SUCCESSOR);

      assertThat(predecessor.isPredecessor()).isTrue();
      assertThat(successor.isPredecessor()).isFalse();
    }

    @Test
    @DisplayName("isHierarchical() 应正确识别层级关系")
    void shouldIdentifyHierarchical() {
      OrganizationRelation parent = createRelation(OrganizationRelationType.PARENT);
      OrganizationRelation child = createRelation(OrganizationRelationType.CHILD);
      OrganizationRelation related = createRelation(OrganizationRelationType.RELATED);

      assertThat(parent.isHierarchical()).isTrue();
      assertThat(child.isHierarchical()).isTrue();
      assertThat(related.isHierarchical()).isFalse();
    }

    @Test
    @DisplayName("isTemporal() 应正确识别时序关系")
    void shouldIdentifyTemporal() {
      OrganizationRelation successor = createRelation(OrganizationRelationType.SUCCESSOR);
      OrganizationRelation predecessor = createRelation(OrganizationRelationType.PREDECESSOR);
      OrganizationRelation parent = createRelation(OrganizationRelationType.PARENT);

      assertThat(successor.isTemporal()).isTrue();
      assertThat(predecessor.isTemporal()).isTrue();
      assertThat(parent.isTemporal()).isFalse();
    }

    @Test
    @DisplayName("hasId() 应正确判断是否已持久化")
    void shouldCheckHasId() {
      OrganizationRelation withId =
          OrganizationRelation.createWithId(
              1L,
              OrganizationRelationType.PARENT,
              RorId.of("https://ror.org/03vek6s52"),
              "Label",
              null);
      OrganizationRelation withoutId = createRelation(OrganizationRelationType.PARENT);

      assertThat(withId.hasId()).isTrue();
      assertThat(withoutId.hasId()).isFalse();
    }

    @Test
    @DisplayName("isLinkedToOrganization() 应正确判断是否已关联到系统内机构")
    void shouldCheckIsLinkedToOrganization() {
      OrganizationRelation linked =
          OrganizationRelation.createWithId(
              1L,
              OrganizationRelationType.PARENT,
              RorId.of("https://ror.org/03vek6s52"),
              "Label",
              456L);
      OrganizationRelation notLinked = createRelation(OrganizationRelationType.PARENT);

      assertThat(linked.isLinkedToOrganization()).isTrue();
      assertThat(notLinked.isLinkedToOrganization()).isFalse();
    }

    private OrganizationRelation createRelation(OrganizationRelationType type) {
      return OrganizationRelation.create(
          type, RorId.of("https://ror.org/03vek6s52"), "Test Organization");
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTest {

    @Test
    @DisplayName("相同类型和 ROR ID 的关系应相等（忽略 ID）")
    void shouldBeEqualWhenTypeAndRorIdSame() {
      RorId rorId = RorId.of("https://ror.org/03vek6s52");

      OrganizationRelation rel1 =
          OrganizationRelation.createWithId(
              1L, OrganizationRelationType.PARENT, rorId, "Label 1", null);
      OrganizationRelation rel2 =
          OrganizationRelation.createWithId(
              2L, OrganizationRelationType.PARENT, rorId, "Label 2", null);

      assertThat(rel1).isEqualTo(rel2);
      assertThat(rel1.hashCode()).isEqualTo(rel2.hashCode());
    }

    @Test
    @DisplayName("不同类型的关系应不相等")
    void shouldNotBeEqualWhenTypeDifferent() {
      RorId rorId = RorId.of("https://ror.org/03vek6s52");

      OrganizationRelation rel1 =
          OrganizationRelation.create(OrganizationRelationType.PARENT, rorId, "Label");
      OrganizationRelation rel2 =
          OrganizationRelation.create(OrganizationRelationType.CHILD, rorId, "Label");

      assertThat(rel1).isNotEqualTo(rel2);
    }

    @Test
    @DisplayName("不同 ROR ID 的关系应不相等")
    void shouldNotBeEqualWhenRorIdDifferent() {
      OrganizationRelation rel1 =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/03vek6s52"), "Label");
      OrganizationRelation rel2 =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0abcdefgh"), "Label");

      assertThat(rel1).isNotEqualTo(rel2);
    }
  }

  @Nested
  @DisplayName("with-style 方法测试")
  class WithMethodsTest {

    @Test
    @DisplayName("withId() 应返回带 ID 的新实例")
    void shouldReturnNewInstanceWithId() {
      OrganizationRelation original =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/03vek6s52"), "Label");

      OrganizationRelation withId = original.withId(123L);

      assertThat(withId.id()).isEqualTo(123L);
      assertThat(withId.type()).isEqualTo(OrganizationRelationType.PARENT);
      // 原对象不变
      assertThat(original.id()).isNull();
    }

    @Test
    @DisplayName("linkToOrganization() 应返回关联到机构的新实例")
    void shouldReturnLinkedInstance() {
      OrganizationRelation original =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/03vek6s52"), "Label");

      OrganizationRelation linked = original.linkToOrganization(456L);

      assertThat(linked.relatedOrgId()).isEqualTo(456L);
      // 原对象不变
      assertThat(original.relatedOrgId()).isNull();
    }
  }
}
