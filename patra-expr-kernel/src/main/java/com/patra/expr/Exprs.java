package com.patra.expr;

import com.patra.expr.Atom.DateRange;
import com.patra.expr.Atom.DateTimeRange;
import com.patra.expr.Atom.ExistsFlag;
import com.patra.expr.Atom.InValues;
import com.patra.expr.Atom.NumberRange;
import com.patra.expr.Atom.Operator;
import com.patra.expr.Atom.RangeValue.Boundary;
import com.patra.expr.Atom.TermValue;
import com.patra.expr.Atom.TokenValue;
import com.patra.expr.json.ExprJsonCodec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/// 表达式节点的静态工厂。
/// 
/// 提供了便捷的方法来构建表达式树，而无需直接实例化记录类。所有方法都执行验证并返回不可变表达式节点。
public final class Exprs {
  private Exprs() {}

  /// 返回常量 TRUE 表达式。
  public static Expr constTrue() {
    return Const.TRUE;
  }

  /// 返回常量 FALSE 表达式。
  public static Expr constFalse() {
    return Const.FALSE;
  }

  /// 创建 AND 表达式，结合多个子表达式。
/// 
/// @param children 待使用 AND 逻辑结合的表达式列表
/// @return AND 表达式
  public static Expr and(List<Expr> children) {
    return new And(children);
  }

  /// 创建 OR 表达式，结合多个子表达式。
/// 
/// @param children 待使用 OR 逻辑结合的表达式列表
/// @return OR 表达式
  public static Expr or(List<Expr> children) {
    return new Or(children);
  }

  /// 创建 NOT 表达式，否定一个子表达式。
/// 
/// @param child 待否定的表达式
/// @return NOT 表达式
  public static Expr not(Expr child) {
    return new Not(child);
  }

  /// 创建 TERM 表达式，进行不区分大小写的匹配。
/// 
/// @param field 待搜索的字段名
/// @param value 待匹配的文本值
/// @param match 匹配策略
/// @return TERM 表达式
  public static Expr term(String field, String value, TextMatch match) {
    return term(field, value, match, CaseSensitivity.INSENSITIVE);
  }

  /// 创建 TERM 表达式，具有可配置的大小写敏感度。
/// 
/// @param field 待搜索的字段名
/// @param value 待匹配的文本值
/// @param match 匹配策略
/// @param caseSensitive 为 true 表示区分大小写，false 表示不区分大小写
/// @return TERM 表达式
  public static Expr term(String field, String value, TextMatch match, boolean caseSensitive) {
    return term(field, value, match, CaseSensitivity.of(caseSensitive));
  }

  /// 创建 TERM 表达式，具有显式的大小写敏感度控制。
/// 
/// @param field 待搜索的字段名
/// @param value 待匹配的文本值
/// @param match 匹配策略
/// @param caseSensitivity 大小写敏感度行为
/// @return TERM 表达式
  public static Expr term(
      String field, String value, TextMatch match, CaseSensitivity caseSensitivity) {
    return new Atom(field, Operator.TERM, new TermValue(value, match, caseSensitivity));
  }

  /// 创建 IN 表达式，进行不区分大小写的匹配。
/// 
/// @param field 待搜索的字段名
/// @param values 待匹配的值列表
/// @return IN 表达式
  public static Expr in(String field, List<String> values) {
    return in(field, values, CaseSensitivity.INSENSITIVE);
  }

  /// 创建 IN 表达式，具有可配置的大小写敏感度。
/// 
/// @param field 待搜索的字段名
/// @param values 待匹配的值列表
/// @param caseSensitive 为 true 表示区分大小写，false 表示不区分大小写
/// @return IN 表达式
  public static Expr in(String field, List<String> values, boolean caseSensitive) {
    return in(field, values, CaseSensitivity.of(caseSensitive));
  }

  /// 创建 IN 表达式，具有显式的大小写敏感度控制。
/// 
/// @param field 待搜索的字段名
/// @param values 待匹配的值列表
/// @param caseSensitivity 大小写敏感度行为
/// @return IN 表达式
  public static Expr in(String field, List<String> values, CaseSensitivity caseSensitivity) {
    return new Atom(field, Operator.IN, new InValues(values, caseSensitivity));
  }

  /// 创建闭合日期范围表达式。
/// 
/// @param field 待搜索的字段名
/// @param from 下边界日期（包含）
/// @param to 上边界日期（包含）
/// @return 日期范围表达式
  public static Expr rangeDate(String field, LocalDate from, LocalDate to) {
    return new Atom(field, Operator.RANGE, new DateRange(from, to));
  }

