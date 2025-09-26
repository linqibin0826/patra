package com.patra.starter.expr.compiler.snapshot.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.expr.Atom;
import com.patra.common.constant.RegistryKeys;
import com.patra.common.enums.RegistryConfigScope;
import com.patra.registry.api.rpc.dto.expr.ApiParamMappingResp;
import com.patra.registry.api.rpc.dto.expr.ExprCapabilityResp;
import com.patra.registry.api.rpc.dto.expr.ExprFieldResp;
import com.patra.registry.api.rpc.dto.expr.ExprRenderRuleResp;
import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.rpc.dto.provenance.ProvenanceResp;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Converts registry DTOs into the starter's immutable {@link ProvenanceSnapshot} model.
 */
@SuppressWarnings("unused")
public class SnapshotAssembler {

    private final ObjectMapper objectMapper;

    public SnapshotAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProvenanceSnapshot assemble(ProvenanceResp provenance,
                                       ExprSnapshotResp snapshot,
                                       String taskType,
                                       String operationCode) {
        Objects.requireNonNull(provenance, "provenance");
        Map<String, ProvenanceSnapshot.FieldDefinition> fields = new HashMap<>();
        for (ExprFieldResp field : nullSafe(snapshot != null ? snapshot.fields() : null)) {
            fields.put(field.fieldKey(), new ProvenanceSnapshot.FieldDefinition(
                    field.fieldKey(),
                    field.displayName(),
                    field.description(),
                    ProvenanceSnapshot.DataType.valueOf(field.dataTypeCode().toUpperCase(Locale.ROOT)),
                    ProvenanceSnapshot.Cardinality.valueOf(field.cardinalityCode().toUpperCase(Locale.ROOT)),
                    field.exposable(),
                    field.dateField()
            ));
        }

        Map<String, ProvenanceSnapshot.Capability> capabilities = new HashMap<>();
        for (ExprCapabilityResp capability : nullSafe(snapshot != null ? snapshot.capabilities() : null)) {
            capabilities.put(capability.fieldKey(), toCapability(capability));
        }

        Map<String, ProvenanceSnapshot.ApiParameter> apiParameters = new HashMap<>();
        for (ApiParamMappingResp mapping : nullSafe(snapshot != null ? snapshot.apiParamMappings() : null)) {
            apiParameters.put(mapping.stdKey(), new ProvenanceSnapshot.ApiParameter(
                    mapping.stdKey(),
                    mapping.providerParamName(),
                    mapping.transformCode(),
                    mapping.notesJson()
            ));
        }

        List<ProvenanceSnapshot.RenderRule> renderRules = new ArrayList<>();
        for (ExprRenderRuleResp rule : nullSafe(snapshot != null ? snapshot.renderRules() : null)) {
            renderRules.add(toRenderRule(rule));
        }

        return new ProvenanceSnapshot(
                new ProvenanceSnapshot.Identity(provenance.id(), provenance.code(), provenance.name()),
                new ProvenanceSnapshot.Scope(RegistryConfigScope.SOURCE.code(), taskType),
                new ProvenanceSnapshot.Operation(operationCode, provenance.timezoneDefault()),
                0L,
                Instant.now(),
                fields,
                capabilities,
                apiParameters,
                renderRules
        );
    }

    private ProvenanceSnapshot.Capability toCapability(ExprCapabilityResp resp) {
        Set<String> ops = toSet(resp.opsJson());
        Set<String> negOps = toSet(resp.negatableOpsJson());
        Set<String> termMatches = toSet(resp.termMatchesJson());
        Set<String> tokenKinds = toSet(resp.tokenKindsJson());
        return new ProvenanceSnapshot.Capability(
                ops,
                negOps,
                resp.supportsNot(),
                termMatches,
                resp.termCaseSensitiveAllowed(),
                resp.termAllowBlank(),
                resp.termMinLength(),
                resp.termMaxLength(),
                resp.termPattern(),
                resp.inMaxSize(),
                resp.inCaseSensitiveAllowed(),
                parseRangeKind(resp.rangeKindCode()),
                resp.rangeAllowOpenStart(),
                resp.rangeAllowOpenEnd(),
                resp.rangeAllowClosedAtInfinity(),
                resp.dateMin(),
                resp.dateMax(),
                resp.datetimeMin(),
                resp.datetimeMax(),
                resp.numberMin() == null ? null : resp.numberMin().toPlainString(),
                resp.numberMax() == null ? null : resp.numberMax().toPlainString(),
                resp.existsSupported(),
                tokenKinds,
                resp.tokenValuePattern()
        );
    }

    private ProvenanceSnapshot.RenderRule toRenderRule(ExprRenderRuleResp resp) {
        Map<String, String> params = parseParams(resp.paramsJson());
        return new ProvenanceSnapshot.RenderRule(
                resp.fieldKey(),
                resp.scopeCode(),
                resp.taskType(),
                Atom.Operator.valueOf(resp.opCode().toUpperCase(Locale.ROOT)),
                resp.matchTypeCode(),
                toNegationQualifier(resp.negated()),
                toValueType(resp.valueTypeCode()),
                ProvenanceSnapshot.EmitType.valueOf(resp.emitTypeCode().toUpperCase(Locale.ROOT)),
                resp.template(),
                resp.itemTemplate(),
                resp.joiner(),
                resp.wrapGroup(),
                params,
                resp.functionCode(),
                resp.effectiveFrom(),
                resp.effectiveTo(),
                0
        );
    }

    private Map<String, String> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = objectMapper.readValue(paramsJson, Map.class);
            return Map.copyOf(map);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse render rule params", e);
        }
    }

    private Set<String> toSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> list = objectMapper.readValue(json, List.class);
            return new HashSet<>(list);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse JSON array: " + json, e);
        }
    }

    private ProvenanceSnapshot.RangeKind parseRangeKind(String code) {
        if (code == null || code.isBlank()) {
            return ProvenanceSnapshot.RangeKind.NONE;
        }
        return ProvenanceSnapshot.RangeKind.valueOf(code.toUpperCase(Locale.ROOT));
    }

    private ProvenanceSnapshot.NegationQualifier toNegationQualifier(Boolean value) {
        if (value == null) {
            return ProvenanceSnapshot.NegationQualifier.ANY;
        }
        return value ? ProvenanceSnapshot.NegationQualifier.TRUE : ProvenanceSnapshot.NegationQualifier.FALSE;
    }

    private ProvenanceSnapshot.ValueType toValueType(String code) {
        if (code == null || code.isBlank()) {
            return ProvenanceSnapshot.ValueType.ANY;
        }
        return ProvenanceSnapshot.ValueType.valueOf(code.toUpperCase(Locale.ROOT));
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
