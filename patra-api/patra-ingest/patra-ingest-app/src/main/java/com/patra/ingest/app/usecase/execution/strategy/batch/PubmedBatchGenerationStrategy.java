package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.plan.BatchPlan;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed 批次生成策略
 *
 * <p>根据 BatchPlan 生成批次列表，支持使用 WebEnv 会话令牌优化批次请求。
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {

  @Override
  public String getSupportedDataSourceCode() {
    return "pubmed";
  }

  @Override
  public List<Batch> generateBatches(BatchPlan plan, ExecutionContext ctx) {
    List<Batch> batches = new ArrayList<>();
    int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
    int totalRecords = plan.totalRecords();

    if (totalRecords <= 0) {
      log.info("PubMed 查询结果为空（totalRecords=0），返回空批次列表");
      return batches;
    }

    int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
    String query = ctx.compiledQuery();

    log.debug(
        "生成 PubMed 批次: totalRecords={}, batchSize={}, pageCount={}, hasStateToken={}",
        totalRecords,
        batchSize,
        pageCount,
        plan.hasStateToken());

    // 检查是否有 History Server session token
    if (plan.hasStateToken()) {
      // 使用 History Server 模式
      Map<String, String> stateToken = plan.stateToken().orElseThrow();
      String webEnv = stateToken.get("webEnv");
      String queryKey = stateToken.get("queryKey");

      for (int i = 0; i < pageCount; i++) {
        int pageNo = i + 1;
        int startOffset = i * batchSize;

        batches.add(
            Batch.withPageAndSession(
                pageNo, query, ctx.compiledParams(), startOffset, batchSize, webEnv, queryKey));
      }

      log.info("已生成 {} 个 PubMed 批次（使用 History Server: webEnv={}）", batches.size(), webEnv);
    } else {
      // 常规模式（无 session token）
      for (int i = 0; i < pageCount; i++) {
        int pageNo = i + 1;
        int startOffset = i * batchSize;

        batches.add(Batch.withPage(pageNo, query, ctx.compiledParams(), startOffset, batchSize));
      }

      log.info("已生成 {} 个 PubMed 批次（常规模式，无 History Server）", batches.size());
    }

    return batches;
  }
}
