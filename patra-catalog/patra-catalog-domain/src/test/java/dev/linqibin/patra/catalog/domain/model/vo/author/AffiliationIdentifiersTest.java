package dev.linqibin.patra.catalog.domain.model.vo.author;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.vo.author.AffiliationIdentifiers.IdentifierLike;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// AffiliationIdentifiers 值对象单元测试。
///
/// 测试范围：
///
/// - 静态工厂方法（of、fromIdentifiers）
/// - 存在性判断（hasRorId、hasRinggoldId、hasGridId、hasAny、hasHighPriorityId）
/// - 优先级选择（getBestIdentifier、getBestIdentifierType）
/// - toString 格式化
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("AffiliationIdentifiers 值对象测试")
@Timeout(2)
class AffiliationIdentifiersTest {

  @Nested
  @DisplayName("of 静态工厂方法测试")
  class OfTests {

    @Test
    @DisplayName("所有标识符都为空时应返回 null")
    void shouldReturnNullWhenAllEmpty() {
      assertThat(AffiliationIdentifiers.of(null, null, null)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("空白字符串应被视为空")
    void shouldTreatBlankAsNull(String blank) {
      assertThat(AffiliationIdentifiers.of(blank, blank, blank)).isNull();
    }

    @Test
    @DisplayName("仅有 ROR ID 时应创建实例")
    void shouldCreateWithOnlyRorId() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of("03vek6s52", null, null);

      assertThat(ids).isNotNull();
      assertThat(ids.rorId()).isEqualTo("03vek6s52");
      assertThat(ids.ringgoldId()).isNull();
      assertThat(ids.gridId()).isNull();
    }

    @Test
    @DisplayName("仅有 Ringgold ID 时应创建实例")
    void shouldCreateWithOnlyRinggoldId() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of(null, "12345", null);

      assertThat(ids).isNotNull();
      assertThat(ids.rorId()).isNull();
      assertThat(ids.ringgoldId()).isEqualTo("12345");
      assertThat(ids.gridId()).isNull();
    }

    @Test
    @DisplayName("仅有 GRID ID 时应创建实例")
    void shouldCreateWithOnlyGridId() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of(null, null, "grid.123.456");

      assertThat(ids).isNotNull();
      assertThat(ids.rorId()).isNull();
      assertThat(ids.ringgoldId()).isNull();
      assertThat(ids.gridId()).isEqualTo("grid.123.456");
    }

    @Test
    @DisplayName("多个标识符同时存在时应创建完整实例")
    void shouldCreateWithMultipleIds() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of("03vek6s52", "12345", "grid.123.456");

