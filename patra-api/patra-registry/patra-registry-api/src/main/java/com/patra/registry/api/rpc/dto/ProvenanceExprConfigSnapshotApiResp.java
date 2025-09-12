package com.patra.registry.api.rpc.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 文献数据源表达式配置快照（聚合返回）。
 *
 * <p>一次性返回指定数据源 + operation 的规则快照，便于客户端减少多次调用。
 *
 * <p>version 来自规则源；若无则为 0。updatedAt 可为空。
 *
 * <p>为贴近编译期快照结构：
 * - fieldDict/capabilities/apiParams 使用 Map 形式，key 分别为 fieldKey/stdKey。
 * - renderRules 仍为列表（已按 priority 排序）。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceExprConfigSnapshotApiResp(
        Long provenanceId,
        String provenanceCode,
        String operation,
        long version,
        Instant updatedAt,
        Map<String, PlatformFieldDictApiResp> fieldDict,
        Map<String, QueryCapabilityApiResp> capabilities,
        List<QueryRenderRuleApiResp> renderRules,
        Map<String, ApiParamMappingApiResp> apiParams
) {}
