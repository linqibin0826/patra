package com.patra.ingest.app.usecase.execution.execute;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 批次规划器接口。
 * <p>
 * 职责：根据执行上下文规划批次，支持按数据源策略定制。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>策略模式：不同数据源（provenanceCode）可有不同的规划策略。</li>
 *   <li>批次限制：规划时检查批次数是否超过上限，超限则抛异常或标记 exceedsLimit。</li>
 *   <li>游标支持：支持基于游标的分页规划（如 token-based pagination）。</li>
 *   <li>窗口感知：根据 WindowSpec 策略调整查询范围（TIME/ID_RANGE/CURSOR_LANDMARK等）。</li>
 * </ul>
 * </p>
 * <p>
 * 实现类应注册到 BatchPlannerRegistry，按 provenanceCode 路由。
 * </p>
 *
 * TODO 新增一个pubmed的实现
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface BatchPlanner {

    /**
     * 获取支持的数据源编码。
     *
     * @return 数据源编码枚举
     */
    ProvenanceCode getProvenanceCode();

    /**
     * 规划批次。
     *
     * @param context 执行上下文（包含 query/params/window/configSnapshot）
     * @param maxBatches 最大批次数限制
     * @return 批次规划结果
     */
    BatchPlan plan(ExecutionContext context, int maxBatches);
}