      assertThat(ids).isNotNull();
      assertThat(ids.rorId()).isEqualTo("03vek6s52");
      assertThat(ids.ringgoldId()).isEqualTo("12345");
      assertThat(ids.gridId()).isEqualTo("grid.123.456");
    }

    @Test
    @DisplayName("空白和有效值混合时应只保留有效值")
    void shouldNormalizeBlankToNull() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of("03vek6s52", "  ", null);

      assertThat(ids).isNotNull();
      assertThat(ids.rorId()).isEqualTo("03vek6s52");
      assertThat(ids.ringgoldId()).isNull();
      assertThat(ids.gridId()).isNull();
    }
  }

  @Nested
  @DisplayName("fromIdentifiers 泛型工厂方法测试")
  class FromIdentifiersTests {

    @Test
    @DisplayName("空列表应返回 null")
    void shouldReturnNullForEmptyList() {
      assertThat(AffiliationIdentifiers.fromIdentifiers(List.of())).isNull();
    }

    @Test
    @DisplayName("null 列表应返回 null")
    void shouldReturnNullForNullList() {
      assertThat(AffiliationIdentifiers.fromIdentifiers(null)).isNull();
    }

    @Test
    @DisplayName("应正确解析 ROR 类型标识符")
    void shouldParseRorIdentifier() {
      List<TestIdentifier> ids = List.of(new TestIdentifier("ror", "03vek6s52"));

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.rorId()).isEqualTo("03vek6s52");
      assertThat(result.hasRorId()).isTrue();
    }

    @Test
    @DisplayName("应正确解析 Ringgold 类型标识符")
    void shouldParseRinggoldIdentifier() {
      List<TestIdentifier> ids = List.of(new TestIdentifier("ringgold", "12345"));

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.ringgoldId()).isEqualTo("12345");
      assertThat(result.hasRinggoldId()).isTrue();
    }

    @Test
    @DisplayName("应正确解析 GRID 类型标识符")
    void shouldParseGridIdentifier() {
      List<TestIdentifier> ids = List.of(new TestIdentifier("grid", "grid.123.456"));

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.gridId()).isEqualTo("grid.123.456");
      assertThat(result.hasGridId()).isTrue();
    }

    @Test
    @DisplayName("应忽略未知类型标识符")
    void shouldIgnoreUnknownTypes() {
      List<TestIdentifier> ids =
          List.of(
              new TestIdentifier("unknown", "value1"),
              new TestIdentifier("orcid", "0000-0001-2345-6789"),
              new TestIdentifier("ror", "03vek6s52"));

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.rorId()).isEqualTo("03vek6s52");
      assertThat(result.ringgoldId()).isNull();
      assertThat(result.gridId()).isNull();
    }

    @Test
    @DisplayName("类型解析应不区分大小写")
    void shouldParseCaseInsensitive() {
      List<TestIdentifier> ids =
          List.of(
              new TestIdentifier("ROR", "03vek6s52"),
              new TestIdentifier("RINGGOLD", "12345"),
              new TestIdentifier("Grid", "grid.123"));

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.rorId()).isEqualTo("03vek6s52");
      assertThat(result.ringgoldId()).isEqualTo("12345");
      assertThat(result.gridId()).isEqualTo("grid.123");
    }

    @Test
    @DisplayName("应忽略空白类型或值的标识符")
    void shouldIgnoreBlankTypeOrValue() {
      List<TestIdentifier> ids =
          List.of(
              new TestIdentifier("", "value1"),
              new TestIdentifier("ror", ""),
              new TestIdentifier("  ", "value2"),
              new TestIdentifier("ringgold", "12345"));

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.rorId()).isNull();
      assertThat(result.ringgoldId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("应忽略 null 元素")
    void shouldIgnoreNullElements() {
      List<TestIdentifier> ids = new java.util.ArrayList<>();
      ids.add(null);
      ids.add(new TestIdentifier("ror", "03vek6s52"));
      ids.add(null);

      AffiliationIdentifiers result = AffiliationIdentifiers.fromIdentifiers(ids);

      assertThat(result).isNotNull();
      assertThat(result.rorId()).isEqualTo("03vek6s52");
    }
  }

  @Nested
  @DisplayName("存在性判断方法测试")
  class ExistenceCheckTests {

    @Test
    @DisplayName("hasRorId 应正确判断")
    void shouldCheckRorIdExistence() {
      AffiliationIdentifiers withRor = AffiliationIdentifiers.of("03vek6s52", null, null);
      AffiliationIdentifiers withoutRor = AffiliationIdentifiers.of(null, "12345", null);

      assertThat(withRor.hasRorId()).isTrue();
      assertThat(withoutRor.hasRorId()).isFalse();
    }

    @Test
    @DisplayName("hasRinggoldId 应正确判断")
    void shouldCheckRinggoldIdExistence() {
      AffiliationIdentifiers withRinggold = AffiliationIdentifiers.of(null, "12345", null);
      AffiliationIdentifiers withoutRinggold = AffiliationIdentifiers.of("03vek6s52", null, null);

      assertThat(withRinggold.hasRinggoldId()).isTrue();
      assertThat(withoutRinggold.hasRinggoldId()).isFalse();
    }

    @Test
    @DisplayName("hasGridId 应正确判断")
    void shouldCheckGridIdExistence() {
      AffiliationIdentifiers withGrid = AffiliationIdentifiers.of(null, null, "grid.123");
      AffiliationIdentifiers withoutGrid = AffiliationIdentifiers.of("03vek6s52", null, null);

      assertThat(withGrid.hasGridId()).isTrue();
      assertThat(withoutGrid.hasGridId()).isFalse();
    }

    @Test
    @DisplayName("hasAny 应在有任意标识符时返回 true")
    void shouldCheckAnyExistence() {
      AffiliationIdentifiers onlyRor = AffiliationIdentifiers.of("03vek6s52", null, null);
      AffiliationIdentifiers onlyRinggold = AffiliationIdentifiers.of(null, "12345", null);
      AffiliationIdentifiers onlyGrid = AffiliationIdentifiers.of(null, null, "grid.123");

      assertThat(onlyRor.hasAny()).isTrue();
      assertThat(onlyRinggold.hasAny()).isTrue();
      assertThat(onlyGrid.hasAny()).isTrue();
    }

    @Test
    @DisplayName("hasHighPriorityId 应在有 ROR 或 Ringgold 时返回 true")
    void shouldCheckHighPriorityIdExistence() {
      AffiliationIdentifiers withRor = AffiliationIdentifiers.of("03vek6s52", null, null);
      AffiliationIdentifiers withRinggold = AffiliationIdentifiers.of(null, "12345", null);
      AffiliationIdentifiers onlyGrid = AffiliationIdentifiers.of(null, null, "grid.123");

      assertThat(withRor.hasHighPriorityId()).isTrue();
      assertThat(withRinggold.hasHighPriorityId()).isTrue();
      assertThat(onlyGrid.hasHighPriorityId()).isFalse();
    }
  }

  @Nested
  @DisplayName("优先级选择方法测试")
  class PrioritySelectionTests {

    @Test
    @DisplayName("getBestIdentifier 应优先返回 ROR ID")
    void shouldReturnRorIdFirst() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of("03vek6s52", "12345", "grid.123");

      assertThat(ids.getBestIdentifier()).isEqualTo("03vek6s52");
    }

    @Test
    @DisplayName("getBestIdentifier 无 ROR 时应返回 Ringgold ID")
    void shouldReturnRinggoldIdWhenNoRor() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of(null, "12345", "grid.123");

      assertThat(ids.getBestIdentifier()).isEqualTo("12345");
    }

    @Test
    @DisplayName("getBestIdentifier 无 ROR 和 Ringgold 时应返回 GRID ID")
    void shouldReturnGridIdWhenNoRorAndRinggold() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of(null, null, "grid.123");

      assertThat(ids.getBestIdentifier()).isEqualTo("grid.123");
    }

    @Test
    @DisplayName("getBestIdentifierType 应返回正确的类型")
    void shouldReturnCorrectType() {
      AffiliationIdentifiers withRor = AffiliationIdentifiers.of("03vek6s52", "12345", "grid.123");
      AffiliationIdentifiers withRinggold = AffiliationIdentifiers.of(null, "12345", "grid.123");
      AffiliationIdentifiers withGrid = AffiliationIdentifiers.of(null, null, "grid.123");

      assertThat(withRor.getBestIdentifierType()).isEqualTo("ror");
      assertThat(withRinggold.getBestIdentifierType()).isEqualTo("ringgold");
      assertThat(withGrid.getBestIdentifierType()).isEqualTo("grid");
    }
  }

  @Nested
  @DisplayName("toString 方法测试")
  class ToStringTests {

    @Test
    @DisplayName("应正确格式化单个标识符")
    void shouldFormatSingleIdentifier() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of("03vek6s52", null, null);

      assertThat(ids.toString()).isEqualTo("AffiliationIdentifiers{ror=03vek6s52}");
    }

    @Test
    @DisplayName("应正确格式化多个标识符")
    void shouldFormatMultipleIdentifiers() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of("03vek6s52", "12345", "grid.123");

      assertThat(ids.toString())
          .isEqualTo("AffiliationIdentifiers{ror=03vek6s52, ringgold=12345, grid=grid.123}");
    }

    @Test
    @DisplayName("应只包含非空标识符")
    void shouldOnlyIncludeNonNullIdentifiers() {
      AffiliationIdentifiers ids = AffiliationIdentifiers.of(null, "12345", "grid.123");

      assertThat(ids.toString()).isEqualTo("AffiliationIdentifiers{ringgold=12345, grid=grid.123}");
    }
  }

  @Nested
  @DisplayName("Record 特性测试")
  class RecordTests {

    @Test
    @DisplayName("equals 应正确比较")
    void shouldCompareCorrectly() {
      AffiliationIdentifiers ids1 = AffiliationIdentifiers.of("03vek6s52", "12345", null);
      AffiliationIdentifiers ids2 = AffiliationIdentifiers.of("03vek6s52", "12345", null);
      AffiliationIdentifiers ids3 = AffiliationIdentifiers.of("different", "12345", null);

      assertThat(ids1).isEqualTo(ids2);
      assertThat(ids1).isNotEqualTo(ids3);
    }

    @Test
    @DisplayName("hashCode 应一致")
    void shouldHaveConsistentHashCode() {
      AffiliationIdentifiers ids1 = AffiliationIdentifiers.of("03vek6s52", "12345", null);
      AffiliationIdentifiers ids2 = AffiliationIdentifiers.of("03vek6s52", "12345", null);

      assertThat(ids1.hashCode()).isEqualTo(ids2.hashCode());
    }
  }

  /// 测试用的 IdentifierLike 实现
  private record TestIdentifier(String type, String value) implements IdentifierLike {}
}
