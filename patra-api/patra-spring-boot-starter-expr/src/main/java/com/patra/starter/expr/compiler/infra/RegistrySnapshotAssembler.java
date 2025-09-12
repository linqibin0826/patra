package com.patra.starter.expr.compiler.infra;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RegistrySnapshotAssembler {

    private RegistrySnapshotAssembler() {
    }

    public static ProvenanceSnapshot toSnapshot(RegistryProvenanceSnapshotDTO dto) {
        // 1) FieldDict
        var fieldDict = new ProvenanceSnapshot.FieldDict(
                dto.fieldDict().values().stream().map(fi ->
                        Map.entry(fi.key(), new ProvenanceSnapshot.FieldDict.Field(
                                fi.key(),
                                mapDataType(fi.dataType()),
                                mapCardinality(fi.cardinality()),
                                fi.isDate(),
                                emptyToNull(fi.datetype())
                        ))
                ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        // 2) CapabilityMatrix
        var caps = new ProvenanceSnapshot.CapabilityMatrix(
                dto.capabilityMatrix().entrySet().stream().map(e -> {
                    String field = e.getKey();
                    var c = e.getValue();
                    return Map.entry(field, new ProvenanceSnapshot.CapabilityMatrix.FieldCapability(
                            mapOps(c.ops()),
                            mapOps(c.negatableOps()),
                            c.supportsNot(),
                            mapTermMatches(c.termMatches()),
                            c.termCaseSensitiveAllowed(),
                            c.termAllowBlank(),
                            c.termMinLen(),
                            c.termMaxLen(),
                            emptyToNull(c.termPattern()),
                            c.inMaxSize(),
                            c.inCaseSensitiveAllowed(),
                            mapRangeKind(c.rangeKind()),
                            c.rangeAllowOpenStart(),
                            c.rangeAllowOpenEnd(),
                            c.rangeAllowClosedAtInfty(),
                            emptyToNull(c.dateMin()),
                            emptyToNull(c.dateMax()),
                            emptyToNull(c.datetimeMin()),
                            emptyToNull(c.datetimeMax()),
                            emptyToNull(c.numberMin()),
                            emptyToNull(c.numberMax()),
                            c.existsSupported(),
                            Optional.ofNullable(c.tokenKinds()).orElseGet(List::of),
                            emptyToNull(c.tokenValuePattern())
                    ));
                }).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        // 3) RenderRuleSet：同字段下按 priority 降序排好，便于选择器使用
        var rrByField = dto.renderRulesByField().entrySet().stream().map(e -> {
            String field = e.getKey();
            var rules = e.getValue().stream().map(r -> new ProvenanceSnapshot.RenderRuleSet.RenderRule(
                            r.fieldKey(),
                            mapRenderOp(r.op()),
                            mapMatchType(r.matchType()),
                            r.negated(),
                            mapValueType(r.valueType()),
                            mapEmit(r.emit()),
                            r.priority(),
                            r.template(),
                            emptyToNull(r.itemTemplate()),
                            emptyToNull(r.joiner()),
                            r.wrapGroup(),
                            Optional.ofNullable(r.params()).orElseGet(Map::of),
                            emptyToNull(r.fn())
                    )).sorted(Comparator.comparingInt(ProvenanceSnapshot.RenderRuleSet.RenderRule::priority).reversed())
                    .collect(Collectors.toUnmodifiableList());
            return Map.entry(field, rules);
        }).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        var renderRuleSet = new ProvenanceSnapshot.RenderRuleSet(rrByField);

        // 4) ApiParamMappings
        var apm = new ProvenanceSnapshot.ApiParamMappings(
                dto.apiParamMappingsByOperation().entrySet().stream().map(opEntry -> {
                    String op = opEntry.getKey();
                    Map<String, RegistryProvenanceSnapshotDTO.ApiParamMappingItem> m = opEntry.getValue();
                    Map<String, ProvenanceSnapshot.ApiParamMappings.Mapping> mapped =
                            m.values().stream().map(v ->
                                            Map.entry(v.stdKey(),
                                                    new ProvenanceSnapshot.ApiParamMappings.Mapping(
                                                            v.stdKey(),
                                                            v.providerParam(),
                                                            emptyToNull(v.transform())
                                                    )))
                                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                    return Map.entry(op, mapped);
                }).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        // 5) Meta
        var meta = new ProvenanceSnapshot.Meta(
                dto.versionTag(),
                Optional.ofNullable(dto.snapshotAtUTC()).orElseGet(Instant::now),
                Optional.ofNullable(dto.recordRemarks()).orElseGet(List::of)
        );

        return new ProvenanceSnapshot(
                dto.code(),
                dto.name(),
                dto.operation(),
                fieldDict,
                caps,
                renderRuleSet,
                apm,
                meta
        );
    }

    // --- mapping helpers ---

    private static ProvenanceSnapshot.FieldDict.DataType mapDataType(String s) {
        if (s == null) return ProvenanceSnapshot.FieldDict.DataType.text;
        return switch (s) {
            case "date" -> ProvenanceSnapshot.FieldDict.DataType.date;
            case "datetime" -> ProvenanceSnapshot.FieldDict.DataType.datetime;
            case "number" -> ProvenanceSnapshot.FieldDict.DataType.number;
            case "keyword" -> ProvenanceSnapshot.FieldDict.DataType.keyword;
            case "boolean" -> ProvenanceSnapshot.FieldDict.DataType.boolean_;
            case "token" -> ProvenanceSnapshot.FieldDict.DataType.token;
            default -> ProvenanceSnapshot.FieldDict.DataType.text;
        };
    }

    private static ProvenanceSnapshot.FieldDict.Cardinality mapCardinality(String s) {
        if (s == null || s.isBlank()) return ProvenanceSnapshot.FieldDict.Cardinality.single;
        return "multi".equalsIgnoreCase(s) ? ProvenanceSnapshot.FieldDict.Cardinality.multi
                : ProvenanceSnapshot.FieldDict.Cardinality.single;
    }

    private static List<ProvenanceSnapshot.CapabilityMatrix.Op> mapOps(List<String> ops) {
        if (ops == null) return List.of();
        return ops.stream().filter(Objects::nonNull).map(String::toUpperCase).map(x -> switch (x) {
            case "TERM" -> ProvenanceSnapshot.CapabilityMatrix.Op.TERM;
            case "IN" -> ProvenanceSnapshot.CapabilityMatrix.Op.IN;
            case "RANGE" -> ProvenanceSnapshot.CapabilityMatrix.Op.RANGE;
            case "EXISTS" -> ProvenanceSnapshot.CapabilityMatrix.Op.EXISTS;
            case "TOKEN" -> ProvenanceSnapshot.CapabilityMatrix.Op.TOKEN;
            default -> null;
        }).filter(Objects::nonNull).toList();
    }

    private static List<ProvenanceSnapshot.CapabilityMatrix.TermMatch> mapTermMatches(List<String> m) {
        if (m == null) return List.of();
        return m.stream().filter(Objects::nonNull).map(String::toUpperCase).map(x -> switch (x) {
            case "PHRASE" -> ProvenanceSnapshot.CapabilityMatrix.TermMatch.PHRASE;
            case "EXACT" -> ProvenanceSnapshot.CapabilityMatrix.TermMatch.EXACT;
            case "ANY" -> ProvenanceSnapshot.CapabilityMatrix.TermMatch.ANY;
            default -> null;
        }).filter(Objects::nonNull).toList();
    }

    private static ProvenanceSnapshot.CapabilityMatrix.RangeKind mapRangeKind(String s) {
        if (s == null) return ProvenanceSnapshot.CapabilityMatrix.RangeKind.NONE;
        return switch (s.toUpperCase()) {
            case "DATE" -> ProvenanceSnapshot.CapabilityMatrix.RangeKind.DATE;
            case "DATETIME" -> ProvenanceSnapshot.CapabilityMatrix.RangeKind.DATETIME;
            case "NUMBER" -> ProvenanceSnapshot.CapabilityMatrix.RangeKind.NUMBER;
            default -> ProvenanceSnapshot.CapabilityMatrix.RangeKind.NONE;
        };
    }

    private static ProvenanceSnapshot.RenderRuleSet.RenderRule.Op mapRenderOp(String s) {
        return switch (nullToEmpty(s)) {
            case "term" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.Op.term;
            case "in" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.Op.in;
            case "range" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.Op.range;
            case "exists" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.Op.exists;
            case "token" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.Op.token;
            default -> ProvenanceSnapshot.RenderRuleSet.RenderRule.Op.term;
        };
    }

    private static ProvenanceSnapshot.RenderRuleSet.RenderRule.MatchType mapMatchType(String s) {
        if (s == null) return null;
        return switch (s) {
            case "phrase" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.MatchType.phrase;
            case "exact" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.MatchType.exact;
            case "any" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.MatchType.any;
            default -> null;
        };
    }

    private static ProvenanceSnapshot.RenderRuleSet.RenderRule.ValueType mapValueType(String s) {
        if (s == null) return null;
        return switch (s) {
            case "string" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.ValueType.string;
            case "date" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.ValueType.date;
            case "datetime" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.ValueType.datetime;
            case "number" -> ProvenanceSnapshot.RenderRuleSet.RenderRule.ValueType.number;
            default -> null;
        };
    }

    private static ProvenanceSnapshot.RenderRuleSet.RenderRule.Emit mapEmit(String s) {
        return "params".equalsIgnoreCase(s)
                ? ProvenanceSnapshot.RenderRuleSet.RenderRule.Emit.params
                : ProvenanceSnapshot.RenderRuleSet.RenderRule.Emit.query;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
