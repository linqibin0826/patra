package com.patra.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;


/**
 * AST 构造工厂（公共 API）。
 * <p>只暴露公开类型（Expr、TextMatch、JDK 基本类型），避免可见性泄露。</p>
 * <p>
 * 线程安全性：全部为无状态静态工厂方法，线程安全。
 * 资源与事务：不持有外部资源。
 * <p>
 * 使用示例：
 * <pre>
 *   Expr e = Exprs.and(List.of(
 *       Exprs.term("title", "deep learning", TextMatch.PHRASE),
 *       Exprs.rangeDate("year", LocalDate.of(2018,1,1), LocalDate.of(2020,12,31))
 *   ));
 * </pre>
 * <p>
 * 注意：参数的空值/边界检查由后续的结构/能力校验器负责，本工厂不做强校验以保持构造灵活性。
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class Exprs {
    private Exprs() {
    }

    /**
     * 构造 AND 节点。
     *
     * @param children 子表达式列表；允许包含嵌套 AND/OR/NOT/Atom
     * @return AND 表达式（未做结构归一化）
     */
    public static Expr and(List<Expr> children) {
        return new And(children);
    }

    /**
     * 构造 OR 节点。
     *
     * @param children 子表达式列表
     * @return OR 表达式（未做结构归一化）
     */
    public static Expr or(List<Expr> children) {
        return new Or(children);
    }

    /**
     * 构造 NOT 节点。
     *
     * @param child 被取反的子表达式
     * @return NOT(child)
     */
    public static Expr not(Expr child) {
        return new Not(child);
    }

    /**
     * TERM：字符串词项（默认不区分大小写）。
     *
     * @param field 字段名
     * @param value 原始字符串值；可为 null；空白处理由校验器约束
     * @param match 文本匹配策略
     * @return 词项表达式
     */
    public static Expr term(String field, String value, TextMatch match) {
        return new Atom(field, Atom.Op.TERM, new Atom.Str(value, match));
    }

    /**
     * TERM：字符串词项（可指定是否区分大小写）。
     *
     * @param field         字段名
     * @param value         原始字符串值；可为 null
     * @param match         文本匹配策略
     * @param caseSensitive 是否区分大小写；true 表示保持大小写敏感
     * @return 词项表达式
     */
    public static Expr term(String field, String value, TextMatch match, boolean caseSensitive) {
        return new Atom(field, Atom.Op.TERM, new Atom.Str(value, match, caseSensitive));
    }

    /**
     * IN：多值之一（空列表应在校验阶段拒绝）。
     *
     * @param field  字段名
     * @param values 多个候选字符串；可包含重复或空白，待校验阶段处理
     * @return IN 表达式；默认大小写不敏感
     */
    public static Expr in(String field, List<String> values) {
        return new Atom(field, Atom.Op.IN, new Atom.Strs(values)); // 默认不区分大小写
    }

    /**
     * IN：多值之一（可指定大小写敏感）。
     *
     * @param field         字段名
     * @param values        候选字符串集合
     * @param caseSensitive 是否大小写敏感
     * @return IN 表达式
     */
    public static Expr in(String field, List<String> values, boolean caseSensitive) {
        return new Atom(field, Atom.Op.IN, new Atom.Strs(values, caseSensitive));
    }


    /**
     * RANGE：日期（日粒度），默认闭区间。
     *
     * @param field 字段名
     * @param from  起始（可空）
     * @param to    结束（可空）
     * @return 日期范围表达式（闭区间）
     */
    public static Expr rangeDate(String field, LocalDate from, LocalDate to) {
        return new Atom(field, Atom.Op.RANGE, new Atom.DateRange(from, to));
    }

    /**
     * RANGE：日期（日粒度），可指定端点开闭。
     *
     * @param field       字段名
     * @param from        起始（可空）
     * @param to          结束（可空）
     * @param includeFrom 起始端是否闭
     * @param includeTo   结束端是否闭
     * @return 日期范围表达式
     */
    public static Expr rangeDate(String field, LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo) {
        return new Atom(field, Atom.Op.RANGE, new Atom.DateRange(
                from, to,
                includeFrom ? Atom.Boundary.CLOSED : Atom.Boundary.OPEN,
                includeTo ? Atom.Boundary.CLOSED : Atom.Boundary.OPEN));
    }

    /**
     * RANGE：日期时间（Instant），默认闭区间。
     *
     * @param field 字段名
     * @param from  起始（可空）
     * @param to    结束（可空）
     * @return 日期时间范围表达式（闭区间）
     */
    public static Expr rangeDateTime(String field, Instant from, Instant to) {
        return new Atom(field, Atom.Op.RANGE, new Atom.DateTimeRange(from, to));
    }

    /**
     * RANGE：日期时间（Instant），可指定端点开闭。
     *
     * @param field       字段名
     * @param from        起始（可空）
     * @param to          结束（可空）
     * @param includeFrom 起始端是否闭
     * @param includeTo   结束端是否闭
     * @return 日期时间范围表达式
     */
    public static Expr rangeDateTime(String field, Instant from, Instant to, boolean includeFrom, boolean includeTo) {
        return new Atom(field, Atom.Op.RANGE, new Atom.DateTimeRange(
                from, to,
                includeFrom ? Atom.Boundary.CLOSED : Atom.Boundary.OPEN,
                includeTo ? Atom.Boundary.CLOSED : Atom.Boundary.OPEN));
    }

    /**
     * RANGE：数值范围（BigDecimal），默认闭区间。
     *
     * @param field 字段名
     * @param from  起始（可空）
     * @param to    结束（可空）
     * @return 数值范围表达式（闭区间）
     */
    public static Expr rangeNumber(String field, BigDecimal from, BigDecimal to) {
        return new Atom(field, Atom.Op.RANGE, new Atom.NumberRange(from, to));
    }

    /**
     * RANGE：数值范围（BigDecimal），可指定端点开闭。
     *
     * @param field       字段名
     * @param from        起始（可空）
     * @param to          结束（可空）
     * @param includeFrom 起始端是否闭
     * @param includeTo   结束端是否闭
     * @return 数值范围表达式
     */
    public static Expr rangeNumber(String field, BigDecimal from, BigDecimal to, boolean includeFrom, boolean includeTo) {
        return new Atom(field, Atom.Op.RANGE, new Atom.NumberRange(
                from, to,
                includeFrom ? Atom.Boundary.CLOSED : Atom.Boundary.OPEN,
                includeTo ? Atom.Boundary.CLOSED : Atom.Boundary.OPEN));
    }

    /**
     * EXISTS：字段存在/不存在。
     *
     * @param field  字段名
     * @param exists true 表示要求存在；false 表示要求不存在
     * @return EXISTS 表达式
     */
    public static Expr exists(String field, boolean exists) {
        return new Atom(field, Atom.Op.EXISTS, new Atom.Bool(exists));
    }

    /**
     * TOKEN：特殊记号（如 ownerNasa、PMC2600426）。
     *
     * @param kind  记号类别（同时用作字段名）
     * @param value 记号取值
     * @return TOKEN 表达式
     */
    public static Expr token(String kind, String value) {
        // 约定：field 使用 kind，便于 canonical key 与渲染。
        return new Atom(kind, Atom.Op.TOKEN, new Atom.Token(kind, value));
    }

    public static Expr constTrue() {
        return Const.TRUE;
    }
    public static Expr constFalse() {
        return Const.FALSE;
    }
}
