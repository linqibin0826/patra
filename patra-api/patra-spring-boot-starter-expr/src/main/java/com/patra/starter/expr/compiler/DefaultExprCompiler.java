package com.patra.starter.expr.compiler;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.boot.CompilerProperties;
import com.patra.starter.expr.compiler.checker.CapabilityChecker;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.slice.ExprSlicer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;
import com.patra.starter.expr.compiler.util.ExprConditions;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class DefaultExprCompiler implements ExprCompiler {

    private final RuleSnapshotLoader snapshotLoader;
    private final CompilerProperties props;
    // 构造器新增一个参数：
    private final CapabilityChecker checker;
    private final ExprRenderer renderer;
    private final ExprSlicer slicer;
    private final ExprNormalizer normalizer;

    @Override
    public Boolean hasAtom(Expr expr) {
        return ExprConditions.hasAtom(expr);
    }

    @Override
    public CompileResult compile(Expr expr,
                                 ProvenanceCode provenance,
                                 String operation,
                                 CompileOptions options) {
        final String op = opOrDefault(operation);

        // 1) 先拉快照（只一次）
        final ProvenanceSnapshot snap = loadSnapshot(provenance, op);

        // 2) 校验（使用已有快照，避免二次远程）
        Expr norm = normalizer.normalize(expr, snap, options.strict());

        ValidationReport report = validateWithSnapshot(norm, provenance, op, options.strict(), snap);

        // 2.1 短路：有错误就不渲染
        if (!report.ok()) {
            return new CompileResult(
                    "", Map.of(), report, toRef(snap),
                    options.traceEnabled() ? new RenderTrace(List.of()) : null
            );
        }

        // 3) 渲染
        var outcome = renderer.render(norm, snap, provenance, op, options.traceEnabled());
        String query = outcome != null ? (outcome.query() == null ? "" : outcome.query()) : "";
        Map<String, String> params = outcome != null && outcome.params() != null
                ? Map.copyOf(outcome.params())
                : Map.of();

        // 3.1 长度约束
        if (options.maxQueryLength() > 0 && query.length() > options.maxQueryLength()) {
            // 策略：此处先给出 ERROR 并短路；若你想截断可改成 WARN + 截断
            var errs = new java.util.ArrayList<>(report.errors());
            errs.add(new Issue(Severity.ERROR, "E-QUERY-LEN-MAX",
                    "渲染后的 query 超过最大长度限制",
                    Map.of("max", options.maxQueryLength(), "len", query.length())));
            report = new ValidationReport(report.warnings(), errs);
            return new CompileResult("", Map.of(), report, toRef(snap),
                    options.traceEnabled() ? outcome.trace() : null);
        }

        // 4) 合并 warnings（去重）
        var mergedWarns = new java.util.LinkedHashSet<Issue>();
        mergedWarns.addAll(report.warnings());
        if (outcome != null && outcome.warnings() != null) mergedWarns.addAll(outcome.warnings());
        report = new ValidationReport(java.util.List.copyOf(mergedWarns), report.errors());

        // 5) 输出
        return new CompileResult(
                query,
                params,
                report,
                toRef(snap),
                options.traceEnabled() ? outcome.trace() : null
        );
    }

    // 私有：基于现有快照做校验（避免 validate 内部再次 loadSnapshot）
    private ValidationReport validateWithSnapshot(Expr expr,
                                                  ProvenanceCode provenance,
                                                  String operation,
                                                  boolean strict,
                                                  ProvenanceSnapshot snap) {
        var issues = checker.check(expr, snap, provenance, operation, strict);
        var warns = issues.stream().filter(i -> i.severity() == Severity.WARN).toList();
        var errs  = issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
        return new ValidationReport(warns, errs);
    }


    // ========== 切片重写（占位直通） ==========
    @Override
    public SliceResult sliceAndRewrite(Expr expr,
                                       ProvenanceCode provenance,
                                       String operation,
                                       SliceOptions options) {
        ProvenanceSnapshot snap = loadSnapshot(provenance, opOrDefault(operation));
        var o = slicer.sliceTopLevelDateRange(
                expr, snap, provenance, operation,
                options.primaryDateField(),
                options.boundStyle(),
                options.targetWindowSize(),
                options.overlap(),
                options.maxWindowCount(),
                options.respectGranularity(),
                options.strict()
        );

        // 将 slicer 的 TimeWindow 转为门面定义的 TimeWindow（字段名一致时可直接复用）：
        List<TimeWindow> tws = o.windows().stream()
                .map(w -> new TimeWindow(w.startDate(), w.endDate(), w.boundStyle(), w.datetype()))
                .toList();

        return new SliceResult(tws, o.rewritten(), o.warnings(), toRef(snap));

    }

    // ========== 私有辅助 ==========

    private ProvenanceSnapshot loadSnapshot(ProvenanceCode provenance, String operation) {
        return snapshotLoader.load(provenance, operation);
    }

    private String opOrDefault(String operation) {
        String op = operation;
        if (op == null || op.isBlank()) {
            var reg = props.registryApi();
            if (reg != null && reg.operationDefault() != null && !reg.operationDefault().isBlank()) {
                op = reg.operationDefault();
            } else {
                op = "search";
            }
        }
        return op;
    }

    private SnapshotRef toRef(ProvenanceSnapshot s) {
        return new SnapshotRef(
                s.key() != null ? s.key().id() : null,
                s.key() != null ? s.key().code() : null,
                s.operation(),
                s.version(),
                s.updatedAt()
        );
    }
}
