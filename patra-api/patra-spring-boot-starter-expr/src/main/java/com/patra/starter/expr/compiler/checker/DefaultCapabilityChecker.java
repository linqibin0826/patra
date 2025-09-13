package com.patra.starter.expr.compiler.checker;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Atom;
import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.ExprCompiler.Issue;
import com.patra.starter.expr.compiler.ExprCompiler.Severity;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class DefaultCapabilityChecker implements CapabilityChecker {

    @Override
    public List<Issue> check(Expr expr,
                             ProvenanceSnapshot snapshot,
                             ProvenanceCode provenance,
                             String operation,
                             boolean strict) {
        List<Issue> issues = new ArrayList<>();
        walk(expr, false, snapshot, issues, strict);
        return issues;
    }

    // ===== AST 递归遍历（只识别 NOT→Atom 的直接取反；复杂德摩根在后续版本处理） =====
    private void walk(Expr e,
                      boolean underNot,
                      ProvenanceSnapshot snap,
                      List<Issue> out,
                      boolean strict) {
        if (e instanceof Atom a) {
            validateAtom(a, underNot, snap, out, strict);
            return;
        }
        // 其余节点：And/Or/Not —— 按名称反射式处理，避免依赖具体实现类
        String cn = e.getClass().getSimpleName();
        try {
            if ("Not".equals(cn)) {
                var f = e.getClass().getDeclaredField("child");
                f.setAccessible(true);
                Object child = f.get(e);
                if (child instanceof Expr ce) {
                    walk(ce, true, snap, out, strict);
                } else {
                    out.add(err("E-STRUCT-NOT", "NOT 节点子类型非法", Map.of("node", e.getClass().getName())));
                }
            } else if ("And".equals(cn) || "Or".equals(cn)) {
                var f = e.getClass().getDeclaredField("children");
                f.setAccessible(true);
                Object val = f.get(e);
                if (val instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Expr ce) {
                            walk(ce, underNot, snap, out, strict);
                        } else {
                            out.add(err("E-STRUCT-CHILD", "布尔节点子类型非法", Map.of("node", e.getClass().getName())));
                        }
                    }
                } else {
                    out.add(err("E-STRUCT-FIELD", "布尔节点 children 缺失或非法", Map.of("node", e.getClass().getName())));
                }
            } else {
                out.add(warn("W-NODE-UNKNOWN", "未知节点类型，跳过校验", Map.of("node", e.getClass().getName())));
            }
        } catch (ReflectiveOperationException ex) {
            out.add(err("E-REFLECT", "访问 AST 节点失败", Map.of("node", e.getClass().getName(), "ex", ex.getClass().getSimpleName())));
        }
    }

    // ===== 针对 Atom 的最小能力校验 =====
    private void validateAtom(Atom a,
                              boolean underNot,
                              ProvenanceSnapshot snap,
                              List<Issue> out,
                              boolean strict) {
        String field = a.field();
        ProvenanceSnapshot.FieldDictEntry dict = snap.fieldDict().get(field);
        if (dict == null) {
            out.add(err("E-FIELD-NOT-FOUND", "字段在当前数据源不可用", Map.of("field", field)));
            return;
        }

        ProvenanceSnapshot.CapabilityRule cap = snap.capability().get(field);
        if (cap == null) {
            out.add(err("E-CAP-NOT-FOUND", "字段缺少能力定义", Map.of("field", field)));
            return;
        }

        String op = a.op().name(); // TERM/IN/...
        if (cap.ops() == null || !cap.ops().contains(op)) {
            out.add(err("E-OP-NOT-ALLOWED", "该字段不允许此操作符", Map.of("field", field, "op", op)));
            return;
        }

        // NOT 支持性检查（仅对 NOT 直接包裹 Atom 的情形）
        if (underNot) {
            if (!cap.supportsNotOp()) {
                out.add(err("E-NOT-DISABLED", "该字段不支持 NOT 取反", Map.of("field", field)));
            } else {
                List<String> neg = cap.negatableOps();
                if (neg != null && !neg.isEmpty() && !neg.contains(op)) {
                    out.add(err("E-NOT-OP-DISABLED", "该操作符不支持 NOT 取反", Map.of("field", field, "op", op)));
                }
            }
        }

        // 细分值类型校验
        switch (a.val()) {
            case Atom.Str sv -> checkTERM(sv, cap, field, out);
            case Atom.Strs iv -> checkIN(iv, cap, field, out);
            case Atom.DateRange dr -> checkDateRange(dr, cap, field, out);
            case Atom.DateTimeRange dtr -> checkDateTimeRange(dtr, cap, field, out);
            case Atom.NumberRange nr -> checkNumberRange(nr, cap, field, out);
            case Atom.Bool b -> checkExists(b, cap, field, out);
            case Atom.Token t -> checkToken(t, cap, field, out, strict);
            default -> out.add(warn("W-VAL-UNKNOWN", "未知值类型，跳过细节校验", Map.of("field", field)));
        }
    }

    private void checkTERM(Atom.Str sv, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out) {
        if (sv.v() == null || sv.v().isBlank()) {
            if (!cap.termAllowBlank()) {
                out.add(err("E-TERM-BLANK", "TERM 不允许空值", Map.of("field", field)));
            }
            return;
        }
        int len = sv.v().length();
        if (cap.termMinLen() > 0 && len < cap.termMinLen()) {
            out.add(err("E-TERM-LEN-MIN", "TERM 长度过短", Map.of("field", field, "min", cap.termMinLen(), "len", len)));
        }
        if (cap.termMaxLen() > 0 && len > cap.termMaxLen()) {
            out.add(err("E-TERM-LEN-MAX", "TERM 长度超限", Map.of("field", field, "max", cap.termMaxLen(), "len", len)));
        }
        if (cap.termPattern() != null && !cap.termPattern().isBlank()) {
            Pattern p = Pattern.compile(cap.termPattern());
            if (!p.matcher(sv.v()).matches()) {
                out.add(err("E-TERM-PATTERN", "TERM 不满足正则约束", Map.of("field", field, "pattern", cap.termPattern())));
            }
        }
        if (sv.caseSensitive() && !cap.termCaseSensitiveAllowed()) {
            out.add(err("E-TERM-CS-NOT-ALLOWED", "不允许大小写敏感匹配", Map.of("field", field)));
        }
        // match 策略集合校验（能力端是字符串集合）
        if (cap.termMatches() != null && !cap.termMatches().isEmpty()) {
            String m = sv.match().name(); // PHRASE/EXACT/ANY
            if (!cap.termMatches().contains(m)) {
                out.add(err("E-TERM-MATCH-NOT-ALLOWED", "不支持的匹配策略", Map.of("field", field, "match", m)));
            }
        }
    }

    private void checkIN(Atom.Strs iv, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out) {
        List<String> vs = iv.v();
        if (vs == null || vs.isEmpty()) {
            out.add(err("E-IN-EMPTY", "IN 值集合不能为空", Map.of("field", field)));
            return;
        }
        if (cap.inMaxSize() > 0 && vs.size() > cap.inMaxSize()) {
            out.add(err("E-IN-SIZE", "IN 项数超限", Map.of("field", field, "max", cap.inMaxSize(), "size", vs.size())));
        }
        if (iv.caseSensitive() && !cap.inCaseSensitiveAllowed()) {
            out.add(err("E-IN-CS-NOT-ALLOWED", "IN 不允许大小写敏感", Map.of("field", field)));
        }
    }

    private void checkDateRange(Atom.DateRange dr, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out) {
        if (cap.rangeKind() != ProvenanceSnapshot.CapabilityRule.RangeKind.DATE) {
            out.add(err("E-RANGE-KIND", "该字段不支持 DATE 范围", Map.of("field", field, "kind", cap.rangeKind())));
            return;
        }
        LocalDate from = dr.from();
        LocalDate to = dr.to();
        if (from == null && to == null) {
            out.add(err("E-RANGE-OPEN", "DATE 范围不能两端皆空", Map.of("field", field)));
            return;
        }
        if (from != null && cap.dateMin() != null && from.isBefore(cap.dateMin())) {
            out.add(err("E-DATE-MIN", "起始日期早于允许下限", Map.of("field", field, "min", cap.dateMin(), "from", from)));
        }
        if (to != null && cap.dateMax() != null && to.isAfter(cap.dateMax())) {
            out.add(err("E-DATE-MAX", "结束日期晚于允许上限", Map.of("field", field, "max", cap.dateMax(), "to", to)));
        }
    }

    private void checkDateTimeRange(Atom.DateTimeRange dtr, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out) {
        if (cap.rangeKind() != ProvenanceSnapshot.CapabilityRule.RangeKind.DATETIME) {
            out.add(err("E-RANGE-KIND", "该字段不支持 DATETIME 范围", Map.of("field", field, "kind", cap.rangeKind())));
            return;
        }
        if (dtr.from() == null && dtr.to() == null) {
            out.add(err("E-RANGE-OPEN", "DATETIME 范围不能两端皆空", Map.of("field", field)));
        }
        if (dtr.from() != null && cap.datetimeMin() != null && dtr.from().isBefore(cap.datetimeMin())) {
            out.add(err("E-DT-MIN", "起始时间早于允许下限", Map.of("field", field, "min", cap.datetimeMin(), "from", dtr.from())));
        }
        if (dtr.to() != null && cap.datetimeMax() != null && dtr.to().isAfter(cap.datetimeMax())) {
            out.add(err("E-DT-MAX", "结束时间晚于允许上限", Map.of("field", field, "max", cap.datetimeMax(), "to", dtr.to())));
        }
    }

    private void checkNumberRange(Atom.NumberRange nr, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out) {
        if (cap.rangeKind() != ProvenanceSnapshot.CapabilityRule.RangeKind.NUMBER) {
            out.add(err("E-RANGE-KIND", "该字段不支持 NUMBER 范围", Map.of("field", field, "kind", cap.rangeKind())));
        }
        // numberMin/Max 在快照里是字符串（高精度）；此处仅做“是否同时为空”的结构校验
        if (nr.from() == null && nr.to() == null) {
            out.add(err("E-RANGE-OPEN", "NUMBER 范围不能两端皆空", Map.of("field", field)));
        }
    }

    private void checkExists(Atom.Bool b, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out) {
        if (!cap.existsSupported()) {
            out.add(err("E-EXISTS-UNSUPPORTED", "该字段不支持 EXISTS", Map.of("field", field)));
        }
    }

    private void checkToken(Atom.Token t, ProvenanceSnapshot.CapabilityRule cap, String field, List<Issue> out, boolean strict) {
        List<String> kinds = cap.tokenKinds();
        if (kinds != null && !kinds.isEmpty() && (t.kind() == null || !kinds.contains(t.kind().toLowerCase(Locale.ROOT)))) {
            out.add(err("E-TOKEN-KIND", "不支持的 token 种类", Map.of("field", field, "kind", t.kind())));
        }
        if (cap.tokenValuePattern() != null && !cap.tokenValuePattern().isBlank() && t.value() != null) {
            Pattern p = Pattern.compile(cap.tokenValuePattern());
            if (!p.matcher(t.value()).matches()) {
                out.add(err("E-TOKEN-PATTERN", "token 值不满足正则约束", Map.of("field", field)));
            }
        } else if (strict && (t.value() == null || t.value().isBlank())) {
            out.add(err("E-TOKEN-BLANK", "token 值不能为空（strict 模式）", Map.of("field", field)));
        }
    }

    private Issue warn(String code, String msg, Map<String, Object> ctx) {
        return new Issue(Severity.WARN, code, msg, ctx);
    }

    private Issue err(String code, String msg, Map<String, Object> ctx) {
        return new Issue(Severity.ERROR, code, msg, ctx);
    }
}
