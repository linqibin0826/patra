package com.patra.starter.expr.compiler.check;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.List;

public interface CapabilityChecker {
    List<Issue> check(Expr expression, ProvenanceSnapshot snapshot, boolean strictMode);
}
