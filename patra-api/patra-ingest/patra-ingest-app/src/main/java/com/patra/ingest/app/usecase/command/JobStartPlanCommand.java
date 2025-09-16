package com.patra.ingest.app.usecase.command;

import java.util.Optional;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.IngestOperationType;

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
    public static JobStartPlanCommand of(
            String requestedBy,
            ProvenanceCode provenanceCode,
            IngestOperationType ingestOperationType
    ) {
        return new JobStartPlanCommand(
                requestedBy,
                provenanceCode,
                ingestOperationType,
                Optional.empty(),
                CursorSpec.empty(),
                false,
                Priority.NORMAL,
                SafetyLimits.empty(),
                IngestOptOverrides.empty()
        );
    }
}
