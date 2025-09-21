package com.patra.starter.expr.compiler.normalize;

import com.patra.expr.Atom;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认规范化实现（结构 + 值域（IN/TERM））。
 * <p>
 * 覆盖：
 * 1) 值域规范：
 * - TERM: value.trim()（空串不删除，交由校验阶段报错）
 * - IN:   trim → 删除空白项 → 稳定去重
 * IN([])  → Const(false)（恒假）
 * IN([x]) → TERM(x)（保留 caseSensitive，match 取默认 PHRASE）
 * <p>
 * 2) 结构规范：
 * - 展平 And/Or
 * - 单子节点折叠 And(x)->x, Or(x)->x
 * - 双重 Not 折叠 Not(Not(x))->x
 * - 单位元/零元：And(x,true)->x; And(x,false)->false; Or(x,false)->x; Or(x,true)->true
 * - 同层排序与去重（确定性输出）
 * <p>
 * 说明：
 * - 所有节点重建均走 Exprs 工厂，保障不可变与兼容性。
 * - 未做范围端点互换/补全，避免推断语义；此类处理交由 Capability/Policy。
 */
public class DefaultExprNormalizer implements ExprNormalizer {

    // ========== 外部入口 ==========
    @Override
    public Expr normalize(Expr expr, ProvenanceSnapshot snapshot, boolean strict) {
        Expr cur = expr;
        for (int i = 0; i < 4; i++) { // 小迭代上限，通常一轮即可稳定
            Expr next = normalizeOnce(cur);
            if (next == cur || canonical(next).equals(canonical(cur))) {
                return next;
            }
            cur = next;
        }
        return cur;
    }

    // ========== 单次规范化 ==========
    private Expr normalizeOnce(Expr e) {
        String cn = e.getClass().getSimpleName();

        // 1) Atom：做值域规范（TERM/IN）
        if ("Atom".equals(cn)) return normalizeAtom((Atom) e);

        // 2) Not：递归 + NNF（德摩根下推）+ 双重否定 + 常量取反
        if ("Not".equals(cn)) {
            Expr child = (Expr) getFieldQuiet(e, "child");
            // 先对 child 做一轮 normalize，便于后续下推/折叠
            Expr nChild = normalizeOnce(child);
            String cc = nChild.getClass().getSimpleName();

            // 双重否定：NOT(NOT(x)) → x
            if ("Not".equals(cc)) {
                Expr grand = (Expr) getFieldQuiet(nChild, "child");
                if (grand != null) {
                    return normalizeOnce(grand);
                }
            }

            // 常量取反：NOT(true/false) → false/true
            if ("Const".equals(cc)) {
                boolean v = isConstTrue(nChild);
                return v ? Exprs.constFalse() : Exprs.constTrue();
            }

            // 德摩根下推到原子层（NNF）：
            // NOT(AND(a,b,...)) → OR(NOT(a), NOT(b), ...)
            // NOT(OR(a,b,...))  → AND(NOT(a), NOT(b), ...)
            if ("And".equals(cc) || "Or".equals(cc)) {
                @SuppressWarnings("unchecked")
                List<Expr> cs = (List<Expr>) getFieldQuiet(nChild, "children");
                List<Expr> negChildren = new ArrayList<>(cs.size());
                for (Expr c : cs) {
                    // 这里不再额外 normalize，交给外层多轮 normalize 收敛
                    negChildren.add(Exprs.not(c));
                }
                if ("And".equals(cc)) {
                    return Exprs.or(negChildren);   // NOT(AND) → OR of NOTs
                } else {
                    return Exprs.and(negChildren);  // NOT(OR)  → AND of NOTs
                }
            }

            // NOT(Atom) 或 其它类型：保留 NOT 包裹
            return Exprs.not(nChild);
        }

        // 3) And/Or：递归、展平、单位元/零元、单子折叠、排序去重
        if ("And".equals(cn) || "Or".equals(cn)) {
            @SuppressWarnings("unchecked")
            List<Expr> children = (List<Expr>) getFieldQuiet(e, "children");
            List<Expr> normChildren = children.stream().map(this::normalizeOnce).collect(Collectors.toList());

            // 展平
            List<Expr> flat = new ArrayList<>();
            for (Expr c : normChildren) {
                if (c != null && c.getClass().getSimpleName().equals(cn)) {
                    @SuppressWarnings("unchecked")
                    List<Expr> sub = (List<Expr>) getFieldQuiet(c, "children");
                    flat.addAll(sub);
                } else {
                    flat.add(c);
                }
            }

            if ("And".equals(cn)) {
                // 单位元/零元
                List<Expr> kept = new ArrayList<>();
                for (Expr c : flat) {
                    if (isConstTrue(c)) continue;
                    if (isConstFalse(c)) return Exprs.constFalse();
                    kept.add(c);
                }
                // 单子折叠
                if (kept.isEmpty()) return Exprs.constTrue();
                if (kept.size() == 1) return kept.get(0);

                // 排序 + 去重
                kept = sortAndDedupe(kept);
                return Exprs.and(kept);
            } else {
                // Or
                List<Expr> kept = new ArrayList<>();
                for (Expr c : flat) {
                    if (isConstFalse(c)) continue;
                    if (isConstTrue(c)) return Exprs.constTrue();
                    kept.add(c);
                }
                if (kept.isEmpty()) return Exprs.constFalse();
                if (kept.size() == 1) return kept.get(0);

                kept = sortAndDedupe(kept);
                return Exprs.or(kept);
            }
        }

        // 4) 其它节点：保守返回
        return e;
    }

