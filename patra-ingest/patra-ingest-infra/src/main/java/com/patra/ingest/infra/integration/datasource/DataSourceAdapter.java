package com.patra.ingest.infra.integration.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.model.CanonicalLiterature;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.DataSourcePort;
import com.patra.starter.provenance.common.provider.ProviderRegistry;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.common.provider.ProviderResult;
import com.patra.starter.provenance.common.provider.BatchExecutionParams;
import com.patra.starter.provenance.common.provider.BatchMetadata;
import com.patra.starter.provenance.common.provider.DataSourceProvider;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.PaginationConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import com.patra.starter.provenance.common.config.WindowOffsetConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据源适配器 - Infrastructure 层桥接实现
 *
 * <p><b>职责</b>: 实现 Domain 层的 {@link DataSourcePort} 接口,桥接到 Framework 层的 {@link
 * DataSourceProvider} 实现。
 *
 * <h2>核心转换流程</h2>
 *
 * <ol>
 *   <li>从 {@link ExecutionContext} 提取 provenanceCode
 *   <li>通过 {@link ProviderRegistry} 解析框架层提供者
 *   <li>将 {@link ProvenanceConfigSnapshot} 转换为 {@link ProvenanceConfig}
 *   <li>构建 {@link ProviderRequest}:
 *       <ul>
 *         <li>ExecutionContext.operationCode → ProviderRequest.operationCode
 *         <li>ExecutionContext + Batch → BatchExecutionParams (query + params)
 *         <li>Batch → BatchMetadata (batchNo + cursorToken)
 *       </ul>
 *   <li>调用框架层提供者 {@link DataSourceProvider#fetchData(ProviderRequest)}
 *   <li>将 {@link ProviderResult} 转换为 {@link DataFetchResult}
 * </ol>
 *
 * <h2>错误处理策略</h2>
 *
 * <ul>
 *   <li><b>框架层异常</b>: 捕获并转换为 {@code DataFetchResult.retriableFailure}
 *   <li><b>配置转换失败</b>: 记录警告,使用默认配置继续执行
 *   <li><b>提供者未找到</b>: 转换为 {@code DataFetchResult.nonRetriableFailure}
 * </ul>
 *
 * @see DataSourcePort 领域层端口接口
 * @see DataSourceProvider 框架层提供者接口
 * @see ProviderRegistry 框架层提供者注册表
 * @author linqibin
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSourceAdapter implements DataSourcePort {

  private final ProviderRegistry providerRegistry;

  @Override
  public DataFetchResult fetchData(ExecutionContext context, Batch batch) {
    String provenanceCode = context.provenanceCode();
    String operationCode = context.operationCode();
    int batchNo = batch.batchNo();

    try {
      // 1. 解析框架层提供者
      DataSourceProvider provider = resolveProvider(provenanceCode);
      if (provider == null) {
        String errorMsg = String.format("未找到提供者: provenanceCode=%s", provenanceCode);
        log.error(errorMsg);
        return DataFetchResult.nonRetriableFailure(errorMsg);
      }

      // 2. 转换配置快照为运行时配置
      ProvenanceConfig runtimeConfig = convertConfig(context.configSnapshot());

      // 3. 构建批次执行参数 (查询 + 完整参数载荷)
      BatchExecutionParams executionParams = buildExecutionParams(context, batch);

      // 4. 构建批次元数据 (批次号 + 游标令牌)
      BatchMetadata metadata = buildMetadata(batch);

      // 5. 构建提供者请求
      ProviderRequest request =
          ProviderRequest.builder()
              .operationCode(operationCode)
              .config(runtimeConfig)
              .executionParams(executionParams)
              .metadata(metadata)
              .build();

      if (log.isDebugEnabled()) {
        log.debug(
            "调用数据源提供者 provenanceCode={} operationCode={} batchNo={} hasCursor={}",
            provenanceCode,
            operationCode,
            batchNo,
            batch.hasCursor());
      }

      // 6. 调用框架层提供者
      long startTime = System.currentTimeMillis();
      ProviderResult providerResult = provider.fetchData(request);
      long duration = System.currentTimeMillis() - startTime;

      if (log.isDebugEnabled()) {
        log.debug(
            "数据源提供者返回 provenanceCode={} batchNo={} success={} fetchedCount={} duration={}ms",
            provenanceCode,
            batchNo,
            providerResult.success(),
            providerResult.fetchedCount(),
            duration);
      }

      // 7. 转换结果
      return convertResult(providerResult);

    } catch (Exception ex) {
      String errorMsg =
          String.format(
              "数据源提供者调用异常 provenanceCode=%s batchNo=%d error=%s",
              provenanceCode, batchNo, ex.getMessage());
      log.error(errorMsg, ex);
      return DataFetchResult.retriableFailure(errorMsg);
    }
  }

  /**
   * 解析框架层提供者
   *
   * @param provenanceCode Provenance 代码
   * @return 框架层提供者实例,未找到时返回 null
   */
  private DataSourceProvider resolveProvider(String provenanceCode) {
    try {
      return providerRegistry.getProvider(provenanceCode);
    } catch (Exception ex) {
      log.error("解析提供者失败 provenanceCode={}", provenanceCode, ex);
      return null;
    }
  }

  /**
   * 构建批次执行参数
   *
   * <p>合并 ExecutionContext 和 Batch 的查询和参数:
   *
   * <ul>
   *   <li>优先使用 Batch.query (批次特定查询)
   *   <li>回退使用 ExecutionContext.compiledQuery (任务级查询)
   *   <li>合并 ExecutionContext.compiledParams 和 Batch.params
   * </ul>
   *
   * @param context 执行上下文
   * @param batch 批次定义
   * @return 批次执行参数
   */
  private BatchExecutionParams buildExecutionParams(ExecutionContext context, Batch batch) {
    // 查询字符串: 优先使用 Batch.query, 回退使用 ExecutionContext.compiledQuery
    String query = StringUtils.hasText(batch.query()) ? batch.query() : context.compiledQuery();

    // 参数合并: ExecutionContext.compiledParams + Batch.params
    JsonNode mergedParams = mergeParams(context.compiledParams(), batch.params());

    return new BatchExecutionParams(query, mergedParams);
  }

  /**
   * 合并参数
   *
   * <p>Batch.params 覆盖 ExecutionContext.compiledParams 中的同名字段
   *
   * @param baseParams 基础参数 (来自 ExecutionContext)
   * @param batchParams 批次参数 (来自 Batch)
   * @return 合并后的参数
   */
  private JsonNode mergeParams(JsonNode baseParams, JsonNode batchParams) {
    if (batchParams == null || batchParams.isNull() || batchParams.isEmpty()) {
      return baseParams;
    }
    if (baseParams == null || baseParams.isNull() || baseParams.isEmpty()) {
      return batchParams;
    }

    // 深拷贝 baseParams
    ObjectNode merged = ((ObjectNode) baseParams).deepCopy();

    // 覆盖 batchParams
    batchParams
        .fields()
        .forEachRemaining(
            entry -> {
              merged.set(entry.getKey(), entry.getValue());
            });

    return merged;
  }

  /**
   * 构建批次元数据
   *
   * @param batch 批次定义
   * @return 批次元数据
   */
  private BatchMetadata buildMetadata(Batch batch) {
    return new BatchMetadata(batch.batchNo(), batch.cursorToken());
  }

  /**
   * 转换配置快照为运行时配置
   *
   * <p>将领域层的 {@link ProvenanceConfigSnapshot} 转换为框架层的 {@link ProvenanceConfig}。
   *
   * <p>转换失败时记录警告并返回 null,由框架层使用默认配置。
   *
   * @param snapshot 配置快照
   * @return 运行时配置,失败时返回 null
   */
  private ProvenanceConfig convertConfig(ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null || snapshot.provenance() == null) {
      return null;
    }

    String baseUrl = snapshot.provenance().baseUrlDefault();
    if (!StringUtils.hasText(baseUrl)) {
      log.debug(
          "配置快照缺少 baseUrl,使用默认配置 provenanceId={}",
          snapshot.provenance().id());
      return null;
    }

    try {
      HttpConfig http = convertHttpConfig(snapshot.http());
      PaginationConfig pagination = convertPaginationConfig(snapshot.pagination());
      WindowOffsetConfig windowOffset = convertWindowOffsetConfig(snapshot.windowOffset());
      BatchingConfig batching = convertBatchingConfig(snapshot.batching());
      RetryConfig retry = convertRetryConfig(snapshot.retry());
      RateLimitConfig rateLimit = convertRateLimitConfig(snapshot.rateLimit());

      return new ProvenanceConfig(
          baseUrl.trim(), http, pagination, windowOffset, batching, retry, rateLimit);
    } catch (Exception ex) {
      log.warn(
          "构建运行时配置失败,使用默认配置 provenanceId={}",
          snapshot.provenance().id(),
          ex);
      return null;
    }
  }

  /**
   * 转换 HTTP 配置
   *
   * @param source 配置快照
   * @return HTTP 配置,无有效配置时返回 null
   */
  private HttpConfig convertHttpConfig(ProvenanceConfigSnapshot.HttpConfig source) {
    if (source == null) {
      return null;
    }
    Map<String, String> headers = parseHeaders(source.defaultHeadersJson());
    return new HttpConfig(
        headers,
        source.timeoutConnectMillis(),
        source.timeoutReadMillis(),
        source.timeoutTotalMillis());
  }

  /**
   * 转换分页配置
   *
   * @param source 配置快照
   * @return 分页配置,无有效配置时返回 null
   */
  private PaginationConfig convertPaginationConfig(ProvenanceConfigSnapshot.PaginationConfig source) {
    if (source == null) {
      return null;
    }
    if (source.pageSizeValue() == null && source.maxPagesPerExecution() == null) {
      return null;
    }
    return new PaginationConfig(source.pageSizeValue(), source.maxPagesPerExecution());
  }

  /**
   * 转换窗口偏移配置
   *
   * @param source 配置快照
   * @return 窗口偏移配置,无有效配置时返回 null
   */
  private WindowOffsetConfig convertWindowOffsetConfig(
      ProvenanceConfigSnapshot.WindowOffsetConfig source) {
    if (source == null) {
      return null;
    }
    // 检查是否所有字段都为空
    if (source.windowModeCode() == null
        && source.windowSizeValue() == null
        && source.windowSizeUnitCode() == null
        && source.lookbackValue() == null
        && source.lookbackUnitCode() == null
        && source.overlapValue() == null
        && source.overlapUnitCode() == null
        && source.offsetTypeCode() == null
        && source.maxIdsPerWindow() == null) {
      return null;
    }
    return new WindowOffsetConfig(
        source.windowModeCode(),
        source.windowSizeValue(),
        source.windowSizeUnitCode(),
        source.lookbackValue(),
        source.lookbackUnitCode(),
        source.overlapValue(),
        source.overlapUnitCode(),
        source.offsetTypeCode(),
        source.maxIdsPerWindow());
  }

  /**
   * 转换批处理配置
   *
   * @param source 配置快照
   * @return 批处理配置,无有效配置时返回 null
   */
  private BatchingConfig convertBatchingConfig(ProvenanceConfigSnapshot.BatchingConfig source) {
    if (source == null) {
      return null;
    }
    if (source.detailFetchBatchSize() == null && source.maxIdsPerRequest() == null) {
      return null;
    }
    return new BatchingConfig(source.detailFetchBatchSize(), source.maxIdsPerRequest(), null);
  }

  /**
   * 转换重试配置
   *
   * @param source 配置快照
   * @return 重试配置,无有效配置时返回 null
   */
  private RetryConfig convertRetryConfig(ProvenanceConfigSnapshot.RetryConfig source) {
    if (source == null) {
      return null;
    }
    if (source.maxRetryTimes() == null && source.initialDelayMillis() == null) {
      return null;
    }
    return new RetryConfig(source.maxRetryTimes(), source.initialDelayMillis());
  }

  /**
   * 转换限流配置
   *
   * @param source 配置快照
   * @return 限流配置,无有效配置时返回 null
   */
  private RateLimitConfig convertRateLimitConfig(ProvenanceConfigSnapshot.RateLimitConfig source) {
    if (source == null) {
      return null;
    }
    if (source.maxConcurrentRequests() == null && source.perCredentialQpsLimit() == null) {
      return null;
    }
    return new RateLimitConfig(source.maxConcurrentRequests(), source.perCredentialQpsLimit());
  }

  /**
   * 解析 HTTP 头部 JSON
   *
   * @param headersJson JSON 格式的头部字符串
   * @return 头部键值对,解析失败时返回空 Map
   */
  private Map<String, String> parseHeaders(String headersJson) {
    if (!StringUtils.hasText(headersJson)) {
      return Map.of();
    }
    try {
      JsonNode node = JsonMapperHolder.getObjectMapper().readTree(headersJson);
      if (!node.isObject()) {
        return Map.of();
      }
      Map<String, String> headers = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(
              entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                  headers.put(
                      entry.getKey(), value.isTextual() ? value.asText() : value.toString());
                }
              });
      return headers;
    } catch (Exception ex) {
      log.warn(
          "解析 HTTP 头部 JSON 失败,忽略配置覆盖 length={}",
          headersJson.length(),
          ex);
      return Map.of();
    }
  }

  /**
   * 转换提供者结果为领域层结果
   *
   * <p>直接映射字段:
   *
   * <ul>
   *   <li>success → success
   *   <li>literatures → literatures
   *   <li>nextCursorToken → nextCursorToken
   *   <li>errorMessage → errorMessage
   *   <li>fetchedCount → fetchedCount
   *   <li>errorType → errorType (枚举名称相同)
   * </ul>
   *
   * @param providerResult 框架层结果
   * @return 领域层结果
   */
  private DataFetchResult convertResult(ProviderResult providerResult) {
    List<CanonicalLiterature> literatures = providerResult.literatures();
    String nextCursorToken = providerResult.nextCursorToken();
    String errorMessage = providerResult.errorMessage();
    int fetchedCount = providerResult.fetchedCount();

    // 转换错误类型 (枚举名称相同,直接映射)
    DataFetchResult.ErrorType errorType = convertErrorType(providerResult.errorType());

    return DataFetchResult.builder()
        .success(providerResult.success())
        .literatures(literatures)
        .nextCursorToken(nextCursorToken)
        .errorMessage(errorMessage)
        .fetchedCount(fetchedCount)
        .errorType(errorType)
        .build();
  }

  /**
   * 转换错误类型枚举
   *
   * @param frameworkErrorType 框架层错误类型
   * @return 领域层错误类型
   */
  private DataFetchResult.ErrorType convertErrorType(
      ProviderResult.ErrorType frameworkErrorType) {
    if (frameworkErrorType == null) {
      return DataFetchResult.ErrorType.NONE;
    }
    return switch (frameworkErrorType) {
      case NONE -> DataFetchResult.ErrorType.NONE;
      case RETRIABLE -> DataFetchResult.ErrorType.RETRIABLE;
      case NON_RETRIABLE -> DataFetchResult.ErrorType.NON_RETRIABLE;
      case PARTIAL_SUCCESS -> DataFetchResult.ErrorType.PARTIAL_SUCCESS;
    };
  }
}
