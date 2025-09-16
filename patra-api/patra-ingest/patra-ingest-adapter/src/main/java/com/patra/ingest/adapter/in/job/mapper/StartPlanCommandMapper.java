package com.patra.ingest.adapter.in.job.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.SortDirection;
import com.patra.ingest.adapter.in.job.SourceIngestJobParameters;
import com.patra.ingest.app.usecase.command.*;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.IngestOperationType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StartPlanCommandMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private StartPlanCommandMapper() {}

    /** 入口：从 JSON 字符串 + 代码侧上下文，生成 JobStartPlanCommand。 */
    public static JobStartPlanCommand fromIngestJobJson(
            String xxlJobParamJson,
            String requestedBy,
            ProvenanceCode provenanceCode,
            IngestOperationType opType
    ) {
        final SourceIngestJobParameters raw = readJson(xxlJobParamJson);

        final List<String> errors = new ArrayList<>();

        // —— cursorSpec
        final CursorSpec cursorSpec = toCursorSpec(raw.cursor(), errors);

        // BACKFILL 规则：至少有 since/until 之一
        if (opType == IngestOperationType.BACKFILL
                && cursorSpec.since().isEmpty()
                && cursorSpec.until().isEmpty()) {
            errors.add("BACKFILL 需要在 cursor 里提供 since 或 until（或二者之一）");
        }

        // —— scope
        final Optional<IngestScope> scope = Optional.ofNullable(raw.scope())
                .map(s -> new IngestScope(
                        optList(s.journalIssns()),
                        optList(s.affiliations()),
                        optList(s.subjectAreas())
                ));

        // —— dryRun
        final boolean dryRun = raw.dryRun() != null ? raw.dryRun() : false;

        // —— priority
        final Priority priority = raw.priority() == null
                ? Priority.NORMAL
                : parseEnum(raw.priority(), Priority.class, "priority", errors);

        // —— safety
        final SafetyLimits safetyLimits = toSafetyLimits(raw.safety(), errors);

        // —— overrides：优先使用 JSON 中的覆盖；
        final IngestOptOverrides overrides = mergeOverrides(raw.overrides());

        // 错误聚合抛出
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("XXL-Job 参数不合法：\n - " + String.join("\n - ", errors));
        }

        return new JobStartPlanCommand(
                requestedBy,
                provenanceCode,
                opType,
                scope,
                cursorSpec,
                dryRun,
                priority,
                safetyLimits,
                overrides
        );
    }

    // ========== 私有：反序列化 & 子对象映射 & 工具 ==========

    private static SourceIngestJobParameters readJson(String json) {
        try {
            return MAPPER.readValue(json, SourceIngestJobParameters.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("XXL-Job 参数 JSON 解析失败：" + e.getMessage(), e);
        }
    }

    private static CursorSpec toCursorSpec(SourceIngestJobParameters.CursorJson c, List<String> errors) {
        if (c == null) {
            errors.add("cursor 不能为空");
            // 返回一个哑对象，避免 NPE（最终会因 errors 抛错）
            return CursorSpec.timeAsc(Optional.empty(), Optional.empty(), Optional.empty());
        }

        final CursorType type = parseEnum(c.type(), CursorType.class, "cursor.type", errors);
        final SortDirection direction = c.direction() == null
                ? SortDirection.ASC
                : parseEnum(c.direction(), SortDirection.class, "cursor.direction", errors);

        final Optional<String> field = optString(c.field());
        final Optional<String> lastSeenId = optString(c.lastSeenId());
        final Optional<Instant> since = parseInstant(c.since(), "cursor.since", errors);
        final Optional<Instant> until = parseInstant(c.until(), "cursor.until", errors);

        final Optional<java.time.Duration> timeWindow = c.timeWindowDays() == null
                ? Optional.empty()
                : Optional.of(Duration.ofDays(c.timeWindowDays()));

        final Optional<Long> idWindow = Optional.ofNullable(c.idWindow());

        // 基于类型生成 CursorSpec
        return new CursorSpec(
                type,
                field,
                direction,
                lastSeenId,
                since,
                until,
                timeWindow,
                idWindow
        );
    }

    private static SafetyLimits toSafetyLimits(SourceIngestJobParameters.SafetyJson s, List<String> errors) {
        if (s == null) return SafetyLimits.empty();

        // 基本正数校验交给 SafetyLimits 自身构造器；这里只做空值规整
        final Optional<Integer> maxPages = Optional.ofNullable(s.maxPages());
        final Optional<Integer> maxRecords = Optional.ofNullable(s.maxRecords());
        final Optional<Duration> maxRuntime = s.maxRuntimeSeconds() == null
                ? Optional.empty()
                : Optional.of(Duration.ofSeconds(s.maxRuntimeSeconds()));

        try {
            return new SafetyLimits(maxPages, maxRecords, maxRuntime);
        } catch (IllegalArgumentException ex) {
            errors.add("safety 不合法：" + ex.getMessage());
            // 返回一个空对象占位，最终会因 errors 抛错
            return SafetyLimits.empty();
        }
    }

    private static IngestOptOverrides mergeOverrides(SourceIngestJobParameters.OverridesJson json) {
        final Optional<String> timeField = json != null ? optString(json.timeFieldName()) : Optional.empty();
        final Optional<String> idField = json != null ? optString(json.idFieldName()) : Optional.empty();
        final Optional<String> pageParam = json != null ? optString(json.pageParamName()) : Optional.empty();

        return new IngestOptOverrides(timeField, idField, pageParam);
    }

    // ===== 基础工具 =====

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> clazz, String name, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(name + " 不能为空");
            // 返回一个占位；最终会因 errors 抛错
            return clazz.getEnumConstants()[0];
        }
        try {
            return Enum.valueOf(clazz, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            errors.add(name + " 无效值：" + raw);
            return clazz.getEnumConstants()[0];
        }
    }

    private static Optional<String> optString(String s) {
        return (s == null || s.isBlank()) ? Optional.empty() : Optional.of(s);
    }

    private static Optional<Instant> parseInstant(String raw, String name, List<String> errors) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(Instant.parse(raw));
        } catch (Exception e) {
            errors.add(name + " 不是合法的 ISO-8601 Instant：" + raw);
            return Optional.empty();
        }
    }

    private static Optional<List<String>> optList(List<String> list) {
        return (list == null || list.isEmpty()) ? Optional.empty() : Optional.of(list);
    }
}
