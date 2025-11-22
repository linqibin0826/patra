package com.patra.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/// 描述字段级约束的叶子表达式。
/// 
/// 原子表示查询的基本构建块，将字段名、运算符和值组合在一起。运算符决定允许哪种值类型。
/// 
/// @param fieldKey 要查询的字段名
/// @param operator 要执行的运算
/// @param value 要匹配的值
public record Atom(String fieldKey, Operator operator, Value value) implements Expr {

  public Atom {
    Objects.requireNonNull(fieldKey, "fieldKey");
    if (fieldKey.isBlank()) {
      throw new IllegalArgumentException("字段名不能为空");
    }
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(value, "value");
    operator.verifyValueCompatibility(value);
  }

  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitAtom(this);
  }

  /// 支持的字段运算符。
/// 
/// 每个运算符都关联一个特定的值类型。
  public enum Operator {
    /// 基于文本的词语匹配。
    TERM(TermValue.class),

    /// 离散值集合匹配。
    IN(InValues.class),

    /// 日期、时间和数字的范围匹配。
    RANGE(RangeValue.class),

    /// 字段存在性检查。
    EXISTS(ExistsFlag.class),

    /// 平台特定的令牌匹配。
    TOKEN(TokenValue.class);

    private final Class<? extends Value> supportedType;

    Operator(Class<? extends Value> supportedType) {
      this.supportedType = supportedType;
    }

    void verifyValueCompatibility(Value value) {
      if (!supportedType.isInstance(value)) {
        throw new IllegalArgumentException(
            String.format(
                "运算符 %s 需要值类型 %s，但收到 %s",
                this, supportedType.getSimpleName(), value.getClass().getSimpleName()));
      }
    }
  }

  /// 所有值变体的标记接口。
/// 
/// 密封以确保类型安全和穷举模式匹配。
  public sealed interface Value permits TermValue, InValues, RangeValue, ExistsFlag, TokenValue {}

  /// 用于 TERM 操作的基于文本的值。
/// 
/// @param text 要匹配的文本
/// @param match 匹配策略
/// @param caseSensitivity 大小写敏感度行为
  public record TermValue(String text, TextMatch match, CaseSensitivity caseSensitivity)
      implements Value {
    public TermValue {
      Objects.requireNonNull(match, "match");
      Objects.requireNonNull(caseSensitivity, "caseSensitivity");
    }

    public TermValue(String text, TextMatch match) {
      this(text, match, CaseSensitivity.INSENSITIVE);
    }
  }

  /// 用于 IN 操作的离散字符串值的集合。
/// 
/// @param values 非空的要匹配的值列表
/// @param caseSensitivity 大小写敏感度行为
  public record InValues(List<String> values, CaseSensitivity caseSensitivity) implements Value {
    public InValues {
      Objects.requireNonNull(values, "values");
      if (values.isEmpty()) {
        throw new IllegalArgumentException("IN 值必须至少包含一个项目");
      }
      if (values.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("IN 值不能包含空项目");
      }
      values = List.copyOf(values);
      Objects.requireNonNull(caseSensitivity, "caseSensitivity");
    }

    public InValues(List<String> values) {
      this(values, CaseSensitivity.INSENSITIVE);
    }
  }

  /// 基于范围值的公共约定。
/// 
/// 支持日期、日期时间和数字范围，具有可配置的边界包含。
  public sealed interface RangeValue extends Value permits DateRange, DateTimeRange, NumberRange {
    /// 返回下边界包含类型。
    Boundary fromBoundary();

    /// 返回上边界包含类型。
    Boundary toBoundary();

    /// 范围值的边界包含类型。
    enum Boundary {
      /// 从范围中排除边界值。
      OPEN,

      /// 在范围中包含边界值。
      CLOSED
    }
  }

  /// 日期范围值。
/// 
/// @param from 下边界日期
/// @param to 上边界日期
/// @param fromBoundary 下边界包含类型
/// @param toBoundary 上边界包含类型
  public record DateRange(LocalDate from, LocalDate to, Boundary fromBoundary, Boundary toBoundary)
      implements RangeValue {
    public DateRange {
      Objects.requireNonNull(fromBoundary, "fromBoundary");
      Objects.requireNonNull(toBoundary, "toBoundary");
    }

    public DateRange(LocalDate from, LocalDate to) {
      this(from, to, Boundary.CLOSED, Boundary.CLOSED);
    }
  }

  /// 日期时间范围值。
/// 
/// @param from 下边界时刻
/// @param to 上边界时刻
/// @param fromBoundary 下边界包含类型
/// @param toBoundary 上边界包含类型
  public record DateTimeRange(Instant from, Instant to, Boundary fromBoundary, Boundary toBoundary)
      implements RangeValue {
    public DateTimeRange {
      Objects.requireNonNull(fromBoundary, "fromBoundary");
      Objects.requireNonNull(toBoundary, "toBoundary");
    }

    public DateTimeRange(Instant from, Instant to) {
      this(from, to, Boundary.CLOSED, Boundary.CLOSED);
    }
  }

  /// 数字范围值。
/// 
/// @param from 下边界数字
/// @param to 上边界数字
/// @param fromBoundary 下边界包含类型
/// @param toBoundary 上边界包含类型
  public record NumberRange(
      BigDecimal from, BigDecimal to, Boundary fromBoundary, Boundary toBoundary)
      implements RangeValue {
    public NumberRange {
      Objects.requireNonNull(fromBoundary, "fromBoundary");
      Objects.requireNonNull(toBoundary, "toBoundary");
    }

    public NumberRange(BigDecimal from, BigDecimal to) {
      this(from, to, Boundary.CLOSED, Boundary.CLOSED);
    }
  }

  /// EXISTS 操作值，表示字段的存在或不存在。
/// 
/// @param shouldExist true 检查字段存在，false 检查字段不存在
  public record ExistsFlag(boolean shouldExist) implements Value {}

  /// TOKEN 操作值，用于平台特定的令牌语义。
/// 
/// @param tokenType 令牌的类型（例如 "MeSH"、"GeneSymbol"）
/// @param tokenValue 令牌标识符值
  public record TokenValue(String tokenType, String tokenValue) implements Value {
    public TokenValue {
      Objects.requireNonNull(tokenType, "tokenType");
      Objects.requireNonNull(tokenValue, "tokenValue");
    }
  }
}
