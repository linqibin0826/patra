package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.model.plan.PubmedPlanMetadata;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PubMed 批次生成策略
 *
 * <p>根据 PubmedPlanMetadata 生成批次列表，支持使用 WebEnv 会话令牌优化批次请求。
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {

    @Override
    public Class<? extends PlanMetadata> getSupportedType() {
        return PubmedPlanMetadata.class;
    }

    @Override
    public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
        if (!(plan instanceof PubmedPlanMetadata pubmedPlan)) {
            throw new IllegalArgumentException(
                "期望 PubmedPlanMetadata，实际类型: " + plan.getClass().getName()
            );
        }

        List<Batch> batches = new ArrayList<>();
        int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
        int totalCount = pubmedPlan.totalCount();

        if (totalCount <= 0) {
            log.info("PubMed 查询结果为空（totalCount=0），返回空批次列表");
            return batches;
        }

        int pageCount = (int) Math.ceil((double) totalCount / batchSize);
        String query = ctx.compiledQuery();

        log.debug("生成 PubMed 批次: totalCount={}, batchSize={}, pageCount={}, hasSessionToken={}",
                totalCount, batchSize, pageCount, pubmedPlan.hasSessionToken());

        // 检查是否有 History Server session token
        if (pubmedPlan.hasSessionToken()) {
            // 使用 History Server 模式
            for (int i = 0; i < pageCount; i++) {
                int pageNo = i + 1;
                int startOffset = i * batchSize;

                batches.add(Batch.withPageAndSession(
                        pageNo,
                        query,
                        ctx.compiledParams(),
                        startOffset,
                        batchSize,
                        pubmedPlan.webEnv(),
                        pubmedPlan.queryKey()
                ));
            }

            log.info("已生成 {} 个 PubMed 批次（使用 History Server: webEnv={}）",
                    batches.size(), pubmedPlan.webEnv());
        } else {
            // 常规模式（无 session token）
            for (int i = 0; i < pageCount; i++) {
                int pageNo = i + 1;
                int startOffset = i * batchSize;

                batches.add(Batch.withPage(
                        pageNo,
                        query,
                        ctx.compiledParams(),
                        startOffset,
                        batchSize
                ));
            }

            log.info("已生成 {} 个 PubMed 批次（常规模式，无 History Server）", batches.size());
        }

        return batches;
    }
}
