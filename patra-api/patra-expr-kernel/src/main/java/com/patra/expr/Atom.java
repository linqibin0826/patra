package com.patra.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 原子条件：字段 + 操作符 + 值（值为代数类型）。
 * <p>设计目标：覆盖主流学术/文献平台的 term/范围/存在性等通用语义；平台差异通过渲染规则吸收。</p>
 */
public record Atom(String field, Op op, Val val) implements Expr {

    /**
     * 操作符：
     * <ul>
     *   <li>TERM：单值词项（支持 {@link TextMatch} 策略）。</li>
     *   <li>IN：多值集合中的任意一个。</li>
     *   <li>RANGE：范围（日期/日期时间/数值）。</li>
     *   <li>EXISTS：字段存在性（存在/不存在）。</li>
     *   <li>TOKEN：平台内建的特殊记号（如 ownerNasa、PMC2600426）。</li>
     * </ul>
     */
    public enum Op {TERM, IN, RANGE, EXISTS, TOKEN}

    /**
     * 值类型（代数类型）。不同操作符对值类型有明确约束。
     */
    public sealed interface Val permits Str, Strs, DateRange, DateTimeRange, NumberRange, Bool, Token {
    }

    /**
     * 字符串值（带匹配策略）。
     * <p>caseSensitive 用于确需区分大小写的场景；默认 false。</p>
     */
    public record Str(String v, TextMatch match, boolean caseSensitive) implements Val {
        public Str(String v, TextMatch match) {
            this(v, match, false);
        }
    }

    /**
     * 多字符串集合（用于 IN）。
     */
    public record Strs(List<String> v, boolean caseSensitive) implements Val {
        public Strs(List<String> v) {
            this(v, false);
        }
    }

    /**
     * 日期范围（日粒度）。端点可空；边界开闭由 boundary 指定。
     */
    public record DateRange(LocalDate from, LocalDate to, Boundary fromBoundary, Boundary toBoundary) implements Val {
        public DateRange(LocalDate from, LocalDate to) {
            this(from, to, Boundary.CLOSED, Boundary.CLOSED);
        }
    }

    /**
     * 日期时间范围（到秒/毫秒级）。适合支持 since/until 的平台。
     */
    public record DateTimeRange(Instant from, Instant to, Boundary fromBoundary, Boundary toBoundary) implements Val {
        public DateTimeRange(Instant from, Instant to) {
            this(from, to, Boundary.CLOSED, Boundary.CLOSED);
        }
    }

    /**
     * 数值范围。使用 BigDecimal 表示端点，便于等精度比较；端点可空。
     */
    public record NumberRange(BigDecimal from, BigDecimal to, Boundary fromBoundary,
                              Boundary toBoundary) implements Val {
        public NumberRange(BigDecimal from, BigDecimal to) {
            this(from, to, Boundary.CLOSED, Boundary.CLOSED);
        }
    }

    /**
     * 字段存在性：exists=true 表示要求存在；false 表示要求不存在。
     */
    public record Bool(boolean exists) implements Val {
    }

    /**
     * 特殊记号（平台内建 token）。
     * <p>kind 建议为 token 分类（如 "owner"、"pmcid"），value 为其取值（如 "nasa" 或 "PMC2600426"）。</p>
     */
    public record Token(String kind, String value) implements Val {
    }

    /**
     * 区间边界类型：开/闭。
     */
    public enum Boundary {OPEN, CLOSED}
}
