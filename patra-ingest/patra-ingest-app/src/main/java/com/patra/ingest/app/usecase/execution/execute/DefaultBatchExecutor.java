package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchResult;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认批次执行器实现。
 * <p>
 * 职责：为不支持实际数据抓取的数据源提供模拟执行（用于测试/演示）。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>模拟执行：不实际调用 API，返回模拟的成功结果。</li>
 *   <li>无存储上传：storageKey 返回 null。</li>
 *   <li>通用性：作为其他数据源的基础模板，可被继承扩展。</li>
 * </ul>
 * </p>
 * <p>
 * 适用场景：
 * <ul>
 *   <li>新数据源接入前的骨架实现。</li>
 *   <li>集成测试中的模拟执行。</li>
 *   <li>不需要实际抓取数据的场景（如配置测试）。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class DefaultBatchExecutor implements BatchExecutor {

    /**
     * 默认支持的数据源编码（通用/兜底）。
     */
    private static final String PROVENANCE_CODE = "DEFAULT";

    @Override
    public String getProvenanceCode() {
        return PROVENANCE_CODE;
    }

    /**
     * 执行批次（默认实现：模拟成功）。
     *
     * @param context 执行上下文
     * @param batch 批次信息
     * @return 批次执行结果（模拟成功，返回 fetchedCount = 0）
     */
    @Override
    public BatchResult execute(ExecutionContext context, Batch batch) {
        log.info("[INGEST][APP] default batch executor: simulate success taskId={} runId={} batchNo={}",
                 context.taskId(), context.runId(), batch.batchNo());

        // 模拟执行成功，返回空数据
        return BatchResult.success(
            batch.batchNo(),
            0,          // fetchedCount = 0（模拟无数据）
            null,       // nextCursorToken = null（无后续批次）
            null        // storageKey = null（未上传存储）
        );
    }
}
