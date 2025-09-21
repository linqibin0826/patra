package com.patra.starter.expr.compiler.render;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Atom;
import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.ExprCompiler.Issue;
import com.patra.starter.expr.compiler.ExprCompiler.RenderTrace;
import com.patra.starter.expr.compiler.ExprCompiler.Severity;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 极简渲染器：
 * - 支持 Atom(TERM) / Atom(IN)
 * - 仅消费 Emit=query/params 两类规则
 * - 规则选择：fieldKey 完全相等 + op 相同；TERM 会尝试匹配 matchType（忽略大小写）
 * - 模板支持占位符：{{v}}（TERM）；{{items}}（IN，已按 itemTemplate+joiner 渲染）
 * - 未识别节点：跳过并给出 WARN
 * - 布尔结构：简单 AND 聚合（以 " AND " 连接），OR/NOT 暂跳过并 WARN
 */
public class DefaultExprRenderer implements ExprRenderer {

    @Override
    public Outcome render(Expr expr,
                          ProvenanceSnapshot snapshot,
                          ProvenanceCode provenance,
                          String operation,
                          boolean traceEnabled) {
        List<String> queryPieces = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();
        List<Issue> warns = new ArrayList<>();
        List<RenderTrace.Hit> hits = new ArrayList<>();

        walk(expr, snapshot, queryPieces, params, warns, hits);

        String query = String.join(" AND ", queryPieces);
        RenderTrace trace = traceEnabled ? new RenderTrace(hits) : null;
        return new Outcome(query, params, trace, warns);
    }

