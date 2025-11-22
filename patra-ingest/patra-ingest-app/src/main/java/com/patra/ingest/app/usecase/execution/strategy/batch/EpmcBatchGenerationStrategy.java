package com.patra.ingest.app.usecase.execution.strategy.batch;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.domain.strategy.BatchGenerationStrategy;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// EPMC 批次生成策略
///
/// 根据查询会话生成批次列表。
///
/// EPMC 使用 Solr 风格的 cursorMark 分页机制，具体参数映射由 Infrastructure 层的 EpmcParameterMapper 处理。
///
/// @author Patra Architecture Team
/// @since 0.1.0
@Component
@Slf4j
public class EpmcBatchGenerationStrategy implements BatchGenerationStrategy {

  @Override
  public ProvenanceCode getSupportedProvenanceCode() {
    return ProvenanceCode.EPMC;
  }

  @Override
  public List<Batch> generateBatches(QuerySession session, ExecutionContext ctx) {
    List<Batch> batches = new ArrayList<>();
    int batchSize = ctx.configSnapshot().pagination().pageSizeValue();
    int totalRecords = session.totalRecords();

    if (totalRecords <= 0) {
      log.info("EPMC 查询结果为空（totalRecords=0），返回空批次列表");
      return batches;
    }

    int pageCount = (int) Math.ceil((double) totalRecords / batchSize);
    String query = ctx.compiledQuery();

    log.debug(
        "生成 EPMC 批次: totalRecords={}, batchSize={}, pageCount={}",
        totalRecords,
        batchSize,
        pageCount);

    // 使用统一的批次计算逻辑（只包含 offset/limit，不包含数据源特定参数）
    for (int i = 0; i < pageCount; i++) {
      int batchNo = i + 1;
      int offset = i * batchSize;
      batches.add(new Batch(batchNo, query, offset, batchSize));
    }

    log.info("已生成 {} 个 EPMC 批次", batches.size());

    return batches;
  }
}
