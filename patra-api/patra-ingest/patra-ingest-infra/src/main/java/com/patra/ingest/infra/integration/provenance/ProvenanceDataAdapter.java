package com.patra.ingest.infra.integration.provenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.model.DataType;
import com.patra.common.type.TypeReference;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.query.QuerySession;
import com.patra.ingest.domain.port.ProvenanceDataPort;
import com.patra.ingest.infra.exception.ProvenanceDataException;
import com.patra.ingest.infra.integration.provenance.acl.QuerySessionTranslator;
import com.patra.ingest.infra.mapper.ProviderParameterMapper;
import com.patra.ingest.infra.mapper.ProviderParameterMapperRegistry;
import com.patra.starter.provenance.common.config.BatchingConfig;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.PaginationConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.config.RateLimitConfig;
import com.patra.starter.provenance.common.config.RetryConfig;
import com.patra.starter.provenance.common.config.WindowOffsetConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.provider.BatchExecutionParams;
import com.patra.starter.provenance.common.provider.ProvenanceDataProvider;
import com.patra.starter.provenance.common.provider.ProviderNotFoundException;
import com.patra.starter.provenance.common.provider.ProviderRegistry;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.common.provider.ProviderResult;
import com.patra.starter.provenance.internal.metadata.PlanMetadata;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 数据源适配器
///
/// ProvenanceDataAdapter是Infrastructure层的核心组件，实现Domain层的ProvenanceDataPort接口，
/// 桥接Domain层和Framework层。
///
/// **主要职责**：
///
/// - 实现ProvenanceDataPort接口（Domain层契约）
///   - 使用ProviderRegistry查找Provider（二维索引）
///   - 验证类型一致性（DataType vs TypeReference）
///   - 转换参数（ExecutionContext + Batch → ProviderRequest）
///   - 转换结果（ProviderResult → DataFetchResult）
///
/// **架构位置**：
///
/// ```
///
/// Application Layer (GenericBatchExecutor)
///     ↓ 调用
/// Domain Layer (ProvenanceDataPort接口)
///     ↑ 实现
/// Infrastructure Layer (ProvenanceDataAdapter) ← [本类]
///     ↓ 使用
/// ProviderRegistry → ProvenanceDataProvider → DataProcessor
///
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Component
@RequiredArgsConstructor
@Slf4j
public class ProvenanceDataAdapter implements ProvenanceDataPort {

  private final ProviderRegistry providerRegistry;
  private final QuerySessionTranslator querySessionTranslator;
  private final ProviderParameterMapperRegistry parameterMapperRegistry;

  /// 准备查询会话
  ///
  /// **实现流程**:
  ///
  /// @param context 执行上下文
  /// @param dataType 数据类型标识
  /// @return 查询会话（领域模型）
  /// @throws ProviderNotFoundException 如果Provider不存在
  /// @throws ProvenanceDataException 如果调用数据源失败
  @Override
  public QuerySession prepareQuerySession(ExecutionContext context, DataType dataType) {
    ProvenanceCode provenanceCode = context.provenanceCode();

    log.debug(
        "ProvenanceDataAdapter.prepareQuerySession: provenance={}, dataType={}",
        provenanceCode.getCode(),
        dataType);

    try {
      // 1. 查找Provider
      ProvenanceDataProvider provider = providerRegistry.getProvider(provenanceCode, dataType);

      // 2. 提取通用参数
      String query = context.compiledQuery();
      JsonNode params = context.compiledParams();
      ProvenanceConfig config = convertToProvenanceConfig(context);

      // 3. 调用Provider获取PlanMetadata
      PlanMetadata planMetadata = provider.preparePlan(query, params, config);

      log.info(
          "计划元数据已准备: provenance={}, dataType={}, totalCount={}",
          provenanceCode.getCode(),
          dataType,
          planMetadata.totalCount());

      // 4. 使用翻译器转换为领域模型
      QuerySession querySession = querySessionTranslator.translate(planMetadata);

      log.debug(
          "计划元数据已翻译为领域模型: provenanceCode={}, totalRecords={}, hasStateToken={}",
          querySession.provenanceCode(),
          querySession.totalRecords(),
          querySession.hasStateToken());

      return querySession;

    } catch (ProviderNotFoundException ex) {
      log.error("Provider未找到: provenance={}, dataType={}", provenanceCode.getCode(), dataType, ex);
      throw ex;
    } catch (ProvenanceClientException ex) {
      log.error("调用数据源失败: provenance={}, dataType={}", provenanceCode.getCode(), dataType, ex);
      throw new ProvenanceDataException("准备计划元数据失败: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      log.error(
          "准备计划元数据时发生未知错误: provenance={}, dataType={}", provenanceCode.getCode(), dataType, ex);
      throw new ProvenanceDataException("准备计划元数据失败: " + ex.getMessage(), ex);
    }
  }

