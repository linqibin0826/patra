package com.patra.starter.expr.compiler;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.check.CapabilityChecker;
import com.patra.starter.expr.compiler.model.CompileRequest;
import com.patra.starter.expr.compiler.model.CompileResult;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.model.IssueSeverity;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.model.SnapshotRef;
import com.patra.starter.expr.compiler.model.ValidationReport;
import com.patra.starter.expr.compiler.normalize.ExprNormalizer;
import com.patra.starter.expr.compiler.render.ExprRenderer;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import com.patra.starter.expr.compiler.snapshot.RuleSnapshotLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultExprCompiler implements ExprCompiler {

    private final RuleSnapshotLoader snapshotLoader;
    private final CapabilityChecker capabilityChecker;
    private final ExprNormalizer normalizer;
    private final ExprRenderer renderer;

    public DefaultExprCompiler(RuleSnapshotLoader snapshotLoader,
                               CapabilityChecker capabilityChecker,
                               ExprNormalizer normalizer,
                               ExprRenderer renderer) {
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader);
        this.capabilityChecker = Objects.requireNonNull(capabilityChecker);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.renderer = Objects.requireNonNull(renderer);
    }

    @Override
    public CompileResult compile(CompileRequest request) {
        Objects.requireNonNull(request, "request");

        ProvenanceSnapshot snapshot = snapshotLoader.load(request.provenance(), request.taskType(), request.operationCode());
        Expr normalized = normalizer.normalize(request.expression(), request.options().strict());

        List<Issue> issues = capabilityChecker.check(normalized, snapshot, request.options().strict());
        List<Issue> warnings = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.severity() == IssueSeverity.ERROR) {
                errors.add(issue);
            } else {
                warnings.add(issue);
            }
        }
        ValidationReport report = new ValidationReport(warnings, errors);

        if (!errors.isEmpty()) {
            return new CompileResult("", Map.of(), normalized, report, toRef(snapshot, request.operationCode()), request.options().traceEnabled() ? new RenderTrace(List.of()) : null);
        }

        ExprRenderer.RenderOutcome outcome = renderer.render(normalized, snapshot, request.options().traceEnabled());

        List<Issue> mergedWarnings = new ArrayList<>(report.warnings());
        mergedWarnings.addAll(outcome.warnings());
        List<Issue> mergedErrors = new ArrayList<>(report.errors());

        if (request.options().maxQueryLength() > 0 && outcome.query().length() > request.options().maxQueryLength()) {
            mergedErrors.add(Issue.error("E-QUERY-LEN-MAX",
                    "Rendered query exceeds length budget",
                    Map.of("max", request.options().maxQueryLength(), "actual", outcome.query().length())));
            ValidationReport finalReport = new ValidationReport(mergedWarnings, mergedErrors);
            return new CompileResult("", Map.of(), normalized, finalReport, toRef(snapshot, request.operationCode()), outcome.trace());
        }

        ValidationReport finalReport = new ValidationReport(mergedWarnings, mergedErrors);
        return new CompileResult(outcome.query(), outcome.params(), normalized, finalReport, toRef(snapshot, request.operationCode()), outcome.trace());
    }

    private SnapshotRef toRef(ProvenanceSnapshot snapshot, String operationCode) {
        ProvenanceSnapshot.Identity id = snapshot.identity();
        return new SnapshotRef(id.provenanceId(), id.code(), operationCode, snapshot.version(), snapshot.capturedAt());
    }
}
