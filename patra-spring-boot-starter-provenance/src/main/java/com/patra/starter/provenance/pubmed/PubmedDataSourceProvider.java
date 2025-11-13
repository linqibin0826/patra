package com.patra.starter.provenance.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.DataType;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.internal.metadata.PlanMetadata;
import com.patra.starter.provenance.internal.metadata.PubmedPlanMetadata;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.processor.ProcessResult;
import com.patra.starter.provenance.common.processor.ProviderContext;
import com.patra.starter.provenance.common.provider.DataSourceProvider;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.common.provider.ProviderResult;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import com.patra.starter.provenance.pubmed.processor.PubmedLiteratureProcessor;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PubMed 数据源提供者实现
 *
 * <p>封装PubMed的搜索、获取和转换逻辑,遵循配置优先级:运行时快照 > 数据源覆盖 > 共享默认值。
 *
 * <p>核心流程:
 *
 * <ul>
 *   <li>ESearch:执行搜索获取PMID列表(最多10000个)
 *   <li>EPost(可选):当ID数量超过阈值时,上传ID列表到服务器获取WebEnv
 *   <li>EFetch:批量获取文章详细元数据
 *   <li>转换:将PubMed XML响应转换为标准文献格式
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
  private final PubMedClient pubMedClient;
  private final PubMedESearchRequestAssembler requestAssembler;

  @Override
  public String getProvenanceCode() {
    return PROVENANCE_CODE;
  }

  @Override
  public Set<DataType> getSupportedDataTypes() {
    return SUPPORTED_TYPES;
  }

  @Override
  public PlanMetadata preparePlan(String query, JsonNode params, ProvenanceConfig config) {
    log.debug("准备 PubMed 计划元数据: query={}", query);

    try {
      // 1. 构建 ESearch 请求(带 usehistory=y 以获取 WebEnv)
      ESearchRequest request = requestAssembler.buildCount(params);

      // 2. 调用 PubMed API
      ESearchResponse response = pubMedClient.esearch(request, config);

      // 3. 提取元数据
      ESearchResponse.Result result = response.result();
      int totalCount = result != null ? result.count() : 0;
      String webEnv = result != null ? result.webEnv() : null;
      String queryKey = result != null ? result.queryKey() : null;

      log.info("PubMed 计划元数据已准备: totalCount={}, hasWebEnv={}",
               totalCount, webEnv != null);

      // 4. 返回 PubmedPlanMetadata
      return new PubmedPlanMetadata(totalCount, webEnv, queryKey);

    } catch (ProvenanceClientException ex) {
      log.error("准备 PubMed 计划元数据失败: query={}", query, ex);
      throw ex;
    } catch (Exception ex) {
      log.error("准备 PubMed 计划元数据时发生未知错误: query={}", query, ex);
      throw new ProvenanceClientException(
          "pubmed",
          "preparePlan",
          "准备 PubMed 计划元数据失败: " + ex.getMessage(),
          ex
      );
    }
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
