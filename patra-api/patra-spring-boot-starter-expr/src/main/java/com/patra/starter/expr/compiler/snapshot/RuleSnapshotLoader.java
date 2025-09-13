package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;

/**
 * 规则快照加载端口：屏蔽底层来源（registry/file/inline）。
 */
public interface RuleSnapshotLoader {

    /**
     * 加载指定数据源 + operation 的规则快照。
     */
    ProvenanceSnapshot load(ProvenanceCode provenanceCode, String operation);
}
