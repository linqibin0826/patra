package dev.linqibin.patra.expr.json;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.expr.Expr;
import dev.linqibin.patra.expr.Exprs;
import dev.linqibin.patra.expr.TextMatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ExprJsonCodec 单元测试。
@DisplayName("ExprJsonCodec")
class ExprJsonCodecTest {

  /// 构造覆盖全部节点类型和原子值类型的表达式树。
  ///
  /// @param suffix 区分不同表达式的文本后缀
  /// @return 表达式树
  private static Expr sampleExpr(String suffix) {
    return Exprs.and(
        List.of(
            Exprs.term("title", "heart" + suffix, TextMatch.ANY),
            Exprs.or(
                List.of(
                    Exprs.in("journal", List.of("Nature", "Science" + suffix)),
                    Exprs.not(Exprs.exists("doi", true)))),
            Exprs.rangeDate("pubDate", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)),
            Exprs.rangeDateTime("updatedAt", Instant.parse("2020-01-01T00:00:00Z"), null),
            Exprs.rangeNumber("citations", new BigDecimal("10"), new BigDecimal("99.5")),
            Exprs.token("mesh", "D006331" + suffix),
            Exprs.constTrue()));
  }

  @Test
  @DisplayName("toJson 后 fromJson 得到相等的表达式树")
  void should_round_trip_expression_tree() {
    Expr expr = sampleExpr("");

    String json = ExprJsonCodec.toJson(expr);
    Expr restored = ExprJsonCodec.fromJson(json);

    assertThat(restored).isEqualTo(expr);
    assertThat(ExprJsonCodec.toJson(restored)).isEqualTo(json);
  }

  @Test
  @DisplayName("mapper 每次返回同一个缓存实例")
  void should_return_cached_mapper_instance() {
    assertThat(ExprJsonCodec.mapper()).isSameAs(ExprJsonCodec.mapper());
  }

  @Test
  @DisplayName("共享 mapper 并发序列化时各线程输出互不串扰")
  void should_serialize_concurrently_without_interference() {
    List<Integer> indexes = IntStream.range(0, 200).boxed().toList();

    List<String> concurrent =
        indexes.parallelStream().map(i -> ExprJsonCodec.toJson(sampleExpr("-" + i))).toList();

    List<String> sequential =
        indexes.stream().map(i -> ExprJsonCodec.toJson(sampleExpr("-" + i))).toList();
    assertThat(concurrent).isEqualTo(sequential);
  }
}
