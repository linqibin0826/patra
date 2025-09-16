package com.patra.ingest.adapter.in.job;

import java.util.List;
/**
 * 承接 XXL-Job 的入参 JSON 的“形状”（输入 DTO）。
 * 这些字段名建议与 JSON key 一致，便于直接反序列化。
 * 这里全部用 String/数值/简单结构，避免直接暴露领域对象。
 */
public record SourceIngestJobParameters(
        Boolean dryRun,             // 可选；缺省走默认 false
        CursorJson cursor,          // 游标相关入参
        ScopeJson scope,            // 业务范围（可选）
        String priority,            // LOW|NORMAL|HIGH（可选）
        SafetyJson safety,          // 安全阈值（可选）
        OverridesJson overrides     // 字段名覆盖（可选）
) {
    public record CursorJson(
            String type,           // TIME|ID|PAGE|HYBRID
            String field,          // 比如 PDAT；可为空->走配置
            String direction,      // ASC|DESC
            String lastSeenId,     // 可选
            String since,          // ISO-8601，如 "2015-01-01T00:00:00Z"；可选
            String until,          // ISO-8601；可选
            Integer timeWindowDays,// 可选，例如 30
            Long idWindow          // 可选
    ) {}

    public record ScopeJson(
            List<String> journalIssns,
            List<String> affiliations,
            List<String> subjectAreas
    ) {}

    public record SafetyJson(
            Integer maxPages,
            Integer maxRecords,
            Long maxRuntimeSeconds
    ) {}

    public record OverridesJson(
            String timeFieldName,
            String idFieldName,
            String pageParamName
    ) {}
}
