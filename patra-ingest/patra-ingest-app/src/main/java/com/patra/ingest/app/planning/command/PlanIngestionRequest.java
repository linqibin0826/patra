package com.patra.ingest.app.planning.command;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 计划编排入口请求（Adapter → Application 的指令模型）。
 * <p>
 * 由调度作业或外部调用方组装，经 Adapter 层（参数解析 / 默认值填充）后传递给应用层，用于：
 * <ol>
 *   <li>解析计划窗口（若 windowFrom / windowTo 缺省，将由窗口解析器推导）</li>
 *   <li>构建触发规范 {@code PlanTriggerNorm}（包含模式、步长、优先级等）</li>
 *   <li>驱动计划装配（表达式、切片策略、任务生成）</li>
 * </ol>
 * </p>
 * <h4>字段语义 & 约束</h4>
 * <ul>
 *   <li><b>provenanceCode / endpoint / operationCode</b>：定义业务来源 + 端点 + 操作三元组，组成后续幂等与分区计算的重要组成；其中 provenanceCode 与 operationCode 为必填（不可为 null）。</li>
 *   <li><b>step</b>：切片步长，采用 ISO-8601 Duration 字符串（如 {@code PT1H}、{@code P1D}）；允许为空（由策略决定是否需要），若提供需满足 {@code java.time.Duration.parse(step)} 可解析。</li>
 *   <li><b>windowFrom/windowTo</b>：计划时间窗口的上下界（半开区间假设：含 from 不含 to，若 to = null 代表“无上界”）；可同时为空，表示交由窗口解析器基于业务策略推导；若仅一端为空，以业务模式（如 HARVEST / BACKFILL / UPDATE）补全。</li>
 *   <li><b>priority</b>：调度优先级；允许为空，默认回退到 {@link com.patra.common.enums.Priority#NORMAL}。</li>
 *   <li><b>triggeredAt</b>：触发发生时间，一般由调度器传入；为空时应用层会补写当前时间（由上游保证正确性优先）。</li>
 *   <li><b>scheduler/schedulerJobId/schedulerLogId</b>：调度器维度上下文，便于追踪和回放；日志 ID 可能为空。</li>
 *   <li><b>triggerParams</b>：透传的可选附加参数（如强制窗口、特殊过滤标志）；值需为 Jackson 可序列化类型；空 map 与 null 语义一致。</li>
 * </ul>
 * <h4>不变式 (Invariants)</h4>
 * <ul>
 *   <li>{@code provenanceCode != null}</li>
 *   <li>{@code operationCode != null}</li>
 *   <li>{@code triggerType != null}</li>
 *   <li>{@code scheduler != null}</li>
 *   <li>priority 总是非 null（构造时回退）</li>
 * </ul>
 * <h4>边界与错误处理</h4>
 * <ul>
 *   <li>不在此处做 {@code step} 语法校验（延迟到使用处 / 策略）</li>
 *   <li>窗口上下界未做先后顺序校验（由窗口解析器或后续验证器负责）</li>
 * </ul>
 * <h4>线程安全</h4>
 * <p>record 不可变（内部未暴露可变集合引用），可在多线程间安全共享。</p>
 *
 * @param provenanceCode 来源编码（必填）
 * @param endpoint 采集端点（可选，某些操作可能不需要具体 endpoint）
 * @param operationCode 操作类型（必填）
 * @param step 切片步长（ISO-8601 持续时间，允许为空）
 * @param triggerType 触发类型（必填）
 * @param scheduler 调度器类型（必填）
 * @param schedulerJobId 调度任务 ID（可为空）
 * @param schedulerLogId 调度日志 ID（可为空）
 * @param windowFrom 窗口开始（可为空）
 * @param windowTo 窗口结束（可为空）
 * @param priority 调度优先级（为空时回退 NORMAL）
 * @param triggeredAt 触发时间（可为空）
 * @param triggerParams 额外触发参数（可为空）
 */
public record PlanIngestionRequest(
        ProvenanceCode provenanceCode,
        Endpoint endpoint,
        OperationCode operationCode,
        String step,
        TriggerType triggerType,
        Scheduler scheduler,
        String schedulerJobId,
        String schedulerLogId,
        Instant windowFrom,
        Instant windowTo,
        Priority priority,
        Instant triggeredAt,
        Map<String, Object> triggerParams
) {
    public PlanIngestionRequest {
        Objects.requireNonNull(provenanceCode, "provenanceCode must not be null");
        Objects.requireNonNull(operationCode, "operationCode must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        priority = priority == null ? Priority.NORMAL : priority;
    }
}
