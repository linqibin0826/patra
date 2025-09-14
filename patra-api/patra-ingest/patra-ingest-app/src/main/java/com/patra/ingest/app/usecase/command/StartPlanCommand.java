package com.patra.ingest.app.usecase.command;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 启动一次采集计划的命令。Operation/Provenance 从 IngestRuntimeContext.provenanceConfig 中取。
 */
@Value
@Builder
public class StartPlanCommand {
    // —— 表达式原型（JSON 串：AST 的序列化），允许为空，app 侧可默认 constTrue() ——
    String exprProtoJson;

    // —— 本次调度窗口（左开右闭，UTC） ——
    Instant windowFromExclusive;
    Instant windowToInclusive;

    // —— 边界/切片策略（MVP 固定即可） ——
    BoundStyle boundStyle;
    SliceStrategy sliceStrategy;

    @lombok.Getter
    public enum BoundStyle {OPEN_LEFT_CLOSED_RIGHT}

    @lombok.Getter
    public enum SliceStrategy {TIME}

    // —— 调度元信息（审计） ——
    String triggerSource;     // 固定 "xxl" 即可
    String schedulerJobId;
}