    // ===== 遍历：仅对 AND 做深度展开，OR/NOT 暂警告并跳过 =====
    private void walk(Expr e,
                      ProvenanceSnapshot snap,
                      List<String> queryPieces,
                      Map<String, String> params,
                      List<Issue> warns,
                      List<RenderTrace.Hit> hits) {
        if (e instanceof Atom a) {
            renderAtom(a, snap, queryPieces, params, warns, hits);
            return;
        }
        String cn = e.getClass().getSimpleName();
        try {
            if ("And".equals(cn)) {
                var f = e.getClass().getDeclaredField("children");
                f.setAccessible(true);
                Object v = f.get(e);
                if (v instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Expr ce) walk(ce, snap, queryPieces, params, warns, hits);
                    }
                }
            } else if ("Or".equals(cn) || "Not".equals(cn)) {
                warns.add(warn("W-RENDER-BOOL-PARTIAL", "当前渲染器仅支持 AND 聚合，OR/NOT 将被跳过",
                        Map.of("node", cn)));
            } else {
                warns.add(warn("W-RENDER-NODE", "未知节点类型，跳过渲染", Map.of("node", cn)));
            }
        } catch (ReflectiveOperationException ex) {
            warns.add(warn("W-RENDER-REFLECT", "访问 AST 节点失败，跳过渲染",
                    Map.of("node", cn, "ex", ex.getClass().getSimpleName())));
        }
    }

    private void renderAtom(Atom a,
                            ProvenanceSnapshot snap,
                            List<String> queryPieces,
                            Map<String, String> params,
                            List<Issue> warns,
                            List<RenderTrace.Hit> hits) {
        // 选择规则
        ProvenanceSnapshot.RenderRuleTemplate rule = selectRule(a, snap, warns);
        if (rule == null) return;

        // 渲染
        switch (a.op()) {
            case TERM -> {
                if (!(a.val() instanceof Atom.Str sv)) {
                    warns.add(warn("W-VAL-MISMATCH", "TERM 值类型不匹配，跳过", Map.of("field", a.field())));
                    return;
                }
                String qFrag = null;
                if (rule.emit() == ProvenanceSnapshot.RenderRuleTemplate.Emit.query) {
                    String tpl = nz(rule.template());
                    qFrag = tpl.replace("{{v}}", quote(sv.v()));
                }
                if (rule.emit() == ProvenanceSnapshot.RenderRuleTemplate.Emit.params) {
                    // 若规则提供了 params 映射，优先根据映射填值；否则尝试用 field->value
                    if (rule.params() != null && !rule.params().isEmpty()) {
                        rule.params().forEach((stdKey, providerParam) -> {
                            params.put(providerParam, sv.v());
                        });
                    } else {
                        params.put(a.field(), sv.v());
                    }
                }
                if (qFrag != null && !qFrag.isBlank()) queryPieces.add(qFrag);
                hits.add(new RenderTrace.Hit(a.field(), "TERM", rule.priority(), hitId(rule)));
            }
            case IN -> {
                if (!(a.val() instanceof Atom.Strs iv)) {
                    warns.add(warn("W-VAL-MISMATCH", "IN 值类型不匹配，跳过", Map.of("field", a.field())));
                    return;
                }
                if (rule.emit() == ProvenanceSnapshot.RenderRuleTemplate.Emit.query) {
                    String itemTpl = nz(rule.itemTemplate(), "{{v}}");
                    String joined = iv.v().stream()
                            .map(this::quote)
                            .map(s -> itemTpl.replace("{{v}}", s))
                            .collect(Collectors.joining(nz(rule.joiner(), " OR ")));
                    if (rule.wrapGroup()) joined = "(" + joined + ")";
                    String tpl = nz(rule.template(), "{{items}}");
                    String qFrag = tpl.replace("{{items}}", joined);
                    queryPieces.add(qFrag);
                } else if (rule.emit() == ProvenanceSnapshot.RenderRuleTemplate.Emit.params) {
                    if (rule.params() != null && !rule.params().isEmpty()) {
                        rule.params().forEach((stdKey, providerParam) -> {
                            params.put(providerParam, String.join(",", iv.v()));
                        });
                    } else {
                        params.put(a.field(), String.join(",", iv.v()));
                    }
                }
                hits.add(new RenderTrace.Hit(a.field(), "IN", rule.priority(), hitId(rule)));
            }
            default -> warns.add(warn("W-OP-UNSUPPORTED", "暂不支持该操作符的渲染，已跳过",
                    Map.of("field", a.field(), "op", a.op().name())));
        }
    }

    private ProvenanceSnapshot.RenderRuleTemplate selectRule(Atom a,
                                                             ProvenanceSnapshot snap,
                                                             List<Issue> warns) {
        ProvenanceSnapshot.RenderRuleTemplate.Op need =
                a.op() == Atom.Op.TERM ? ProvenanceSnapshot.RenderRuleTemplate.Op.term :
                        a.op() == Atom.Op.IN ? ProvenanceSnapshot.RenderRuleTemplate.Op.in : null;
        if (need == null) return null;

        String match = null;
        if (a.op() == Atom.Op.TERM && a.val() instanceof Atom.Str sv && sv.match() != null) {
            match = sv.match().name().toLowerCase(Locale.ROOT);
        }

        // renderRules 已按 priority 降序；这里选择第一个最匹配的
        for (var r : snap.renderRules()) {
            if (!Objects.equals(r.fieldKey(), a.field())) continue;
            if (r.op() != need) continue;
            if (r.matchType() != null && match != null && !r.matchType().equalsIgnoreCase(match)) continue;
            return r;
        }
        warns.add(warn("W-RULE-NOT-FOUND", "未找到匹配的渲染规则，已跳过",
                Map.of("field", a.field(), "op", a.op().name(), "match", match)));
        return null;
    }

    // ===== 工具 =====
    private String hitId(ProvenanceSnapshot.RenderRuleTemplate r) {
        // 简易可读 ID：field|op|prio
        return r.fieldKey() + "|" + r.op() + "|" + r.priority();
    }

    private Issue warn(String code, String msg, Map<String, Object> ctx) {
        return new Issue(Severity.WARN, code, msg, ctx);
    }

    private String quote(String s) {
        if (s == null) return "\"\"";
        String esc = s.replace("\"", "\\\"");
        return "\"" + esc + "\"";
        // 后续可接入 {{q v}} 助手与更复杂转义策略
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private String nz(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
