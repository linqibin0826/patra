package com.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.OrganizationNameType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OrganizationName 值对象单元测试。
///
/// 基于 ROR Schema v2.0 的 names 字段定义。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationName 值对象")
class OrganizationNameTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应正确创建带类型的名称")
    void shouldCreateNameWithTypes() {
      Set<OrganizationNameType> types =
          Set.of(OrganizationNameType.ROR_DISPLAY, OrganizationNameType.LABEL);

      OrganizationName name = OrganizationName.create("Harvard University", types, "en");

      assertThat(name.value()).isEqualTo("Harvard University");
      assertThat(name.types())
          .containsExactlyInAnyOrder(OrganizationNameType.ROR_DISPLAY, OrganizationNameType.LABEL);
      assertThat(name.lang()).isEqualTo("en");
      assertThat(name.id()).isNull();
    }

    @Test
    @DisplayName("应正确创建带 ID 的名称")
    void shouldCreateNameWithId() {
      Set<OrganizationNameType> types = Set.of(OrganizationNameType.ALIAS);

      OrganizationName name = OrganizationName.createWithId(123L, "哈佛大学", types, "zh");

      assertThat(name.id()).isEqualTo(123L);
      assertThat(name.value()).isEqualTo("哈佛大学");
      assertThat(name.types()).containsExactly(OrganizationNameType.ALIAS);
      assertThat(name.lang()).isEqualTo("zh");
    }

    @Test
    @DisplayName("应正确创建无语言的名称")
    void shouldCreateNameWithoutLang() {
      OrganizationName name =
          OrganizationName.create("MIT", Set.of(OrganizationNameType.ACRONYM), null);

      assertThat(name.value()).isEqualTo("MIT");
      assertThat(name.lang()).isNull();
    }

    @Test
    @DisplayName("空白名称值应抛出异常")
    void shouldThrowWhenValueIsBlank() {
      Set<OrganizationNameType> types = Set.of(OrganizationNameType.LABEL);

      assertThatThrownBy(() -> OrganizationName.create("", types, "en"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("名称值不能为空");

      assertThatThrownBy(() -> OrganizationName.create("  ", types, "en"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("名称值不能为空");

      assertThatThrownBy(() -> OrganizationName.create(null, types, "en"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("名称值不能为空");
    }

    @Test
    @DisplayName("空类型集合应抛出异常")
    void shouldThrowWhenTypesIsEmpty() {
      assertThatThrownBy(() -> OrganizationName.create("Harvard", Set.of(), "en"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("名称类型不能为空");
    }

    @Test
    @DisplayName("null 类型集合应抛出异常")
    void shouldThrowWhenTypesIsNull() {
      assertThatThrownBy(() -> OrganizationName.create("Harvard", null, "en"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("名称类型不能为空");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isDisplayName() 应正确识别 ROR 显示名")
    void shouldIdentifyDisplayName() {
      OrganizationName displayName =
          OrganizationName.create(
              "Harvard University",
              Set.of(OrganizationNameType.ROR_DISPLAY, OrganizationNameType.LABEL),
              "en");
      OrganizationName alias =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.ALIAS), "en");

      assertThat(displayName.isDisplayName()).isTrue();
      assertThat(alias.isDisplayName()).isFalse();
    }

    @Test
    @DisplayName("isOfficialName() 应正确识别官方名称")
    void shouldIdentifyOfficialName() {
      OrganizationName displayName =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.ROR_DISPLAY), "en");
      OrganizationName label =
          OrganizationName.create("Harvard University", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName alias =
          OrganizationName.create("Harvard College", Set.of(OrganizationNameType.ALIAS), "en");

      assertThat(displayName.isOfficialName()).isTrue();
      assertThat(label.isOfficialName()).isTrue();
      assertThat(alias.isOfficialName()).isFalse();
    }

    @Test
    @DisplayName("isAcronym() 应正确识别缩写")
    void shouldIdentifyAcronym() {
      OrganizationName acronym =
          OrganizationName.create("MIT", Set.of(OrganizationNameType.ACRONYM), "en");
      OrganizationName fullName =
          OrganizationName.create(
              "Massachusetts Institute of Technology", Set.of(OrganizationNameType.LABEL), "en");

      assertThat(acronym.isAcronym()).isTrue();
      assertThat(fullName.isAcronym()).isFalse();
    }

    @Test
    @DisplayName("isAlias() 应正确识别别名")
    void shouldIdentifyAlias() {
      OrganizationName alias =
          OrganizationName.create("Harvard College", Set.of(OrganizationNameType.ALIAS), "en");
      OrganizationName label =
          OrganizationName.create("Harvard University", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName mixed =
          OrganizationName.create(
              "Harvard", Set.of(OrganizationNameType.ALIAS, OrganizationNameType.LABEL), "en");

      assertThat(alias.isAlias()).isTrue();
      assertThat(label.isAlias()).isFalse();
      assertThat(mixed.isAlias()).isTrue(); // 只要包含 ALIAS 类型即返回 true
    }

    @Test
    @DisplayName("hasLang() 应正确判断是否有语言代码")
    void shouldCheckHasLang() {
      OrganizationName withLang =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName withoutLang =
          OrganizationName.create("MIT", Set.of(OrganizationNameType.ACRONYM), null);

      assertThat(withLang.hasLang()).isTrue();
      assertThat(withoutLang.hasLang()).isFalse();
    }

    @Test
    @DisplayName("hasId() 应正确判断是否已持久化")
    void shouldCheckHasId() {
      OrganizationName withId =
          OrganizationName.createWithId(1L, "Harvard", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName withoutId =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.LABEL), "en");

      assertThat(withId.hasId()).isTrue();
      assertThat(withoutId.hasId()).isFalse();
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTest {

    @Test
    @DisplayName("相同值和语言的名称应相等（忽略 ID）")
    void shouldBeEqualWhenValueAndLangSame() {
      OrganizationName name1 =
          OrganizationName.createWithId(1L, "Harvard", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName name2 =
          OrganizationName.createWithId(2L, "Harvard", Set.of(OrganizationNameType.ALIAS), "en");

      assertThat(name1).isEqualTo(name2);
      assertThat(name1.hashCode()).isEqualTo(name2.hashCode());
    }

    @Test
    @DisplayName("不同值的名称应不相等")
    void shouldNotBeEqualWhenValueDifferent() {
      OrganizationName name1 =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName name2 =
          OrganizationName.create("Yale", Set.of(OrganizationNameType.LABEL), "en");

      assertThat(name1).isNotEqualTo(name2);
    }

    @Test
    @DisplayName("不同语言的同名应不相等")
    void shouldNotBeEqualWhenLangDifferent() {
      OrganizationName name1 =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.LABEL), "en");
      OrganizationName name2 =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.LABEL), "zh");

      assertThat(name1).isNotEqualTo(name2);
    }
  }

  @Nested
  @DisplayName("with-style 方法测试")
  class WithMethodsTest {

    @Test
    @DisplayName("withId() 应返回带 ID 的新实例")
    void shouldReturnNewInstanceWithId() {
      OrganizationName original =
          OrganizationName.create("Harvard", Set.of(OrganizationNameType.LABEL), "en");

      OrganizationName withId = original.withId(123L);

      assertThat(withId.id()).isEqualTo(123L);
      assertThat(withId.value()).isEqualTo("Harvard");
      assertThat(withId.types()).isEqualTo(original.types());
      assertThat(withId.lang()).isEqualTo("en");
      // 原对象不变
      assertThat(original.id()).isNull();
    }
  }
}
