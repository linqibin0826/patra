package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import lombok.RequiredArgsConstructor;

/**
 * 基于 registry Feign 接口的规则快照加载器。
 */
@RequiredArgsConstructor
public class RegistryRuleSnapshotLoader implements RuleSnapshotLoader {


    @Override
    public ProvenanceSnapshot load(ProvenanceCode provenanceCode, String operation) {
        return null;
    }
}