  /// 转换ExecutionContext为ProvenanceConfig
  ///
  /// 将Domain层的ProvenanceConfigSnapshot转换为Starter层的ProvenanceConfig。
  /// 如果没有配置快照,返回null(Provider将使用默认配置)。
  ///
  /// @param context 执行上下文
  /// @return ProvenanceConfig,如果快照为空则返回null
  private ProvenanceConfig convertToProvenanceConfig(ExecutionContext context) {
    var snapshot = context.configSnapshot();

    // 如果没有配置快照,返回null(Provider将使用默认配置)
    if (snapshot == null || snapshot.provenance() == null) {
      log.debug("配置快照为空,Provider将使用默认配置");
      return null;
    }

    // 提取ProvenanceInfo
    var prov = snapshot.provenance();
    String baseUrl = prov.baseUrlDefault();

    // 转换各个配置维度
    var httpConfig = toHttpConfig(snapshot.http());
    var paginationConfig = toPaginationConfig(snapshot.pagination());
    var windowOffsetConfig = toWindowOffsetConfig(snapshot.windowOffset());
    var batchingConfig = toBatchingConfig(snapshot.batching());
    var retryConfig = toRetryConfig(snapshot.retry());
    var rateLimitConfig = toRateLimitConfig(snapshot.rateLimit());

    return new ProvenanceConfig(
        baseUrl,
        httpConfig,
        paginationConfig,
        windowOffsetConfig,
        batchingConfig,
        retryConfig,
        rateLimitConfig);
  }

  /// 转换HTTP配置
  private HttpConfig toHttpConfig(
      com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.HttpConfig httpInfo) {
    if (httpInfo == null) {
      return null;
    }

    // 解析defaultHeadersJson为Map
    Map<String, String> headers = parseHeadersJson(httpInfo.defaultHeadersJson());

    return new HttpConfig(
        headers,
        httpInfo.timeoutConnectMillis(),
        httpInfo.timeoutReadMillis(),
        httpInfo.timeoutTotalMillis());
  }

