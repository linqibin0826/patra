package dev.linqibin.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.enums.ExternalIdType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ExternalId 值对象单元测试。
///
/// 基于 ROR Schema v2.0 的 external_ids 字段定义。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExternalId 值对象")
class ExternalIdTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应正确创建单值外部标识符")
    void shouldCreateSingleValueExternalId() {
      ExternalId extId = ExternalId.create(ExternalIdType.ISNI, "0000 0001 2157 6568");

      assertThat(extId.type()).isEqualTo(ExternalIdType.ISNI);
      assertThat(extId.preferred()).isEqualTo("0000 0001 2157 6568");
      assertThat(extId.allValues()).containsExactly("0000 0001 2157 6568");
      assertThat(extId.id()).isNull();
    }

    @Test
    @DisplayName("应正确创建多值外部标识符")
    void shouldCreateMultiValueExternalId() {
      List<String> values = List.of("100000001", "100000002");

      ExternalId extId = ExternalId.create(ExternalIdType.FUNDREF, values, "100000001");

      assertThat(extId.type()).isEqualTo(ExternalIdType.FUNDREF);
      assertThat(extId.allValues()).containsExactly("100000001", "100000002");
      assertThat(extId.preferred()).isEqualTo("100000001");
    }

    @Test
    @DisplayName("应正确创建带 ID 的外部标识符")
    void shouldCreateExternalIdWithId() {
      List<String> values = List.of("Q219563");

      ExternalId extId = ExternalId.createWithId(123L, ExternalIdType.WIKIDATA, values, "Q219563");

      assertThat(extId.id()).isEqualTo(123L);
      assertThat(extId.type()).isEqualTo(ExternalIdType.WIKIDATA);
    }

    @Test
    @DisplayName("null 类型应抛出异常")
    void shouldThrowWhenTypeIsNull() {
      assertThatThrownBy(() -> ExternalId.create(null, "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("外部标识符类型不能为空");
    }

    @Test
    @DisplayName("空值列表应抛出异常")
    void shouldThrowWhenValuesIsEmpty() {
      assertThatThrownBy(() -> ExternalId.create(ExternalIdType.ISNI, List.of(), "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("外部标识符值不能为空");
    }

    @Test
    @DisplayName("空白首选值应抛出异常")
    void shouldThrowWhenPreferredIsBlank() {
      assertThatThrownBy(() -> ExternalId.create(ExternalIdType.ISNI, List.of("value"), ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("首选值不能为空");

      assertThatThrownBy(() -> ExternalId.create(ExternalIdType.ISNI, List.of("value"), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("首选值不能为空");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("hasMultipleValues() 应正确判断是否有多个值")
    void shouldCheckHasMultipleValues() {
      ExternalId single = ExternalId.create(ExternalIdType.ISNI, "value1");
      ExternalId multiple = ExternalId.create(ExternalIdType.FUNDREF, List.of("v1", "v2"), "v1");

      assertThat(single.hasMultipleValues()).isFalse();
      assertThat(multiple.hasMultipleValues()).isTrue();
    }

    @Test
    @DisplayName("hasId() 应正确判断是否已持久化")
    void shouldCheckHasId() {
      ExternalId withId =
          ExternalId.createWithId(1L, ExternalIdType.ISNI, List.of("value"), "value");
      ExternalId withoutId = ExternalId.create(ExternalIdType.ISNI, "value");

      assertThat(withId.hasId()).isTrue();
      assertThat(withoutId.hasId()).isFalse();
    }

    @Test
    @DisplayName("isGrid() 应正确识别 GRID 标识符")
    void shouldIdentifyGrid() {
      ExternalId grid = ExternalId.create(ExternalIdType.GRID, "grid.38142.3c");
      ExternalId isni = ExternalId.create(ExternalIdType.ISNI, "0000 0001 2157 6568");

      assertThat(grid.isGrid()).isTrue();
      assertThat(isni.isGrid()).isFalse();
    }

    @Test
    @DisplayName("isIsni() 应正确识别 ISNI 标识符")
    void shouldIdentifyIsni() {
      ExternalId isni = ExternalId.create(ExternalIdType.ISNI, "0000 0001 2157 6568");
      ExternalId grid = ExternalId.create(ExternalIdType.GRID, "grid.38142.3c");

      assertThat(isni.isIsni()).isTrue();
      assertThat(grid.isIsni()).isFalse();
    }

    @Test
    @DisplayName("isWikidata() 应正确识别 Wikidata 标识符")
    void shouldIdentifyWikidata() {
      ExternalId wikidata = ExternalId.create(ExternalIdType.WIKIDATA, "Q219563");
      ExternalId isni = ExternalId.create(ExternalIdType.ISNI, "value");

      assertThat(wikidata.isWikidata()).isTrue();
      assertThat(isni.isWikidata()).isFalse();
    }

    @Test
    @DisplayName("isFundRef() 应正确识别 FundRef 标识符")
    void shouldIdentifyFundRef() {
      ExternalId fundref = ExternalId.create(ExternalIdType.FUNDREF, "100000001");
      ExternalId isni = ExternalId.create(ExternalIdType.ISNI, "value");

      assertThat(fundref.isFundRef()).isTrue();
      assertThat(isni.isFundRef()).isFalse();
    }

    @Test
    @DisplayName("isRinggold() 应正确识别 Ringgold 标识符")
    void shouldIdentifyRinggold() {
      ExternalId ringgold = ExternalId.create(ExternalIdType.RINGGOLD, "1812");
      ExternalId isni = ExternalId.create(ExternalIdType.ISNI, "value");

      assertThat(ringgold.isRinggold()).isTrue();
      assertThat(isni.isRinggold()).isFalse();
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTest {

    @Test
    @DisplayName("相同类型的外部标识符应相等（忽略 ID）")
    void shouldBeEqualWhenTypeSame() {
      ExternalId ext1 = ExternalId.createWithId(1L, ExternalIdType.ISNI, List.of("v1"), "v1");
      ExternalId ext2 = ExternalId.createWithId(2L, ExternalIdType.ISNI, List.of("v2"), "v2");

      assertThat(ext1).isEqualTo(ext2);
      assertThat(ext1.hashCode()).isEqualTo(ext2.hashCode());
    }

    @Test
    @DisplayName("不同类型的外部标识符应不相等")
    void shouldNotBeEqualWhenTypeDifferent() {
      ExternalId ext1 = ExternalId.create(ExternalIdType.ISNI, "value");
      ExternalId ext2 = ExternalId.create(ExternalIdType.GRID, "value");

      assertThat(ext1).isNotEqualTo(ext2);
    }
  }

  @Nested
  @DisplayName("with-style 方法测试")
  class WithMethodsTest {

    @Test
    @DisplayName("withId() 应返回带 ID 的新实例")
    void shouldReturnNewInstanceWithId() {
      ExternalId original = ExternalId.create(ExternalIdType.ISNI, "value");

      ExternalId withId = original.withId(123L);

      assertThat(withId.id()).isEqualTo(123L);
      assertThat(withId.type()).isEqualTo(ExternalIdType.ISNI);
      assertThat(withId.preferred()).isEqualTo("value");
      // 原对象不变
      assertThat(original.id()).isNull();
    }
  }
}
