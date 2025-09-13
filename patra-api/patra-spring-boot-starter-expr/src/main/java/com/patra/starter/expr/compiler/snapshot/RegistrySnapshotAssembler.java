package com.patra.starter.expr.compiler.snapshot;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.patra.registry.api.rpc.dto.*;
import com.patra.starter.core.json.JacksonProvider;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 registry 的快照响应映射为 starter 内部的 ProvenanceSnapshot。
 * 仅做字段搬运与必要的小写化/空值兜底，不做业务逻辑。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegistrySnapshotAssembler {

    public static ProvenanceSnapshot toSnapshot(ProvenanceExprConfigSnapshotApiResp src) {
        // 基本元信息
        ProvenanceSnapshot.ProvenanceKey key = new ProvenanceSnapshot.ProvenanceKey(
                src.provenanceId(), src.provenanceCode()
        );
        String operation = src.operation();
        long version = ObjectUtil.defaultIfNull(src.version(), 0L);
        Instant updatedAt = src.updatedAt();

        // 1) FieldDict
        Map<String, ProvenanceSnapshot.FieldDictEntry> fieldDict =
                safeMap(src.fieldDict()).entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> mapFieldDict(e.getValue()),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));

        // 2) Capability（字段 -> 能力规则）
        Map<String, ProvenanceSnapshot.CapabilityRule> capability =
                safeMap(src.capabilities()).entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> mapCapability(e.getValue()),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));

        // 3) RenderRules（按 priority 降序）
        List<ProvenanceSnapshot.RenderRuleTemplate> renderRules =
                safeList(src.renderRules()).stream()
                        .map(RegistrySnapshotAssembler::mapRenderRule)
                        .sorted(Comparator.comparingInt(ProvenanceSnapshot.RenderRuleTemplate::priority).reversed())
                        .toList();

        // 4) ApiParam（仅本 operation）
        Map<String, ProvenanceSnapshot.ApiParamMappingEntry> apiParams =
                safeMap(src.apiParams()).entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> mapApiParam(e.getValue()),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));

        return ProvenanceSnapshot.builder()
                .key(key)
                .operation(operation)
                .version(version)
                .updatedAt(updatedAt)
                .fieldDict(fieldDict)
                .capability(capability)
                .renderRules(renderRules)
                .apiParam(apiParams)
                .build();
    }

    // ----------------- 映射细节 -----------------

    private static ProvenanceSnapshot.FieldDictEntry mapFieldDict(PlatformFieldDictApiResp f) {
        var dataType = ProvenanceSnapshot.FieldDictEntry.DataType.valueOf(f.dataType());
        var cardinality = ProvenanceSnapshot.FieldDictEntry.Cardinality.valueOf(f.cardinality());
        return new ProvenanceSnapshot.FieldDictEntry(
                f.fieldKey(),
                dataType,
                cardinality,
                ObjectUtil.defaultIfNull(f.isDate(), false),
                f.datetype() == null ? null : f.datetype()
        );
    }

    private static ProvenanceSnapshot.CapabilityRule mapCapability(QueryCapabilityApiResp c) {
        return new ProvenanceSnapshot.CapabilityRule(
                safeList(c.ops()),
                c.negatableOps(),                                        // 允许为 null
                ObjectUtil.defaultIfNull(c.supportsNot(), true),
                c.termMatches(),
                ObjectUtil.defaultIfNull(c.termCaseSensitiveAllowed(), false),
                ObjectUtil.defaultIfNull(c.termAllowBlank(), false),
                ObjectUtil.defaultIfNull(c.termMinLen(), 0),
                ObjectUtil.defaultIfNull(c.termMaxLen(), 0),
                c.termPattern(),
                ObjectUtil.defaultIfNull(c.inMaxSize(), 0),
                ObjectUtil.defaultIfNull(c.inCaseSensitiveAllowed(), false),
                ProvenanceSnapshot.CapabilityRule.RangeKind.valueOf(c.rangeKind()),
                ObjectUtil.defaultIfNull(c.rangeAllowOpenStart(), true),
                ObjectUtil.defaultIfNull(c.rangeAllowOpenEnd(), true),
                ObjectUtil.defaultIfNull(c.rangeAllowClosedAtInfty(), false),
                c.dateMin(),                                             // yyyy-MM-dd
                c.dateMax(),
                c.datetimeMin(),                                         // Instant
                c.datetimeMax(),
                // decimal -> String 承载，避免精度丢失
                c.numberMin() == null ? null : c.numberMin().toPlainString(),
                c.numberMax() == null ? null : c.numberMax().toPlainString(),
                ObjectUtil.defaultIfNull(c.existsSupported(), false),
                c.tokenKinds(),
                c.tokenValuePattern()
        );
    }

    private static ProvenanceSnapshot.RenderRuleTemplate mapRenderRule(QueryRenderRuleApiResp r) {
        var op = ProvenanceSnapshot.RenderRuleTemplate.Op.valueOf(r.op());
        var emit = r.emit() == null ? ProvenanceSnapshot.RenderRuleTemplate.Emit.query
                : ProvenanceSnapshot.RenderRuleTemplate.Emit.valueOf(r.emit());
        String matchType = r.matchType() == null ? null : r.matchType().toLowerCase();
        String valueType = r.valueType() == null ? null : r.valueType().toLowerCase();

        Map<String, String> params = Map.of();
        if (cn.hutool.core.util.StrUtil.isNotBlank(r.params())) {
            try {
                params = JacksonProvider.getObjectMapper().readValue(r.params(), new TypeReference<>() {});
            } catch (Exception ex) {
                // 这里不要直接抛，防止影响整体快照组装，可以打日志或降级为空 map
                params = Map.of();
            }
        }

        return new ProvenanceSnapshot.RenderRuleTemplate(
                r.fieldKey(),
                op,
                matchType,
                r.negated(),
                valueType,
                emit,
                ObjectUtil.defaultIfNull(r.priority(), 0),
                r.template(),
                r.itemTemplate(),
                r.joiner(),
                ObjectUtil.defaultIfNull(r.wrapGroup(), false),
                params,
                r.fn()
        );
    }

    private static ProvenanceSnapshot.ApiParamMappingEntry mapApiParam(ApiParamMappingApiResp p) {
        return new ProvenanceSnapshot.ApiParamMappingEntry(
                p.stdKey(),
                p.providerParam(),
                p.transform()
        );
    }

    // ----------------- Hutool 安全包装 -----------------

    private static <K, V> Map<K, V> safeMap(Map<K, V> m) {
        return MapUtil.isEmpty(m) ? Map.of() : m;
    }

    private static <T> List<T> safeList(List<T> l) {
        return CollUtil.isEmpty(l) ? List.of() : l;
    }
}
