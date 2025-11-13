package com.patra.ingest.infra.integration.datasource;

import cn.hutool.core.map.MapUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import com.patra.common.model.DataType;
import com.patra.common.model.plan.PlanMetadata;
import com.patra.common.type.TypeReference;
import com.patra.ingest.domain.model.vo.batch.Batch;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.port.DataSourcePort;
import com.patra.ingest.infra.exception.DataSourceException;
import com.patra.ingest.infra.registry.ProviderNotFoundException;
import com.patra.ingest.infra.registry.ProviderRegistry;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.common.provider.BatchExecutionParams;
import com.patra.starter.provenance.common.provider.BatchMetadata;
import com.patra.starter.provenance.common.provider.DataSourceProvider;
import com.patra.starter.provenance.common.provider.ProviderRequest;
import com.patra.starter.provenance.common.provider.ProviderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据源适配器
 *
 * <p>DataSourceAdapter是Infrastructure层的核心组件，实现Domain层的DataSourcePort接口，
 * 桥接Domain层和Framework层。
 *
 * <p><strong>主要职责</strong>：
 * <ul>
 *   <li>实现DataSourcePort接口（Domain层契约）</li>
 *   <li>使用ProviderRegistry查找Provider（二维索引）</li>
 *   <li>验证类型一致性（DataType vs TypeReference）</li>
 *   <li>转换参数（ExecutionContext + Batch → ProviderRequest）</li>
 *   <li>转换结果（ProviderResult → DataFetchResult）</li>
 * </ul>
 *
 * <p><strong>架构位置</strong>：
 * <pre>
 * Application Layer (GenericBatchExecutor)
 *     ↓ 调用
 * Domain Layer (DataSourcePort接口)
 *     ↑ 实现
 * Infrastructure Layer (DataSourceAdapter) ← [本类]
 *     ↓ 使用
 * ProviderRegistry → DataSourceProvider → DataProcessor
 * </pre>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSourceAdapter implements DataSourcePort {

    private final ProviderRegistry providerRegistry;

    /**
     * 准备采集计划元数据
     *
     * <p><strong>实现流程</strong>:
     * <ol>
     *   <li>从ExecutionContext提取通用参数(query, params, config)</li>
     *   <li>使用ProviderRegistry查找Provider</li>
     *   <li>调用Provider.preparePlan()获取元数据</li>
     *   <li>处理异常转换</li>
     * </ol>
     *
     * @param context 执行上下文
     * @param dataType 数据类型标识
     * @return 计划元数据(使用继承体系支持不同数据源)
     * @throws ProviderNotFoundException 如果Provider不存在
     * @throws DataSourceException 如果调用数据源失败
     */
    @Override
    public PlanMetadata preparePlan(ExecutionContext context, DataType dataType) {
        String provenanceCode = context.provenanceCode();

        log.debug("DataSourceAdapter.preparePlan: provenance={}, dataType={}",
            provenanceCode, dataType);

        try {
            // 1. 查找Provider
            DataSourceProvider provider = providerRegistry.getProvider(provenanceCode, dataType);

            // 2. 提取通用参数
            String query = context.compiledQuery();
            JsonNode params = context.compiledParams();
            ProvenanceConfig config = convertToProvenanceConfig(context);

            // 3. 调用Provider
            PlanMetadata planMetadata = provider.preparePlan(query, params, config);

            log.info("计划元数据已准备: provenance={}, dataType={}, totalCount={}",
                provenanceCode, dataType, planMetadata.totalCount());

            return planMetadata;

        } catch (ProviderNotFoundException ex) {
            log.error("Provider未找到: provenance={}, dataType={}", provenanceCode, dataType, ex);
            throw ex;
        } catch (ProvenanceClientException ex) {
            log.error("调用数据源失败: provenance={}, dataType={}", provenanceCode, dataType, ex);
            throw new DataSourceException("准备计划元数据失败: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("准备计划元数据时发生未知错误: provenance={}, dataType={}",
                provenanceCode, dataType, ex);
            throw new DataSourceException("准备计划元数据失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 转换ExecutionContext为ProvenanceConfig
     *
     * <p>将Domain层的ProvenanceConfigSnapshot转换为Starter层的ProvenanceConfig。
     * 如果没有配置快照,返回null(Provider将使用默认配置)。
     *
     * @param context 执行上下文
     * @return ProvenanceConfig,如果快照为空则返回null
     */
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
            rateLimitConfig
        );
    }

    /**
     * 转换HTTP配置
     */
    private com.patra.starter.provenance.common.config.HttpConfig toHttpConfig(
            com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.HttpConfig httpInfo) {
        if (httpInfo == null) {
            return null;
        }

        // 解析defaultHeadersJson为Map
        Map<String, String> headers = parseHeadersJson(httpInfo.defaultHeadersJson());

        return new com.patra.starter.provenance.common.config.HttpConfig(
            headers,
            httpInfo.timeoutConnectMillis(),
            httpInfo.timeoutReadMillis(),
            httpInfo.timeoutTotalMillis()
        );
    }

    /**
     * 解析Headers JSON字符串为Map
     */
    private Map<String, String> parseHeadersJson(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Map.of();
        }

        try {
            ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
            return mapper.readValue(headersJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("解析Headers JSON失败: {}", headersJson, ex);
            return Map.of();
        }
    }

    /**
     * 转换分页配置
     */
    private com.patra.starter.provenance.common.config.PaginationConfig toPaginationConfig(
            com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.PaginationConfig paginationInfo) {
        if (paginationInfo == null) {
            return null;
        }

        return new com.patra.starter.provenance.common.config.PaginationConfig(
            paginationInfo.pageSizeValue(),
            paginationInfo.maxPagesPerExecution()
        );
    }

    /**
     * 转换窗口偏移配置
     */
    private com.patra.starter.provenance.common.config.WindowOffsetConfig toWindowOffsetConfig(
            com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.WindowOffsetConfig windowOffsetInfo) {
        if (windowOffsetInfo == null) {
            return null;
        }

        return new com.patra.starter.provenance.common.config.WindowOffsetConfig(
            windowOffsetInfo.windowModeCode(),
            windowOffsetInfo.windowSizeValue(),
            windowOffsetInfo.windowSizeUnitCode(),
            windowOffsetInfo.lookbackValue(),
            windowOffsetInfo.lookbackUnitCode(),
            windowOffsetInfo.overlapValue(),
            windowOffsetInfo.overlapUnitCode(),
            windowOffsetInfo.offsetTypeCode(),
            windowOffsetInfo.maxIdsPerWindow()
        );
    }

    /**
     * 转换批处理配置
     */
    private com.patra.starter.provenance.common.config.BatchingConfig toBatchingConfig(
            com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.BatchingConfig batchingInfo) {
        if (batchingInfo == null) {
            return null;
        }

        return new com.patra.starter.provenance.common.config.BatchingConfig(
            batchingInfo.detailFetchBatchSize(),
            batchingInfo.maxIdsPerRequest(),
            null  // epostThreshold字段在Snapshot中不存在,传null
        );
    }

    /**
     * 转换重试配置
     */
    private com.patra.starter.provenance.common.config.RetryConfig toRetryConfig(
            com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.RetryConfig retryInfo) {
        if (retryInfo == null) {
            return null;
        }

        return new com.patra.starter.provenance.common.config.RetryConfig(
            retryInfo.maxRetryTimes(),
            retryInfo.initialDelayMillis()
        );
    }

    /**
     * 转换限流配置
     */
    private com.patra.starter.provenance.common.config.RateLimitConfig toRateLimitConfig(
            com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.RateLimitConfig rateLimitInfo) {
        if (rateLimitInfo == null) {
            return null;
        }

        return new com.patra.starter.provenance.common.config.RateLimitConfig(
            rateLimitInfo.maxConcurrentRequests(),
            rateLimitInfo.perCredentialQpsLimit()
        );
    }

    /**
     * 从数据源获取指定类型的数据
     *
     * <p><strong>实现流程</strong>：
     * <ol>
     *   <li>验证类型一致性（DataType.getDataClass() vs TypeReference.getRawType()）</li>
     *   <li>使用ProviderRegistry查找Provider（二维索引：provenanceCode + dataType）</li>
     *   <li>构建ProviderRequest（合并ExecutionContext和Batch参数）</li>
     *   <li>调用Provider.fetchData()获取数据</li>
     *   <li>转换ProviderResult为DataFetchResult</li>
     * </ol>
     *
     * @param <T> 数据类型
     * @param context 执行上下文
     * @param dataType 数据类型标识
     * @param typeRef 类型引用
     * @param batch 批次定义
     * @return 数据获取结果
     * @throws TypeMismatchException 如果DataType与TypeReference不一致
     * @throws ProviderNotFoundException 如果Provider不存在
     */
    @Override
    public <T> DataFetchResult<T> fetchData(
            ExecutionContext context,
            DataType dataType,
            TypeReference<T> typeRef,
            Batch batch) {

        long startTime = System.currentTimeMillis();
        String provenanceCode = context.provenanceCode();

        log.debug("DataSourceAdapter.fetchData: provenance={}, dataType={}, batch={}",
            provenanceCode, dataType, batch.batchNo());

        try {
            // 1. 验证类型一致性
            validateTypeConsistency(dataType, typeRef);

            // 2. 查找Provider
            DataSourceProvider provider = providerRegistry.getProvider(provenanceCode, dataType);

            // 3. 构建ProviderRequest
            ProviderRequest request = buildProviderRequest(context, batch);

            // 4. 调用Provider
            @SuppressWarnings("unchecked")
            Class<T> targetClass = (Class<T>) typeRef.getRawType();
            ProviderResult<T> providerResult = provider.fetchData(request, dataType, targetClass);

            // 5. 转换结果
            DataFetchResult<T> result = convertToDataFetchResult(providerResult);

            long duration = System.currentTimeMillis() - startTime;
            log.info("数据获取完成: provenance={}, dataType={}, count={}, duration={}ms",
                provenanceCode, dataType, result.fetchedCount(), duration);

            return result;

        } catch (ProviderNotFoundException ex) {
            log.error("Provider未找到: provenance={}, dataType={}", provenanceCode, dataType, ex);
            throw ex;
        } catch (TypeMismatchException ex) {
            log.error("类型不匹配: dataType={}, typeRef={}", dataType, typeRef, ex);
            throw ex;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("数据获取异常: provenance={}, dataType={}, duration={}ms",
                provenanceCode, dataType, duration, ex);

            return DataFetchResult.failure(
                dataType,
                "数据获取异常: " + ex.getMessage(),
                DataFetchResult.ErrorType.RETRIABLE
            );
        }
    }

    /**
     * 判断是否支持指定的数据源和数据类型
     *
     * @param provenanceCode 数据源代码
     * @param dataType 数据类型
     * @return 如果支持则返回true
     */
    @Override
    public boolean supports(String provenanceCode, DataType dataType) {
        return providerRegistry.supports(provenanceCode, dataType);
    }

    /**
     * 获取指定数据源支持的所有数据类型
     *
     * @param provenanceCode 数据源代码
     * @return 数据类型集合
     */
    @Override
    public Set<DataType> getSupportedTypes(String provenanceCode) {
        return providerRegistry.getSupportedTypes(provenanceCode);
    }

    /**
     * 验证类型一致性
     *
     * <p>确保DataType.getDataClass()与TypeReference.getRawType()一致。
     *
     * <p><strong>特殊处理</strong>：支持List<T>泛型类型（防御性处理）
     *
     * @param <T> 数据类型
     * @param dataType 数据类型标识
     * @param typeRef 类型引用
     * @throws TypeMismatchException 如果类型不一致
     */
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
                    log.warn("检测到TypeReference<List<{}>>，应该使用TypeReference<{}>。" +
                            "DataSourcePort.fetchData返回的DataFetchResult<T>中data字段已经是List<T>类型。",
                        innerClass.getSimpleName(),
                        dataType.getDataClass().getSimpleName());
                    actualClass = innerClass;
                }
            }
        }

        if (!expectedClass.isAssignableFrom(actualClass)) {
            throw new TypeMismatchException(
                String.format("类型不匹配: DataType期望%s, TypeReference提供%s",
                    expectedClass.getSimpleName(), actualClass.getSimpleName())
            );
        }

        log.debug("类型验证通过: dataType={}, typeRef={}",
            dataType, actualClass.getSimpleName());
    }

    /**
     * 构建ProviderRequest
     *
     * <p>合并ExecutionContext和Batch中的参数。
     *
     * @param context 执行上下文
     * @param batch 批次定义
     * @return Provider请求参数
     */
    private ProviderRequest buildProviderRequest(ExecutionContext context, Batch batch) {
        // 构建BatchExecutionParams
        String query = context.compiledQuery();
        JsonNode params = buildParametersAsJson(context, batch);
        BatchExecutionParams executionParams = new BatchExecutionParams(query, params);

        // 构建BatchMetadata
        BatchMetadata metadata = new BatchMetadata(batch.batchNo(), batch.cursorToken());

        return ProviderRequest.builder()
            .operationCode(context.operationCode())
            // config 设置为 null 的设计理由：
            // 1. ProvenanceConfig 由 DataSourceProvider 内部从 ProvenanceProperties 获取
            // 2. ExecutionContext.configSnapshot 是 Domain 层的 ProvenanceConfigSnapshot 类型，
            //    主要用于执行审计和任务回放，而非运行时配置传递
            // 3. 避免跨层类型转换（Domain → Starter），保持架构分层清晰
            // 4. 当前设计已验证可行：Provider 内部通过 properties.mergeWithRuntime() 获取完整配置
            .config(null)
            .executionParams(executionParams)
            .metadata(metadata)
            .build();
    }

    /**
     * 构建参数JsonNode
     *
     * <p>合并ExecutionContext和Batch中的参数，Batch参数优先级更高。
     *
     * @param context 执行上下文
     * @param batch 批次定义
     * @return 参数JsonNode
     */
    private JsonNode buildParametersAsJson(ExecutionContext context, Batch batch) {
        // 使用MapUtil构建Map
        Map<String, Object> params = MapUtil.<String, Object>newHashMap();

        // 从Context添加参数
        params.put("provenanceCode", context.provenanceCode());
        params.put("operationCode", context.operationCode());
        params.put("runId", context.runId());

        // 从Batch添加参数（优先级更高）
        params.put("batchNo", batch.batchNo());
        params.put("pageSize", batch.pageSize());

        // 条件添加cursorToken
        if (batch.cursorToken() != null) {
            params.put("cursorToken", batch.cursorToken());
        }

        // 转换为JsonNode
        ObjectMapper mapper = JsonMapperHolder.getObjectMapper();
        return mapper.valueToTree(params);
    }

    /**
     * 转换ProviderResult为DataFetchResult
     *
     * @param <T> 数据类型
     * @param providerResult Provider结果
     * @return DataFetchResult
     */
    private <T> DataFetchResult<T> convertToDataFetchResult(ProviderResult<T> providerResult) {
        if (providerResult.success()) {
            return DataFetchResult.<T>builder()
                .success(true)
                .data(providerResult.data())
                .dataType(providerResult.dataType())
                .nextCursorToken(providerResult.nextCursorToken())
                .fetchedCount(providerResult.fetchedCount())
                .errorType(DataFetchResult.ErrorType.NONE)
                .errorMessage(providerResult.errorMessage())  // 可能有警告消息
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

    /**
     * 转换错误类型
     *
     * @param providerErrorType Provider错误类型
     * @return DataSourcePort错误类型
     */
    private DataFetchResult.ErrorType convertErrorType(ProviderResult.ErrorType providerErrorType) {
        return switch (providerErrorType) {
            case NONE -> DataFetchResult.ErrorType.NONE;
            case RETRIABLE -> DataFetchResult.ErrorType.RETRIABLE;
            case NON_RETRIABLE -> DataFetchResult.ErrorType.NON_RETRIABLE;
            case PARTIAL_SUCCESS -> DataFetchResult.ErrorType.PARTIAL_SUCCESS;
        };
    }
}
