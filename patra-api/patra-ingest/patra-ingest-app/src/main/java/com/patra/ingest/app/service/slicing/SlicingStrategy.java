package com.patra.ingest.app.service.slicing;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.usecase.command.JobStartPlanCommand;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.starter.expr.compiler.ExprCompiler;
import com.patra.expr.Expr;

import java.time.ZoneId;
import java.util.List;

/**
 * 抽象切片策略，用于根据数据源与采集类型，将“表达式原型”切分为一个或多个切片，并为每个切片产出局部化表达式与稳定化切片规范 JSON。
 *
 * <p>应用层仅做编排，策略不做持久化；返回的草稿由用例持久化为 PlanSlice 和后续 Task。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface SlicingStrategy {

    /**
     * 当前策略是否支持指定的数据源与采集类型。
     */
    boolean supports(ProvenanceCode provenance, IngestOperationType operation);

    /**
     * 基于基础表达式与上下文进行切片，返回切片草稿列表（包含序号、切片规范 JSON、局部化表达式快照 JSON）。
     *
     * @param baseExpr   未局部化的表达式原型（通常仅含整体时间窗）
     * @param provenance 数据源
     * @param operation  采集类型
     * @param compiler   表达式编译/切片器
     * @param cfg        数据源配置快照（供推断粒度/字段/对齐等）
     * @param command    启动命令（可携带覆盖项）
     * @param planId     计划ID（仅用于草稿内观测/记录，不做持久化）
     * @param zone       数据源时区
     * @return 切片草稿列表（有序）
     */
    List<SliceDraft> slice(Expr baseExpr,
                           ProvenanceCode provenance,
                           IngestOperationType operation,
                           ExprCompiler compiler,
                           ProvenanceConfigSnapshot cfg,
                           JobStartPlanCommand command,
                           Long planId,
                           ZoneId zone);

    /**
     * 切片草稿：仅承载稳定化 JSON 字符串，供上层计算哈希与落库。
     */
    record SliceDraft(int sliceNo, String sliceSpecJson, String exprSnapshotJson) {}
}
