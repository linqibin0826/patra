package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;

public interface RuleSnapshotLoader {

    // TODO expr中 operationCode 需要全都重命名为 endpointName， 上游数据库已变更该字段名称 避免与operationType混淆
    ProvenanceSnapshot load(ProvenanceCode provenanceCode, String operationType, String operationCode);
}
