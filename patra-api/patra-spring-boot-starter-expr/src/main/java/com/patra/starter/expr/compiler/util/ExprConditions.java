package com.patra.starter.expr.compiler.util;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 条件判断工具：
 * - hasCondition(expr): 这棵树是否包含“有效条件”（任意 Atom），或被规范化后恒为 false。
 * - hasAtom(expr): 树中是否出现至少一个 Atom（不展开布尔恒等）。
 * <p>
 * 判定约定：
 * - Const(true) 视为“无条件”
 * - Const(false) 视为“有条件”（但不可满足）
 */
public final class ExprConditions {
    private ExprConditions() {
    }

    /**
     * 快速判断：是否包含至少一个 Atom。
     * 不做规范化、不考虑常量折叠。
     */
    public static boolean hasAtom(Expr expr) {
        String n = expr.getClass().getSimpleName();
        if ("Atom".equals(n)) return true;
        try {
            if ("Not".equals(n)) {
                Expr c = (Expr) getField(expr, "child");
                return hasAtom(c);
            }
            if ("And".equals(n) || "Or".equals(n)) {
                @SuppressWarnings("unchecked")
                List<Expr> cs = (List<Expr>) getField(expr, "children");
                for (Expr c : cs) if (hasAtom(c)) return true;
                return false;
            }
            // Const/其它：无 Atom
            return false;
        } catch (ReflectiveOperationException e) {
            // 保守返回：不确定时按“有条件”处理，避免漏判
            return true;
        }
    }

    /**
     * 规范化 + 常量折叠后的判断：
     * - 归约为 Const(true)  → 无条件（false）
     * - 归约为 Const(false) → 有条件（true）
     * - 否则看是否存在 Atom。
     */
    public static boolean hasCondition(Expr expr,
                                       ExprNormalizer normalizer,
                                       ProvenanceSnapshot snapshot) {
        Expr norm = normalizer.normalize(expr, snapshot, true);
        String n = norm.getClass().getSimpleName();
        if ("Const".equals(n)) {
            try {
                Object v = getField(norm, "value");
                return (v instanceof Boolean b) && !b; // false => 有条件（不可满足）
            } catch (ReflectiveOperationException ignored) {
                // 反射失败则退化为原始判定
            }
        }
        return hasAtom(norm);
    }

    // ========== 反射小工具 ==========
    private static Object getField(Object o, String f) throws ReflectiveOperationException {
        Field fd = o.getClass().getDeclaredField(f);
        fd.setAccessible(true);
        return fd.get(o);
    }
}
