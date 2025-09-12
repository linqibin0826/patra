package com.patra.starter.expr.compiler.infra;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Registry 聚合根的“快照 DTO”（出参），用于 starter 通过 Feign 获取。
 * 注意：不要把 Registry 的领域对象直接暴露给 starter，解耦演进。
 */
public record RegistryProvenanceSnapshotDTO(
        Long id,
        String code,            // ProvenanceCode 的字面（如 "pubmed"）
        String name,
        String operation,       // search/fetch/lookup…
        // 四大块：
        Map<String, FieldDictItem> fieldDict,
        Map<String, CapabilityItem> capabilityMatrix,
        Map<String, List<RenderRuleItem>> renderRulesByField,
        Map<String, Map<String, ApiParamMappingItem>> apiParamMappingsByOperation,
        // 元信息：
        String versionTag,
        Instant snapshotAtUTC,
        List<String> recordRemarks
) {
    public record FieldDictItem(
            String key,
            String dataType,
            String cardinality,
            boolean isDate,
            String datetype
    ) {}
    public record CapabilityItem(
            List<String> ops,
            List<String> negatableOps,
            boolean supportsNot,
            List<String> termMatches,
            boolean termCaseSensitiveAllowed,
            boolean termAllowBlank,
            int termMinLen,
            int termMaxLen,
            String termPattern,
            int inMaxSize,
            boolean inCaseSensitiveAllowed,
            String rangeKind,
            boolean rangeAllowOpenStart,
            boolean rangeAllowOpenEnd,
            boolean rangeAllowClosedAtInfty,
            String dateMin,
            String dateMax,
            String datetimeMin,
            String datetimeMax,
            String numberMin,
            String numberMax,
            boolean existsSupported,
            List<String> tokenKinds,
            String tokenValuePattern
    ) {}
    public record RenderRuleItem(
            String fieldKey,
            String op,
            String matchType,
            Boolean negated,
            String valueType,
            String emit,
            int priority,
            String template,
            String itemTemplate,
            String joiner,
            boolean wrapGroup,
            Map<String, String> params,
            String fn
    ) {}
    public record ApiParamMappingItem(
            String stdKey,
            String providerParam,
            String transform
    ) {}
}
