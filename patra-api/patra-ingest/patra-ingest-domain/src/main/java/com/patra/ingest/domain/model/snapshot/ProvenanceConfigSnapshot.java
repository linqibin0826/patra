package com.patra.ingest.domain.model.snapshot;

import java.time.Instant;
import java.util.List;

/**
 * 来源配置聚合快照（Domain Snapshot）。<br>
 * <p>该聚合对象在 Ingest 领域层内作为<strong>只读不可变</strong>的配置载体，通常由应用服务从 Registry API 拉取
 * {@code ProvenanceConfigResp} 后转换而来，并在一次调度周期内缓存/传递，保证执行阶段配置一致性。</p>
 * <p>聚合内容覆盖：来源基础信息 + 端点定义 + 时间窗口/增量指针 + 分页 + HTTP 策略 + 批量策略 + 重试策略 + 限流策略 + 凭证集合。</p>
 * <p>设计要点：
 * <ul>
 *   <li>全部使用 Java record 实现领域内“值对象”语义，禁止在运行时被修改；</li>
 *   <li>允许某些子组件为 {@code null}（表示该来源在当前 scope/taskType 下未配置该维度，调用逻辑需做降级兜底）；</li>
 *   <li>凭证使用列表以支持多 Key 轮换与精细化调度策略（优先 defaultPreferred + 生命周期状态）；</li>
 *   <li>窗口/分页/限流等具备时间片的配置，已在聚合装配阶段按“当前时刻”选择单条有效配置，调用时无需再次判定区间。</li>
 * </ul></p>
 * <p>典型用法：
 * <ol>
 *   <li>调度器获取快照 → 根据 windowOffset 生成待处理窗口列表；</li>
 *   <li>执行器按 pagination / batching 形成请求批次；</li>
 *   <li>HTTP 层引用 http / retry / rateLimit 计算连接参数与限流/重试策略；</li>
 *   <li>鉴权器按 endpoint + credentials 选择合适凭证注入；</li>
 *   <li>结束后将本快照与运行统计一起记录以便审计与重放。</li>
 * </ol></p>
 * <p>线程安全：对象全不可变，可安全在多线程间共享。</p>
 *
 * @param provenance   来源基础信息（必填）。
 * @param endpoint     端点定义（可空）。
 * @param windowOffset 时间窗口 / 增量指针策略（可空）。
 * @param pagination   分页 / 游标策略（可空）。
 * @param http         HTTP 策略（可空）。
 * @param batching     批量 / 请求成型策略（可空）。
 * @param retry        重试与退避策略（可空）。
 * @param rateLimit    限流与并发策略（可空）。
 * @param credentials  凭证集合（可为空列表）。
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigSnapshot(
        /* 来源基础信息 */
        ProvenanceInfo provenance,
        /* 端点定义（缺失表示不需要固定 HTTP 端点） */
        EndpointDefinition endpoint,
        /* 时间窗口 / 增量指针配置 */
        WindowOffsetConfig windowOffset,
        /* 分页 / 游标配置 */
        PaginationConfig pagination,
        /* HTTP 通信策略 */
        HttpConfig http,
        /* 批量 / 请求成型策略 */
        BatchingConfig batching,
        /* 重试与退避策略 */
        RetryConfig retry,
        /* 限流与并发策略 */
        RateLimitConfig rateLimit,
        /* 凭证集合 */
        List<CredentialConfig> credentials
) {

    /**
     * 来源基础信息。
     */
    public record ProvenanceInfo(
            /* 内部主键 ID */ Long id,
            /* 稳定来源编码 */ String code,
            /* 显示名称 */ String name,
            /* 默认基础 URL */ String baseUrlDefault,
            /* 默认业务时区 */ String timezoneDefault,
            /* 官方/文档 URL */ String docsUrl,
            /* 是否激活 */ boolean active,
            /* 生命周期状态编码 */ String lifecycleStatusCode
    ) {
    }

    /**
     * 端点定义配置。
     */
    public record EndpointDefinition(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 端点内部名 */ String endpointName,
            /* 端点用途编码 */ String endpointUsageCode,
            /* HTTP 方法 */ String httpMethodCode,
            /* 路径模板 */ String pathTemplate,
            /* 默认 Query 参数 JSON */ String defaultQueryParamsJson,
            /* 默认 Body JSON */ String defaultBodyPayloadJson,
            /* 请求内容类型 */ String requestContentType,
            /* 是否需要鉴权 */ boolean authRequired,
            /* 推荐凭证名 */ String credentialHintName,
            /* 页号参数名 */ String pageParamName,
            /* 页大小参数名 */ String pageSizeParamName,
            /* 游标参数名 */ String cursorParamName,
            /* 批量 ID 参数名 */ String idsParamName,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo
    ) {
    }

    /**
     * 窗口/指针配置。
     */
    public record WindowOffsetConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 窗口模式 */ String windowModeCode,
            /* 窗口大小数值 */ Integer windowSizeValue,
            /* 窗口大小单位 */ String windowSizeUnitCode,
            /* 日历对齐锚点 */ String calendarAlignTo,
            /* 回溯量 */ Integer lookbackValue,
            /* 回溯量单位 */ String lookbackUnitCode,
            /* 重叠量 */ Integer overlapValue,
            /* 重叠量单位 */ String overlapUnitCode,
            /* Watermark 延迟秒 */ Integer watermarkLagSeconds,
            /* 偏移类型 */ String offsetTypeCode,
            /* 偏移字段名 */ String offsetFieldName,
            /* 偏移日期格式 */ String offsetDateFormat,
            /* 备选日期字段 */ String defaultDateFieldName,
            /* 单窗口最大 ID 数 */ Integer maxIdsPerWindow,
            /* 窗口最大跨度秒 */ Integer maxWindowSpanSeconds
    ) {
    }

    /**
     * 分页配置。
     */
    public record PaginationConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 分页模式 */ String paginationModeCode,
            /* 默认页大小 */ Integer pageSizeValue,
            /* 单执行最大页数 */ Integer maxPagesPerExecution,
            /* 页号参数名 */ String pageNumberParamName,
            /* 页大小参数名 */ String pageSizeParamName,
            /* 起始页号 */ Integer startPageNumber,
            /* 排序字段参数名 */ String sortFieldParamName,
            /* 排序方向 */ String sortDirection,
            /* 游标参数名 */ String cursorParamName,
            /* 初始游标值 */ String initialCursorValue,
            /* 下游游标 JSONPath */ String nextCursorJsonpath,
            /* 是否有更多 JSONPath */ String hasMoreJsonpath,
            /* 总数 JSONPath */ String totalCountJsonpath,
            /* 下一个游标 XPath */ String nextCursorXpath,
            /* 是否有更多 XPath */ String hasMoreXpath,
            /* 总数 XPath */ String totalCountXpath
    ) {
    }

    /**
     * HTTP 配置。
     */
    public record HttpConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 覆盖基础 URL */ String baseUrlOverride,
            /* 默认 Header JSON */ String defaultHeadersJson,
            /* 连接超时毫秒 */ Integer timeoutConnectMillis,
            /* 读取超时毫秒 */ Integer timeoutReadMillis,
            /* 总超时毫秒 */ Integer timeoutTotalMillis,
            /* TLS 校验开关 */ boolean tlsVerifyEnabled,
            /* 代理 URL */ String proxyUrlValue,
            /* 接受压缩 */ boolean acceptCompressEnabled,
            /* 偏好 HTTP/2 */ boolean preferHttp2Enabled,
            /* Retry-After 策略 */ String retryAfterPolicyCode,
            /* Retry-After 上限毫秒 */ Integer retryAfterCapMillis,
            /* 幂等 Header 名 */ String idempotencyHeaderName,
            /* 幂等 Key TTL 秒 */ Integer idempotencyTtlSeconds
    ) {
    }

    /**
     * 批量配置。
     */
    public record BatchingConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 明细批量大小 */ Integer detailFetchBatchSize,
            /* 绑定端点 ID */ Long endpointId,
            /* 指定凭证名 */ String credentialName,
            /* ID 参数名 */ String idsParamName,
            /* ID 分隔符 */ String idsJoinDelimiter,
            /* 单请求最大 ID */ Integer maxIdsPerRequest,
            /* 是否紧凑负载 */ boolean preferCompactPayload,
            /* 压缩策略编码 */ String payloadCompressStrategyCode,
            /* 建议应用并行度 */ Integer appParallelismDegree,
            /* Host 并发限制 */ Integer perHostConcurrencyLimit,
            /* HTTP 连接池大小 */ Integer httpConnPoolSize,
            /* 背压策略编码 */ String backpressureStrategyCode,
            /* 请求模板 JSON */ String requestTemplateJson
    ) {
    }

    /**
     * 重试配置。
     */
    public record RetryConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 最大重试次数 */ Integer maxRetryTimes,
            /* 退避策略类型 */ String backoffPolicyTypeCode,
            /* 初始延迟毫秒 */ Integer initialDelayMillis,
            /* 最大延迟毫秒 */ Integer maxDelayMillis,
            /* 指数倍率 */ Double expMultiplierValue,
            /* 抖动因子 */ Double jitterFactorRatio,
            /* 重试状态码 JSON */ String retryHttpStatusJson,
            /* 放弃状态码 JSON */ String giveupHttpStatusJson,
            /* 网络错误是否重试 */ boolean retryOnNetworkError,
            /* 熔断阈值 */ Integer circuitBreakThreshold,
            /* 熔断冷却毫秒 */ Integer circuitCooldownMillis
    ) {
    }

    /**
     * 限流配置。
     */
    public record RateLimitConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 每秒令牌速率 */ Integer rateTokensPerSecond,
            /* 突发桶容量 */ Integer burstBucketCapacity,
            /* 最大并发请求数 */ Integer maxConcurrentRequests,
            /* 单凭证 QPS 限制 */ Integer perCredentialQpsLimit,
            /* 桶粒度作用域编码 */ String bucketGranularityScopeCode,
            /* 平滑窗口毫秒 */ Integer smoothingWindowMillis,
            /* 是否遵守服务端速率头 */ boolean respectServerRateHeader,
            /* 绑定端点 ID */ Long endpointId,
            /* 绑定凭证名 */ String credentialName
    ) {
    }

    /**
     * 凭证配置。
     */
    public record CredentialConfig(
            /* 主键 ID */ Long id,
            /* 来源 ID */ Long provenanceId,
            /* 作用域编码 */ String scopeCode,
            /* 任务类型 */ String taskType,
            /* 任务子键 */ String taskTypeKey,
            /* 绑定端点 ID（可空） */ Long endpointId,
            /* 凭证名 */ String credentialName,
            /* 认证类型 */ String authType,
            /* 注入位置编码 */ String inboundLocationCode,
            /* 注入字段名 */ String credentialFieldName,
            /* 值前缀（例如 Bearer ） */ String credentialValuePrefix,
            /* 密钥值引用 */ String credentialValueRef,
            /* BASIC 用户名引用 */ String basicUsernameRef,
            /* BASIC 密码引用 */ String basicPasswordRef,
            /* OAuth token URL */ String oauthTokenUrl,
            /* OAuth clientId 引用 */ String oauthClientIdRef,
            /* OAuth clientSecret 引用 */ String oauthClientSecretRef,
            /* OAuth scope */ String oauthScope,
            /* OAuth audience */ String oauthAudience,
            /* 额外扩展 JSON */ String extraJson,
            /* 生效起 */ Instant effectiveFrom,
            /* 生效止（不含） */ Instant effectiveTo,
            /* 是否默认优先 */ boolean defaultPreferred,
            /* 生命周期状态编码 */ String lifecycleStatusCode
    ) {
    }
}