  /// 解析Headers JSON字符串为Map
  private Map<String, String> parseHeadersJson(String headersJson) {
    if (headersJson == null || headersJson.isBlank()) {
      return Map.of();
    }

    try {
      ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
      return mapper.readValue(
          headersJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception ex) {
      log.warn("解析Headers JSON失败: {}", headersJson, ex);
      return Map.of();
    }
  }

  /// 转换分页配置
  private PaginationConfig toPaginationConfig(
      com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.PaginationConfig
          paginationInfo) {
    if (paginationInfo == null) {
      return null;
    }

    return new PaginationConfig(
        paginationInfo.pageSizeValue(), paginationInfo.maxPagesPerExecution());
  }

  /// 转换窗口偏移配置
  private WindowOffsetConfig toWindowOffsetConfig(
      com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.WindowOffsetConfig
          windowOffsetInfo) {
    if (windowOffsetInfo == null) {
      return null;
    }

    return new WindowOffsetConfig(
        windowOffsetInfo.windowModeCode(),
        windowOffsetInfo.windowSizeValue(),
        windowOffsetInfo.windowSizeUnitCode(),
        windowOffsetInfo.lookbackValue(),
        windowOffsetInfo.lookbackUnitCode(),
        windowOffsetInfo.overlapValue(),
        windowOffsetInfo.overlapUnitCode(),
        windowOffsetInfo.offsetTypeCode(),
        windowOffsetInfo.maxIdsPerWindow());
  }

  /// 转换批处理配置
  private BatchingConfig toBatchingConfig(
      com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.BatchingConfig batchingInfo) {
    if (batchingInfo == null) {
      return null;
    }

    return new BatchingConfig(
        batchingInfo.detailFetchBatchSize(),
        batchingInfo.maxIdsPerRequest(),
        null // epostThreshold字段在Snapshot中不存在,传null
        );
  }

  /// 转换重试配置
  private RetryConfig toRetryConfig(
      com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.RetryConfig retryInfo) {
    if (retryInfo == null) {
      return null;
    }

    return new RetryConfig(retryInfo.maxRetryTimes(), retryInfo.initialDelayMillis());
  }

  /// 转换限流配置
  private RateLimitConfig toRateLimitConfig(
      com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.RateLimitConfig
          rateLimitInfo) {
    if (rateLimitInfo == null) {
      return null;
    }

    return new RateLimitConfig(
        rateLimitInfo.maxConcurrentRequests(), rateLimitInfo.perCredentialQpsLimit());
  }

  /// 从数据源获取指定类型的数据
  ///
  /// **实现流程**：
  ///
  /// @param <T> 数据类型
  /// @param context 执行上下文
  /// @param dataType 数据类型标识
  /// @param typeRef 类型引用
  /// @param batch 批次定义
  /// @param querySession 查询会话（包含总记录数、会话令牌等）
  /// @return 数据获取结果
  /// @throws TypeMismatchException 如果DataType与TypeReference不一致
  /// @throws ProviderNotFoundException 如果Provider不存在
  @Override
  public <T> DataFetchResult<T> fetchData(
      ExecutionContext context,
      DataType dataType,
      TypeReference<T> typeRef,
      Batch batch,
      QuerySession querySession) {

    long startTime = System.currentTimeMillis();
    ProvenanceCode provenanceCode = context.provenanceCode();

    log.debug(
        "ProvenanceDataAdapter.fetchData: provenance={}, dataType={}, batch={}",
        provenanceCode.getCode(),
        dataType,
        batch.batchNo());

    try {
      // 1. 验证类型一致性
      validateTypeConsistency(dataType, typeRef);

      // 2. 查找Provider
      ProvenanceDataProvider provider = providerRegistry.getProvider(provenanceCode, dataType);

      // 3. 构建ProviderRequest（使用 ParameterMapper 进行参数映射）
      ProviderRequest request = buildProviderRequest(context, batch, querySession);

      // 4. 调用Provider
      @SuppressWarnings("unchecked")
      Class<T> targetClass = (Class<T>) typeRef.getRawType();
      ProviderResult<T> providerResult = provider.fetchData(request, dataType, targetClass);

      // 5. 转换结果
      DataFetchResult<T> result = convertToDataFetchResult(providerResult);

      long duration = System.currentTimeMillis() - startTime;
      log.info(
          "数据获取完成: provenance={}, dataType={}, count={}, duration={}ms",
          provenanceCode.getCode(),
          dataType,
          result.fetchedCount(),
          duration);

      return result;

    } catch (ProviderNotFoundException ex) {
      log.error("Provider未找到: provenance={}, dataType={}", provenanceCode.getCode(), dataType, ex);
      throw ex;
    } catch (TypeMismatchException ex) {
      log.error("类型不匹配: dataType={}, typeRef={}", dataType, typeRef, ex);
      throw ex;
    } catch (Exception ex) {
      long duration = System.currentTimeMillis() - startTime;
      log.error(
          "数据获取异常: provenance={}, dataType={}, duration={}ms",
          provenanceCode.getCode(),
          dataType,
          duration,
          ex);

      return DataFetchResult.failure(
          dataType, "数据获取异常: " + ex.getMessage(), DataFetchResult.ErrorType.RETRIABLE);
    }
  }

  /// 判断是否支持指定的数据源和数据类型
  ///
  /// @param provenanceCode 数据源代码
  /// @param dataType 数据类型
  /// @return 如果支持则返回true
  @Override
  public boolean supports(ProvenanceCode provenanceCode, DataType dataType) {
    return providerRegistry.supports(provenanceCode, dataType);
  }

  /// 获取指定数据源支持的所有数据类型
  ///
  /// @param provenanceCode 数据源代码
  /// @return 数据类型集合
  @Override
  public Set<DataType> getSupportedTypes(ProvenanceCode provenanceCode) {
    return providerRegistry.getSupportedTypes(provenanceCode);
  }

  /// 验证类型一致性
  ///
  /// 确保DataType.getDataClass()与TypeReference.getRawType()一致。
  ///
  /// **特殊处理**：支持List<T>泛型类型（防御性处理）
  ///
  /// @param <T> 数据类型
  /// @param dataType 数据类型标识
  /// @param typeRef 类型引用
  /// @throws TypeMismatchException 如果类型不一致
  private <T> void validateTypeConsistency(DataType dataType, TypeReference<T> typeRef) {
    Class<?> expectedClass = dataType.getDataClass();
    Class<?> actualClass = typeRef.getRawType();

    // 防御性处理：如果错误地传入了TypeReference<List<T>>，提取内部类型
    // 正确用法应该是TypeReference<T>，而非TypeReference<List<T>>
    if (List.class.isAssignableFrom(actualClass)) {
      Type type = typeRef.getType();
      if (type instanceof ParameterizedType paramType) {
        Type[] args = paramType.getActualTypeArguments();
        if (args.length > 0 && args[0] instanceof Class<?> innerClass) {
          log.warn(
              "检测到TypeReference<List<{}>>，应该使用TypeReference<{}>。"
                  + "ProvenanceDataPort.fetchData返回的DataFetchResult<T>中data字段已经是List<T>类型。",
              innerClass.getSimpleName(),
              dataType.getDataClass().getSimpleName());
          actualClass = innerClass;
        }
      }
    }

    if (!expectedClass.isAssignableFrom(actualClass)) {
      throw new TypeMismatchException(
          String.format(
              "类型不匹配: DataType期望%s, TypeReference提供%s",
              expectedClass.getSimpleName(), actualClass.getSimpleName()));
    }

    log.debug("类型验证通过: dataType={}, typeRef={}", dataType, actualClass.getSimpleName());
  }

  /// 构建ProviderRequest
  ///
  /// 将业务层的查询和分页参数转换为技术层的请求参数。
  ///
  /// @param context 执行上下文
  /// @param batch 批次定义
  /// @param querySession 查询会话
  /// @return Provider请求参数
  private ProviderRequest buildProviderRequest(
      ExecutionContext context, Batch batch, QuerySession querySession) {
    // 构建BatchExecutionParams
    String query = context.compiledQuery();

    // 使用 ParameterMapper 进行参数映射
    ProvenanceCode provenanceCode = context.provenanceCode();
    ProviderParameterMapper mapper = parameterMapperRegistry.getMapper(provenanceCode);
    JsonNode mappedParams = mapper.mapParameters(batch, context.compiledParams(), querySession);

    BatchExecutionParams executionParams = new BatchExecutionParams(query, mappedParams);

    // 从 ExecutionContext 转换配置快照为 ProvenanceConfig
    ProvenanceConfig config = convertToProvenanceConfig(context);

    return ProviderRequest.builder().config(config).executionParams(executionParams).build();
  }

  /// 转换ProviderResult为DataFetchResult
  ///
  /// @param <T> 数据类型
  /// @param providerResult Provider结果
  /// @return DataFetchResult
  private <T> DataFetchResult<T> convertToDataFetchResult(ProviderResult<T> providerResult) {
    if (providerResult.success()) {
      return DataFetchResult.<T>builder()
          .success(true)
          .data(providerResult.data())
          .dataType(providerResult.dataType())
          .nextCursorToken(providerResult.nextCursorToken())
          .fetchedCount(providerResult.fetchedCount())
          .errorType(DataFetchResult.ErrorType.NONE)
          .errorMessage(providerResult.errorMessage()) // 可能有警告消息
          .metadata(providerResult.metadata())
          .build();
    } else {
      return DataFetchResult.<T>builder()
          .success(false)
          .dataType(providerResult.dataType())
          .errorMessage(providerResult.errorMessage())
          .errorType(convertErrorType(providerResult.errorType()))
          .fetchedCount(0)
          .build();
    }
  }

  /// 转换错误类型
  ///
  /// @param providerErrorType Provider错误类型
  /// @return ProvenanceDataPort错误类型
  private DataFetchResult.ErrorType convertErrorType(ProviderResult.ErrorType providerErrorType) {
    return switch (providerErrorType) {
      case NONE -> DataFetchResult.ErrorType.NONE;
      case RETRIABLE -> DataFetchResult.ErrorType.RETRIABLE;
      case NON_RETRIABLE -> DataFetchResult.ErrorType.NON_RETRIABLE;
      case PARTIAL_SUCCESS -> DataFetchResult.ErrorType.PARTIAL_SUCCESS;
    };
  }
}
