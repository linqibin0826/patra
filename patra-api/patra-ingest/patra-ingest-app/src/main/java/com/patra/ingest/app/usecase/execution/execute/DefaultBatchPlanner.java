package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认批次规划器实现。
 * <p>
 * 职责：为不支持多批次的数据源提供单批次规划（全量查询）。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>单批次：仅生成一个批次，包含完整的查询和参数。</li>
 *   <li>无游标：不使用游标令牌（cursorToken = null）。</li>
 *   <li>通用性：作为其他数据源的基础模板，可被继承扩展。</li>
 * </ul>
 * </p>
 * <p>
 * 适用场景：
 * <ul>
 *   <li>数据量较小，可一次性拉取的数据源。</li>
 *   <li>不支持分页的 API。</li>
 *   <li>作为新数据源接入的默认实现。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class DefaultBatchPlanner implements BatchPlanner {

    /**
     * 默认支持的数据源编码（通用/兜底）。
     */
    private static final String PROVENANCE_CODE = "DEFAULT";

    @Override
    public String getProvenanceCode() {
        return PROVENANCE_CODE;
    }

    /**
     * 规划批次（默认实现：单批次）。
     *
     * @param context 执行上下文
     * @param maxBatches 最大批次数限制（对单批次无意义）
     * @return 批次规划结果（固定返回 1 个批次）
     */
    @Override
    public BatchPlan plan(ExecutionContext context, int maxBatches) {
        log.debug("[INGEST][APP] default batch planner: single batch taskId={} provenanceCode={}",
                  context.taskId(), context.provenanceCode());

        // 创建单个批次，包含完整的查询和参数
        Batch batch = Batch.first(context.compiledQuery(), context.compiledParams());

        return BatchPlan.single(batch);
    }
}
