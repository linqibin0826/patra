package com.patra.ingest.app.usecase.command;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.IngestOperationType;

import java.util.Objects;
import java.util.Optional;

/**
 * 启动一次采集计划的应用层命令（用例输入契约）。
 * 只表达业务“采什么、怎么采、以什么边界”，不暴露底层实现细节。
 */
public record JobStartPlanCommand(
        // 1) 身份与幂等（简化：只保留 requestedBy；幂等Key可在更外层生成）
        String requestedBy,

        // 2) 采什么
        ProvenanceCode provenanceCode,
        IngestOperationType ingestOperationType,
        Optional<IngestScope> scope,

        // 3) 怎么采
        CursorSpec cursorSpec,
        // 4) 运行开关
        boolean dryRun,
        Priority priority,
        SafetyLimits safety,
        IngestOptOverrides overrides
) {
    public JobStartPlanCommand {
        // 非空与 Optional 非 null 约束
        Objects.requireNonNull(provenanceCode, "provenanceCode cannot be null");
        Objects.requireNonNull(ingestOperationType, "ingestOperationType cannot be null");
        Objects.requireNonNull(cursorSpec, "cursorSpec cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        safety = safety == null ? SafetyLimits.empty() : safety;
        overrides = overrides == null ? IngestOptOverrides.empty() : overrides;

        // 互斥/依赖规则（示例，可按需扩展）
        if (ingestOperationType == IngestOperationType.BACKFILL
                && cursorSpec.since().isEmpty() && cursorSpec.until().isEmpty()) {
            throw new IllegalArgumentException("BACKFILL 需要提供 since 或 until（或二者之一）作为历史区间边界");
        }

        if (cursorSpec.type() == CursorType.TIME && cursorSpec.field().isPresent()
                && cursorSpec.field().get().isBlank()) {
            throw new IllegalArgumentException("当提供时间字段名时，不可为空字符串（若无请使用 Optional.empty()）");
        }
    }

    /**
     * 提供一个常用工厂：最小必填 + 默认 dryRun=false、priority=NORMAL、空的 safety/overrides
     */
    public static JobStartPlanCommand of(
            String requestedBy,
            ProvenanceCode provenanceCode,
            IngestOperationType ingestOperationType,
            CursorSpec cursorSpec
    ) {
        return new JobStartPlanCommand(
                requestedBy,
                provenanceCode,
                ingestOperationType,
                Optional.empty(),
                cursorSpec,
                false,
                Priority.NORMAL,
                SafetyLimits.empty(),
                IngestOptOverrides.empty()
        );
    }
}
