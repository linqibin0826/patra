package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.BatchPlan;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EPMC 批次生成策略
 *
 * <p>根据 BatchPlan 生成批次列表，支持使用 cursorMark 游标令牌进行游标分页。
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
    public String getSupportedDataSourceCode() {
        return "epmc";
    }

    @Override
    public List<Batch> generateBatches(BatchPlan plan, ExecutionContext ctx) {
        List<Batch> batches = new ArrayList<>();
        int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
        int totalRecords = plan.totalRecords();

        if (totalRecords <= 0) {
            log.info("EPMC 查询结果为空（totalRecords=0），返回空批次列表");
            return batches;
        }

        int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
        String query = ctx.compiledQuery();

        log.debug("生成 EPMC 批次: totalRecords={}, batchSize={}, pageCount={}, hasStateToken={}",
                totalRecords, batchSize, pageCount, plan.hasStateToken());

        // 检查是否有 state token（包含 cursorMark）
        if (plan.hasStateToken()) {
            // 使用游标模式：state token 中包含 cursorMark
            Map<String, String> stateToken = plan.stateToken().orElseThrow();

            for (int i = 0; i < pageCount; i++) {
                int batchNo = i + 1;

                batches.add(new Batch(
                        batchNo,
                        query,
                        ctx.compiledParams(),
                        null,           // cursorToken 通过 sessionTokens 传递
                        null,           // pageNo (EPMC 不使用页码分页)
                        batchSize,
                        stateToken
                ));
            }

            log.info("已生成 {} 个 EPMC 批次（使用游标模式，cursorMark={}）",
                    batches.size(), stateToken.get("cursorMark"));
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
