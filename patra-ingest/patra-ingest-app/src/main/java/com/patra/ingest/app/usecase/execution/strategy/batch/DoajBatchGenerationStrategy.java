package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.fetch.FetchMetadata;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DOAJ 批次生成策略
 *
 * <p>根据抓取元数据生成批次列表。
 *
 * <p>DOAJ 使用 Elasticsearch Scroll API 分页机制，具体参数映射由 Infrastructure 层的 DoajParameterMapper 处理。
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
@Component
@Slf4j
public class DoajBatchGenerationStrategy implements BatchGenerationStrategy {

  @Override
  public ProvenanceCode getSupportedProvenanceCode() {
    return ProvenanceCode.DOAJ;
  }

  @Override
  public List<Batch> generateBatches(FetchMetadata metadata, ExecutionContext ctx) {
    List<Batch> batches = new ArrayList<>();
    int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
    int totalRecords = metadata.totalRecords();

    if (totalRecords <= 0) {
      log.info("DOAJ 查询结果为空（totalRecords=0），返回空批次列表");
      return batches;
    }

    int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
    String query = ctx.compiledQuery();

    log.debug(
        "生成 DOAJ 批次: totalRecords={}, batchSize={}, pageCount={}",
        totalRecords,
        batchSize,
        pageCount);

    // 使用统一的批次计算逻辑（只包含 offset/limit，不包含数据源特定参数）
    for (int i = 0; i < pageCount; i++) {
      int batchNo = i + 1;
      int offset = i * batchSize;
      batches.add(new Batch(batchNo, query, offset, batchSize));
    }

    log.info("已生成 {} 个 DOAJ 批次", batches.size());

    return batches;
  }
}
