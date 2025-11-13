package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.common.model.plan.DoajPlanMetadata;
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
 * DOAJ 批次生成策略
 *
 * <p>根据 DoajPlanMetadata 生成批次列表，支持使用 scrollId 进行 Elasticsearch Scroll API 分页。
 *
 * <p>DOAJ 使用 Elasticsearch Scroll API 分页机制：
 * <ul>
 *   <li>首次请求：不带 scrollId，创建 Scroll Context
 *   <li>后续请求：使用上次返回的 scrollId
 *   <li>分页结束：返回空结果集
 * </ul>
 *
 * <p><strong>关键设计</strong>：
 * <ul>
 *   <li>使用 {@link DoajPlanMetadata#pageSize()} 而不是 {@link ExecutionContext} 的 pageSize
 *   <li>DOAJ API 要求 pageSize 在规划阶段确定，不能使用全局配置
 *   <li>scrollId 通过 {@link Batch#sessionTokens()} 传递
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class DoajBatchGenerationStrategy implements BatchGenerationStrategy {

    @Override
    public Class<? extends PlanMetadata> getSupportedType() {
        return DoajPlanMetadata.class;
    }

    @Override
    public List<Batch> generateBatches(PlanMetadata plan, ExecutionContext ctx) {
        if (!(plan instanceof DoajPlanMetadata doajPlan)) {
            throw new IllegalArgumentException(
                "期望 DoajPlanMetadata，实际类型: " + plan.getClass().getName()
            );
        }

        List<Batch> batches = new ArrayList<>();
        // 关键：使用 DoajPlanMetadata 中的 pageSize，而不是 ExecutionContext 的 pageSize
        int batchSize = doajPlan.pageSize();
        int totalCount = doajPlan.totalCount();

        if (totalCount <= 0) {
            log.info("DOAJ 查询结果为空（totalCount=0），返回空批次列表");
            return batches;
        }

        int pageCount = (int) Math.ceil((double) totalCount / batchSize);
        String query = ctx.compiledQuery();

        log.debug("生成 DOAJ 批次: totalCount={}, batchSize={}, pageCount={}, hasScrollId={}",
                totalCount, batchSize, pageCount, doajPlan.hasSessionToken());

        // 检查是否有 scrollId
        if (doajPlan.hasSessionToken()) {
            // 使用 Scroll API 模式：将 scrollId 存储在 sessionTokens 中
            Map<String, String> sessionTokens = Map.of("scrollId", doajPlan.scrollId());

            for (int i = 0; i < pageCount; i++) {
                int batchNo = i + 1;

                batches.add(new Batch(
                        batchNo,
                        query,
                        ctx.compiledParams(),
                        null,           // scrollId 通过 sessionTokens 传递
                        null,           // pageNo (DOAJ 不使用页码分页)
                        batchSize,
                        sessionTokens
                ));
            }

            log.info("已生成 {} 个 DOAJ 批次（使用 Scroll API: scrollId={}）",
                    batches.size(), doajPlan.scrollId());
        } else {
            // 常规模式（无 scrollId）
            for (int i = 0; i < pageCount; i++) {
                int batchNo = i + 1;

                batches.add(new Batch(
                        batchNo,
                        query,
                        ctx.compiledParams(),
                        null,      // 无 scrollId
                        null,      // 无页码
                        batchSize,
                        null       // 无会话令牌
                ));
            }

            log.info("已生成 {} 个 DOAJ 批次（常规模式，无 Scroll API）", batches.size());
        }

        return batches;
    }
}
