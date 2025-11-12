package com.patra.starter.provenance.pubmed;

import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.processor.ProcessResult;
import com.patra.starter.provenance.common.processor.ProviderContext;
import com.patra.starter.provenance.common.provider.DataSourceProvider;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.common.provider.ProviderResult;
import com.patra.starter.provenance.pubmed.processor.PubmedLiteratureProcessor;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PubMed 数据源提供者实现
 *
 * <p>封装PubMed的搜索、获取和转换逻辑，遵循配置优先级：运行时快照 > 数据源覆盖 > 共享默认值。
 *
 * <p>核心流程：
 *
 * <ul>
 *   <li>ESearch：执行搜索获取PMID列表（最多10000个）
 *   <li>EPost（可选）：当ID数量超过阈值时，上传ID列表到服务器获取WebEnv
 *   <li>EFetch：批量获取文章详细元数据
 *   <li>转换：将PubMed XML响应转换为标准文献格式
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class PubmedDataSourceProvider implements DataSourceProvider {

  private static final String PROVENANCE_CODE = "pubmed";
  private static final Set<DataType> SUPPORTED_TYPES = Set.of(DataType.LITERATURE);

  private final PubmedLiteratureProcessor literatureProcessor;

  @Override
  public String getProvenanceCode() {
    return PROVENANCE_CODE;
  }

  @Override
  public Set<DataType> getSupportedDataTypes() {
    return SUPPORTED_TYPES;
  }

  @Override
  public <T> ProviderResult<T> fetchData(
      ProviderRequest request,
      DataType dataType,
      Class<T> targetClass) {
    long start = System.currentTimeMillis();
    int batchNo = request.metadata().batchNo();
    String operation = request.operationCode();

    log.info("PubMed provider start: operation={} batchNo={} dataType={}", operation, batchNo, dataType);

    // 1. 验证类型一致性
    if (!dataType.getDataClass().isAssignableFrom(targetClass)) {
      String errorMsg = String.format(
          "类型不匹配: DataType期望%s, 但提供了%s",
          dataType.getDataClass().getSimpleName(),
          targetClass.getSimpleName()
      );
      log.error(errorMsg);
      return ProviderResult.nonRetriableFailure(dataType, errorMsg);
    }

    // 2. 根据dataType分派到对应的Processor
    if (dataType == DataType.LITERATURE) {
      try {
        ProviderContext context = ProviderContext.builder().build();
        ProcessResult<CanonicalLiterature> processResult = literatureProcessor.process(request, context);
        ProviderResult<CanonicalLiterature> result = convertToProviderResult(processResult, dataType);

        long duration = System.currentTimeMillis() - start;
        log.info(
            "PubMed provider completed: operation={} batchNo={} fetched={} duration={}ms",
            operation,
            batchNo,
            result.fetchedCount(),
            duration);

        @SuppressWarnings("unchecked")
        ProviderResult<T> typedResult = (ProviderResult<T>) result;
        return typedResult;
      } catch (Exception ex) {
        long duration = System.currentTimeMillis() - start;
        log.error("PubMed provider error: operation={} batchNo={} duration={}ms", operation, batchNo, duration, ex);
        return ProviderResult.nonRetriableFailure(
            dataType,
            "PubMed provider error: " + ex.getMessage());
      }
    }

    // 未来添加其他类型处理...
    // if (dataType == DataType.AUTHOR) { ... }

    String errorMsg = String.format("PubMed不支持数据类型: %s", dataType);
    log.error(errorMsg);
    return ProviderResult.nonRetriableFailure(dataType, errorMsg);
  }

  /**
   * 转换 ProcessResult 为 ProviderResult
   */
  private ProviderResult<CanonicalLiterature> convertToProviderResult(
      ProcessResult<CanonicalLiterature> processResult, DataType dataType) {
    if (processResult.success()) {
      return processResult.errorMessage() != null
          ? ProviderResult.partialSuccess(
              processResult.data(),
              dataType,
              processResult.nextCursor(),
              processResult.errorMessage())
          : ProviderResult.success(
              processResult.data(),
              dataType,
              processResult.nextCursor());
    } else {
      return ProviderResult.nonRetriableFailure(
          dataType,
          processResult.errorMessage());
    }
  }
}