  /// 创建日期范围表达式，具有可配置的边界包含策略。
/// 
/// @param field 待搜索的字段名
/// @param from 下边界日期
/// @param to 上边界日期
/// @param includeFrom 为 true 则包含下边界，false 则不包含
/// @param includeTo 为 true 则包含上边界，false 则不包含
/// @return 日期范围表达式
  public static Expr rangeDate(
      String field, LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo) {
    return new Atom(
        field,
        Operator.RANGE,
        new DateRange(from, to, toBoundary(includeFrom), toBoundary(includeTo)));
  }

  /// 创建闭合日期时间范围表达式。
/// 
/// @param field 待搜索的字段名
/// @param from 下边界时刻（包含）
/// @param to 上边界时刻（包含）
/// @return 日期时间范围表达式
  public static Expr rangeDateTime(String field, Instant from, Instant to) {
    return new Atom(field, Operator.RANGE, new DateTimeRange(from, to));
  }

  /// 创建日期时间范围表达式，具有可配置的边界包含策略。
/// 
/// @param field 待搜索的字段名
/// @param from 下边界时刻
/// @param to 上边界时刻
/// @param includeFrom 为 true 则包含下边界，false 则不包含
/// @param includeTo 为 true 则包含上边界，false 则不包含
/// @return 日期时间范围表达式
  public static Expr rangeDateTime(
      String field, Instant from, Instant to, boolean includeFrom, boolean includeTo) {
    return new Atom(
        field,
        Operator.RANGE,
        new DateTimeRange(from, to, toBoundary(includeFrom), toBoundary(includeTo)));
  }

  /// 创建闭合数字范围表达式。
/// 
/// @param field 待搜索的字段名
/// @param from 下边界数字（包含）
/// @param to 上边界数字（包含）
/// @return 数字范围表达式
  public static Expr rangeNumber(String field, BigDecimal from, BigDecimal to) {
    return new Atom(field, Operator.RANGE, new NumberRange(from, to));
  }

  /// 创建数字范围表达式，具有可配置的边界包含策略。
/// 
/// @param field 待搜索的字段名
/// @param from 下边界数字
/// @param to 上边界数字
/// @param includeFrom 为 true 则包含下边界，false 则不包含
/// @param includeTo 为 true 则包含上边界，false 则不包含
/// @return 数字范围表达式
  public static Expr rangeNumber(
      String field, BigDecimal from, BigDecimal to, boolean includeFrom, boolean includeTo) {
    return new Atom(
        field,
        Operator.RANGE,
        new NumberRange(from, to, toBoundary(includeFrom), toBoundary(includeTo)));
  }

  /// 创建 EXISTS 表达式，检查字段的存在或不存在。
/// 
/// @param field 待检查的字段名
/// @param shouldExist 为 true 则检查存在，false 则检查不存在
/// @return EXISTS 表达式
  public static Expr exists(String field, boolean shouldExist) {
    return new Atom(field, Operator.EXISTS, new ExistsFlag(shouldExist));
  }

  /// 创建 TOKEN 表达式，使用令牌类型作为字段名和令牌类型。
/// 
/// 这是一个便捷方法，与之前的 API 行为兼容。
/// 
/// @param tokenType 令牌类型（同时作为字段名和类型）
/// @param tokenValue 令牌值
/// @return TOKEN 表达式
  public static Expr token(String tokenType, String tokenValue) {
    return token(tokenType, tokenType, tokenValue);
  }

  /// 创建 TOKEN 表达式，字段名和令牌类型分离。
/// 
/// @param field 待搜索的字段名
/// @param tokenType 令牌的类型（例如 "MeSH"、"GeneSymbol"）
/// @param tokenValue 令牌值
/// @return TOKEN 表达式
  public static Expr token(String field, String tokenType, String tokenValue) {
    return new Atom(field, Operator.TOKEN, new TokenValue(tokenType, tokenValue));
  }

  /// 将表达式树序列化为 JSON。
/// 
/// @param expr 待序列化的表达式
/// @return JSON 字符串表示
  public static String toJson(Expr expr) {
    return ExprJsonCodec.toJson(expr);
  }

  /// 从 JSON 反序列化为表达式树。
/// 
/// @param json 待解析的 JSON 字符串
/// @return 表达式树
  public static Expr fromJson(String json) {
    return ExprJsonCodec.fromJson(json);
  }

  private static Boundary toBoundary(boolean inclusive) {
    return inclusive ? Boundary.CLOSED : Boundary.OPEN;
  }
}