    // ========== Atom 值域规范 ==========
    private Expr normalizeAtom(Atom a) {
        // 不改变 field/op；仅根据 val 的类型做等价改写
        return switch (a.op()) {
            case TERM -> normalizeTerm(a);
            case IN -> normalizeIn(a);
            default -> a; // RANGE/EXISTS/TOKEN 等维持原样
        };
    }

    private Expr normalizeTerm(Atom a) {
        if (!(a.val() instanceof Atom.Str sv)) return a;
        // 仅去除前后空白；空串不删除，交由校验器报错
        String v = sv.v();
        String trimmed = (v == null) ? null : v.trim();
        if (Objects.equals(v, trimmed)) return a; // 无变化
        // 通过工厂重建 TERM，保持 match 与 caseSensitive
        if (sv.caseSensitive()) {
            return Exprs.term(a.field(), trimmed, sv.match(), true);
        } else {
            return Exprs.term(a.field(), trimmed, sv.match());
        }
    }

    private Expr normalizeIn(Atom a) {
        if (!(a.val() instanceof Atom.Strs iv)) return a;
        List<String> vs = iv.v();
        if (vs == null) vs = List.of();

        // 清洗：trim → 删除空白 → 稳定去重（保留首次）
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : vs) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) set.add(t);
        }

        // 空集：等价为 false
        if (set.isEmpty()) {
            return Exprs.constFalse();
        }

        // 单项：降阶为 TERM，保留大小写敏感策略
        if (set.size() == 1) {
            String only = set.iterator().next();
            if (iv.caseSensitive()) {
                return Exprs.term(a.field(), only, TextMatch.PHRASE, true);
            } else {
                return Exprs.term(a.field(), only, TextMatch.PHRASE);
            }
        }

        // 多项：以清洗后的顺序重建 IN
        return Exprs.in(a.field(), new ArrayList<>(set), iv.caseSensitive());
    }

    // ========== 排序与去重（确定性输出） ==========
    private List<Expr> sortAndDedupe(List<Expr> children) {
        List<Expr> sorted = new ArrayList<>(children);
        sorted.sort(this::compareExpr);
        List<Expr> out = new ArrayList<>(sorted.size());
        String prev = null;
        for (Expr e : sorted) {
            String can = canonical(e);
            if (!Objects.equals(prev, can)) out.add(e);
            prev = can;
        }
        return out;
    }

    // 排序规则：Atom < Not < And < Or < Const；同类再按 canonical 比较
    private int compareExpr(Expr a, Expr b) {
        int ra = rank(a), rb = rank(b);
        if (ra != rb) return Integer.compare(ra, rb);
        return canonical(a).compareTo(canonical(b));
    }

    private int rank(Expr e) {
        String n = e.getClass().getSimpleName();
        return switch (n) {
            case "Atom" -> 0;
            case "Not" -> 1;
            case "And" -> 2;
            case "Or" -> 3;
            case "Const" -> 4;
            default -> 9;
        };
    }

    // ========== canonical 串（用于去重/对比幂等） ==========
    private String canonical(Expr e) {
        String n = e.getClass().getSimpleName();
        try {
            if ("Atom".equals(n)) {
                String field = String.valueOf(call(e, "field"));
                String op = String.valueOf(call(e, "op"));
                Object val = call(e, "val");
                return "A(" + field + "|" + op + "|" + String.valueOf(val) + ")";
            }
            if ("Not".equals(n)) {
                Expr c = (Expr) getFieldQuiet(e, "child");
                return "N(" + canonical(c) + ")";
            }
            if ("And".equals(n) || "Or".equals(n)) {
                @SuppressWarnings("unchecked")
                List<Expr> cs = (List<Expr>) getFieldQuiet(e, "children");
                List<String> xs = cs.stream().map(this::canonical).collect(Collectors.toList());
                return (n.equals("And") ? "AND" : "OR") + xs;
            }
            if ("Const".equals(n)) {
                Object v = getFieldQuiet(e, "value");
                return "C(" + String.valueOf(v) + ")";
            }
        } catch (Exception ignored) {
        }
        return n + "@" + Integer.toHexString(System.identityHashCode(e));
    }

    // ========== 辅助：常量判断 ==========
    private boolean isConstTrue(Expr e) {
        return isConstWith(e, true);
    }

    private boolean isConstFalse(Expr e) {
        return isConstWith(e, false);
    }

    private boolean isConstWith(Expr e, boolean v) {
        if (!"Const".equals(e.getClass().getSimpleName())) return false;
        Object val = getFieldQuiet(e, "value");
        return (val instanceof Boolean b) && b == v;
    }

    // ========== 反射取字段/Getter（只读） ==========
    private Object getFieldQuiet(Object o, String f) {
        try {
            Field fd = o.getClass().getDeclaredField(f);
            fd.setAccessible(true);
            return fd.get(o);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object call(Object o, String m) {
        try {
            var md = o.getClass().getMethod(m);
            md.setAccessible(true);
            return md.invoke(o);
        } catch (Exception e) {
            return null;
        }
    }
}
