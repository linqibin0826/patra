package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.common.model.plan.EpmcPlanMetadata;
import com.patra.common.model.plan.PlanMetadata;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EPMC 批次生成策略
 *
 * <p>根据 EpmcPlanMetadata 生成批次列表，支持使用 cursorMark 游标令牌进行游标分页。
 *
 * <p>EPMC 使用 Solr 风格的 cursorMark 分页机制：
 * <ul>
 *   <li>首次请求：cursorMark="*"
 *   <li>后续请求：使用上次返回的 nextCursorMark
 *   <li>分页结束：nextCursorMark 等于当前 cursorMark
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class EpmcBatchGenerationStrategy implements BatchGenerationStrategy {

    @Override
    public Class<? extends PlanMetadata> getSupportedType() {
        return EpmcPlanMetadata.class;
    }

    @Override
    public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
        if (!(plan instanceof EpmcPlanMetadata epmcPlan)) {
            throw new IllegalArgumentException(
                "期望 EpmcPlanMetadata，实际类型: " + plan.getClass().getName()
            );
        }

        List<Batch> batches = new ArrayList<>();
        int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
        int totalCount = epmcPlan.totalCount();

        if (totalCount <= 0) {
            log.info("EPMC 查询结果为空（totalCount=0），返回空批次列表");
            return batches;
        }

        int pageCount = (int) Math.ceil((double) totalCount / batchSize);
        String query = ctx.compiledQuery();

        log.debug("生成 EPMC 批次: totalCount={}, batchSize={}, pageCount={}, hasCursorMark={}",
                totalCount, batchSize, pageCount, epmcPlan.hasSessionToken());

        // 检查是否有 cursorMark
        if (epmcPlan.hasSessionToken()) {
            // 使用游标模式：将 cursorMark 存储在 sessionTokens 中
            Map<String, String> sessionTokens = Map.of("cursorMark", epmcPlan.cursorMark());

            for (int i = 0; i < pageCount; i++) {
                int batchNo = i + 1;

                batches.add(new Batch(
                        batchNo,
                        query,
                        ctx.compiledParams(),
                        null,           // cursorToken 通过 sessionTokens 传递
                        null,           // pageNo (EPMC 不使用页码分页)
                        batchSize,
                        sessionTokens
                ));
            }

            log.info("已生成 {} 个 EPMC 批次（使用游标模式: cursorMark={}）",
                    batches.size(), epmcPlan.cursorMark());
        } else {
            // 常规模式（无游标）
            for (int i = 0; i < pageCount; i++) {
                int batchNo = i + 1;

                batches.add(new Batch(
                        batchNo,
                        query,
                        ctx.compiledParams(),
                        null,      // 无游标令牌
                        null,      // 无页码
                        batchSize,
                        null       // 无会话令牌
                ));
            }

            log.info("已生成 {} 个 EPMC 批次（常规模式，无游标）", batches.size());
        }

        return batches;
    }
}
