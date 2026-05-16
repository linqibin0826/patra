package dev.linqibin.patra.ingest.app.usecase.execution.strategy.batch;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.ingest.domain.model.vo.batch.Batch;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionContext;
import dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession;
import dev.linqibin.patra.ingest.domain.strategy.BatchGenerationStrategy;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed 批次生成策略
///
/// 根据查询会话生成批次列表，支持使用 WebEnv 会话令牌优化批次请求。
///
/// @author linqibin
/// @since 0.1.0
@Component
@Slf4j
public class PubmedBatchGenerationStrategy implements BatchGenerationStrategy {

  @Override
  public ProvenanceCode getSupportedProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  @Override
  public List<Batch> generateBatches(QuerySession session, ExecutionContext ctx) {
    List<Batch> batches = new ArrayList<>();
    int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
    int totalRecords = session.totalRecords();

    if (totalRecords <= 0) {
      log.info("PubMed 查询结果为空（totalRecords=0），返回空批次列表");
      return batches;
    }

    int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
    String query = ctx.compiledQuery();

    log.debug(
        "生成 PubMed 批次: totalRecords={}, batchSize={}, pageCount={}",
        totalRecords,
        batchSize,
        pageCount);

    // 使用统一的批次计算逻辑（只包含 offset/limit，不包含数据源特定参数）
    for (int i = 0; i < pageCount; i++) {
      int batchNo = i + 1;
      int offset = i * batchSize;
      batches.add(new Batch(batchNo, query, offset, batchSize));
    }

    log.info("已生成 {} 个 PubMed 批次", batches.size());

    return batches;
  }
}
