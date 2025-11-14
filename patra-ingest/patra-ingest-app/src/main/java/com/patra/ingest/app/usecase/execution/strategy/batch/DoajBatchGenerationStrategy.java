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
 * DOAJ 批次生成策略
 *
 * <p>根据 BatchPlan 生成批次列表，支持使用 cursorMark 进行 Elasticsearch Scroll API 分页。
 *
 * <p>DOAJ 使用 Elasticsearch Scroll API 分页机制：
 *
 * <ul>
 *   <li>首次请求：不带 cursorMark，创建 Scroll Context
 *   <li>后续请求：使用上次返回的 cursorMark
 *   <li>分页结束：返回空结果集
 * </ul>
 *
 * <p><strong>关键设计</strong>：
 *
 * <ul>
 *   <li>使用 {@link ExecutionContext} 的 pageSize（配置快照中的分页大小）
 *   <li>cursorMark 通过 {@link Batch#sessionTokens()} 传递（opaque 令牌）
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class DoajBatchGenerationStrategy implements BatchGenerationStrategy {

  @Override
  public String getSupportedDataSourceCode() {
    return "doaj";
  }

  @Override
  public List<Batch> generateBatches(BatchPlan plan, ExecutionContext ctx) {
    List<Batch> batches = new ArrayList<>();
    int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
    int totalRecords = plan.totalRecords();

    if (totalRecords <= 0) {
      log.info("DOAJ 查询结果为空（totalRecords=0），返回空批次列表");
      return batches;
    }

    int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
    String query = ctx.compiledQuery();

    log.debug(
        "生成 DOAJ 批次: totalRecords={}, batchSize={}, pageCount={}, hasStateToken={}",
        totalRecords,
        batchSize,
        pageCount,
        plan.hasStateToken());

    // 检查是否有 state token（包含 cursorMark）
    if (plan.hasStateToken()) {
      // 使用 Scroll API 模式：state token 中包含 cursorMark
      Map<String, String> stateToken = plan.stateToken().orElseThrow();

      for (int i = 0; i < pageCount; i++) {
        int batchNo = i + 1;

        batches.add(
            new Batch(
                batchNo,
                query,
                ctx.compiledParams(),
                null, // cursorMark 通过 sessionTokens 传递
                null, // pageNo (DOAJ 不使用页码分页)
                batchSize,
                stateToken));
      }

      log.info(
          "已生成 {} 个 DOAJ 批次（使用 Scroll API，cursorMark={}）",
          batches.size(),
          stateToken.get("cursorMark"));
    } else {
      // 常规模式（无 state token）
      for (int i = 0; i < pageCount; i++) {
        int batchNo = i + 1;

        batches.add(
            new Batch(
                batchNo,
                query,
                ctx.compiledParams(),
                null, // 无游标令牌
                null, // 无页码
                batchSize,
                null // 无会话令牌
                ));
      }

      log.info("已生成 {} 个 DOAJ 批次（常规模式，无 Scroll API）", batches.size());
    }

    return batches;
  }
}
