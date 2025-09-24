package com.patra.starter.expr.compiler.snapshot;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.rpc.client.ExprClient;
import com.patra.registry.api.rpc.client.ProvenanceClient;
import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.starter.expr.compiler.snapshot.convert.SnapshotAssembler;

import java.util.Objects;

/**
 * Registry backed snapshot loader that delegates DTO→domain conversion to a dedicated assembler.
 */
public class RegistryRuleSnapshotLoader implements RuleSnapshotLoader {

    private final ProvenanceClient provenanceClient;
    private final ExprClient exprClient;
    private final SnapshotAssembler snapshotAssembler;

    public RegistryRuleSnapshotLoader(ProvenanceClient provenanceClient,
                                      ExprClient exprClient,
                                      SnapshotAssembler snapshotAssembler) {
        this.provenanceClient = Objects.requireNonNull(provenanceClient);
        this.exprClient = Objects.requireNonNull(exprClient);
        this.snapshotAssembler = Objects.requireNonNull(snapshotAssembler);
    }

    @Override
    public ProvenanceSnapshot load(ProvenanceCode provenanceCode, String taskType, String operationCode) {
        ProvenanceResp provenance = provenanceClient.getProvenance(provenanceCode);
        ExprSnapshotResp snapshot = exprClient.getSnapshot(provenanceCode.getCode(), taskType, operationCode, null);
        return snapshotAssembler.assemble(provenance, snapshot, taskType, operationCode);
    }
}
