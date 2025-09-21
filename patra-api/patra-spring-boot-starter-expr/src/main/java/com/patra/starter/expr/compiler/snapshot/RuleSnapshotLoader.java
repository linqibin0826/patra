package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;

public interface RuleSnapshotLoader {
    ProvenanceSnapshot load(ProvenanceCode provenanceCode, String taskType, String operationCode);
}
