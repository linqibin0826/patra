package com.patra.ingest.domain.model.snapshot;

import java.time.Instant;
import java.util.List;

/**
 * 来源配置聚合快照（Domain Snapshot）。
 * <p>聚合 Registry 子域多维度配置于单一不可变视图，保证单次调度/执行期内配置一致性与可重放。</p>
 * <p>时间片选择规则统一：同维度按 NOW() 命中 [effective_from, effective_to) 且 lifecycle=ACTIVE 且 deleted=0 的最新一条（按 effective_from DESC,id DESC）。
 * 凭证维度可多条。</p>
 *
 * <p>包含的维度（表 → 领域嵌套 record）：
 * reg_provenance → ProvenanceInfo；reg_prov_endpoint_def → EndpointDefinition；reg_prov_window_offset_cfg → WindowOffsetConfig；
 * reg_prov_pagination_cfg → PaginationConfig；reg_prov_http_cfg → HttpConfig；reg_prov_batching_cfg → BatchingConfig；
 * reg_prov_retry_cfg → RetryConfig；reg_prov_rate_limit_cfg → RateLimitConfig；reg_prov_credential → CredentialConfig。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigSnapshot(
        /* 来源基础信息 */ ProvenanceInfo provenance,
        /* 端点定义（可空） */ EndpointDefinition endpoint,
        /* 时间窗口 / 增量指针（可空） */ WindowOffsetConfig windowOffset,
        /* 分页 / 游标（可空） */ PaginationConfig pagination,
        /* HTTP 策略（可空） */ HttpConfig http,
        /* 批量 / 请求成型（可空） */ BatchingConfig batching,
        /* 重试与退避（可空） */ RetryConfig retry,
        /* 限流与并发（可空） */ RateLimitConfig rateLimit,
        /* 凭证集合（可空列表，可多条） */ List<CredentialConfig> credentials
) {

    /**
     * 来源基础信息（reg_provenance）。
     * 字典：lifecycle_status = DRAFT|ACTIVE|DEPRECATED|RETIRED
     */
    public record ProvenanceInfo(
            /* 主键ID */ Long id,
            /* 来源编码（全局唯一稳定；如 pubmed / crossref） */ String code,
            /* 来源名称（人类可读） */ String name,
            /* 默认基础URL（端点 path 拼接基线，HTTP 配置未覆盖时生效） */ String baseUrlDefault,
            /* 默认时区（IANA，如 UTC/Asia/Shanghai） */ String timezoneDefault,
            /* 官方/文档 URL（调试引用） */ String docsUrl,
            /* 是否启用（快速开关） */ boolean active,
            /* 生命周期状态 (lifecycle_status: DRAFT|ACTIVE|DEPRECATED|RETIRED) */ String lifecycleStatusCode
    ) {
    }

    /**
     * 端点定义（reg_prov_endpoint_def）。
     * 字典：scope = SOURCE|TASK；endpoint_usage = SEARCH|DETAIL|BATCH|AUTH|HEALTH；http_method = GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS；lifecycle_status 同上。
     * 端点级 page/cursor/ids 参数存在时覆盖同维度 Pagination/Batching 配置。
     */
    public record EndpointDefinition(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（scope=TASK 时必填；文本去枚举化） */ String taskType,
            /* 标准化任务键（生成列：NULL→ALL） */ String taskTypeKey,
            /* 端点逻辑名称（如 search / detail / works / token） */ String endpointName,
            /* 端点用途 (endpoint_usage: SEARCH|DETAIL|BATCH|AUTH|HEALTH) */ String endpointUsageCode,
            /* HTTP 方法 (http_method: GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS) */ String httpMethodCode,
            /* 路径模板（相对或绝对；可含占位符） */ String pathTemplate,
            /* 默认查询参数 JSON（运行时基础合并） */ String defaultQueryParamsJson,
            /* 默认请求体 JSON（运行时基础合并） */ String defaultBodyPayloadJson,
            /* 请求内容类型（如 application/json） */ String requestContentType,
            /* 是否需要鉴权 */ boolean authRequired,
            /* 凭证提示名（多凭证选择辅助） */ String credentialHintName,
            /* 分页：页号参数名（覆盖项，可空） */ String pageParamName,
            /* 分页：页大小参数名（覆盖项，可空） */ String pageSizeParamName,
            /* 游标/令牌参数名（覆盖项，可空） */ String cursorParamName,
            /* 批量：ID 列表参数名（覆盖项，可空） */ String idsParamName,
            /* 生效起（含，UTC） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo
    ) {
    }

    /**
     * 时间窗口与增量指针（reg_prov_window_offset_cfg）。
     * 字典：scope = SOURCE|TASK；window_mode = SLIDING|CALENDAR；time_unit = SECOND|MINUTE|HOUR|DAY；offset_type = DATE|ID|COMPOSITE；lifecycle_status 同上。
     */
    public record WindowOffsetConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 窗口模式 (window_mode: SLIDING|CALENDAR) */ String windowModeCode,
            /* 窗口长度数值（示例 1/7/30） */ Integer windowSizeValue,
            /* 窗口长度单位 (time_unit: SECOND|MINUTE|HOUR|DAY) */ String windowSizeUnitCode,
            /* CALENDAR 对齐粒度（HOUR|DAY|WEEK|MONTH，可空） */ String calendarAlignTo,
            /* 回看长度数值（补偿延迟数据） */ Integer lookbackValue,
            /* 回看长度单位 (time_unit) */ String lookbackUnitCode,
            /* 窗口重叠长度数值（迟到兜底） */ Integer overlapValue,
            /* 窗口重叠单位 (time_unit) */ String overlapUnitCode,
            /* 水位滞后秒（乱序允许延迟） */ Integer watermarkLagSeconds,
            /* 指针类型 (offset_type: DATE|ID|COMPOSITE) */ String offsetTypeCode,
            /* 指针字段或 JSONPath（依据指针类型解释） */ String offsetFieldName,
            /* DATE 指针格式（如 ISO_INSTANT/epochMillis/yyyyMMdd） */ String offsetDateFormat,
            /* 默认增量日期字段（多日期候选时） */ String defaultDateFieldName,
            /* 单窗口最大 ID 数（超出可能二次切窗） */ Integer maxIdsPerWindow,
            /* 单窗口最大跨度秒（超出强制切分） */ Integer maxWindowSpanSeconds
    ) {
    }

    /**
     * 分页 / 游标 / 令牌 / 滚动分页（reg_prov_pagination_cfg）。
     * 字典：scope = SOURCE|TASK；pagination_mode = PAGE_NUMBER|CURSOR|TOKEN|SCROLL；lifecycle_status 同上。
     */
    public record PaginationConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 分页模式 (pagination_mode: PAGE_NUMBER|CURSOR|TOKEN|SCROLL) */ String paginationModeCode,
            /* 每页大小（PAGE_NUMBER/SCROLL 模式常用，NULL=应用默认） */ Integer pageSizeValue,
            /* 单次执行最大翻页数（NULL=不限制或上层控制） */ Integer maxPagesPerExecution,
            /* 页码参数名（如 page） */ String pageNumberParamName,
            /* 每页大小参数名（如 pageSize / rows） */ String pageSizeParamName,
            /* 起始页码（PAGE_NUMBER 起点，默认常用 1） */ Integer startPageNumber,
            /* 排序字段参数名（如 sort） */ String sortFieldParamName,
            /* 排序方向 (ASC|DESC) */ String sortDirection,
            /* 游标/令牌参数名（如 cursor / next_token） */ String cursorParamName,
            /* 初始游标值（可空：运行时确定） */ String initialCursorValue,
            /* 提取下一页游标的 JSONPath/JMESPath */ String nextCursorJsonpath,
            /* 判断是否还有下一页的 JSONPath（布尔） */ String hasMoreJsonpath,
            /* 提取总条数的 JSONPath（可选） */ String totalCountJsonpath,
            /* 提取下一页游标的 XPath（XML，可选） */ String nextCursorXpath,
            /* 判断是否还有下一页的 XPath（布尔，可选） */ String hasMoreXpath,
            /* 提取总条数的 XPath（可选） */ String totalCountXpath
    ) {
    }

    /**
     * HTTP 策略（reg_prov_http_cfg）。
     * 字典：scope = SOURCE|TASK；retry_after_policy = IGNORE|RESPECT|CLAMP；lifecycle_status 同上。
     */
    public record HttpConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 基础URL覆盖（不为空时覆盖来源默认 baseUrl） */ String baseUrlOverride,
            /* 默认 Headers JSON（运行时合并） */ String defaultHeadersJson,
            /* 连接超时毫秒 */ Integer timeoutConnectMillis,
            /* 读取超时毫秒 */ Integer timeoutReadMillis,
            /* 总超时毫秒（请求整体上限） */ Integer timeoutTotalMillis,
            /* 是否校验 TLS 证书 */ boolean tlsVerifyEnabled,
            /* 代理地址（支持 http(s)/socks5） */ String proxyUrlValue,
            /* 是否接受压缩（gzip/deflate/br 等） */ boolean acceptCompressEnabled,
            /* 是否优先 HTTP/2 */ boolean preferHttp2Enabled,
            /* Retry-After 处理策略 (retry_after_policy: IGNORE|RESPECT|CLAMP) */ String retryAfterPolicyCode,
            /* Retry-After 最大等待上限毫秒（CLAMP/RESPECT 时可用） */ Integer retryAfterCapMillis,
            /* 幂等性 Header 名（如 Idempotency-Key） */ String idempotencyHeaderName,
            /* 幂等键 TTL 秒（客户端/服务端支持时生效） */ Integer idempotencyTtlSeconds
    ) {
    }

    /**
     * 批量抓取与请求成型（reg_prov_batching_cfg）。
     * 字典：scope = SOURCE|TASK；payload_compress_strategy = NONE|GZIP；backpressure_strategy = BLOCK|DROP|YIELD；lifecycle_status 同上。
     */
    public record BatchingConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 详情抓取批大小（NULL=应用默认） */ Integer detailFetchBatchSize,
            /* 绑定端点ID（可空） */ Long endpointId,
            /* 指定凭证名（可空） */ String credentialName,
            /* ID 参数名（可空） */ String idsParamName,
            /* ID 拼接分隔符（默认 ,） */ String idsJoinDelimiter,
            /* 单请求最大 ID 数（硬上限） */ Integer maxIdsPerRequest,
            /* 是否偏好紧凑负载（压缩/去冗余） */ boolean preferCompactPayload,
            /* 负载压缩策略 (payload_compress_strategy: NONE|GZIP) */ String payloadCompressStrategyCode,
            /* 建议应用并行度 */ Integer appParallelismDegree,
            /* 每主机并发限制 */ Integer perHostConcurrencyLimit,
            /* HTTP 连接池大小 */ Integer httpConnPoolSize,
            /* 背压策略 (backpressure_strategy: BLOCK|DROP|YIELD) */ String backpressureStrategyCode,
            /* 请求模板 JSON（含占位符） */ String requestTemplateJson
    ) {
    }

    /**
     * 重试与退避（reg_prov_retry_cfg）。
     * 字典：scope = SOURCE|TASK；backoff_policy_type = FIXED|EXP|EXP_JITTER|DECOR_JITTER；lifecycle_status 同上。
     */
    public record RetryConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 最大重试次数（NULL=默认；0=不重试） */ Integer maxRetryTimes,
            /* 退避策略 (backoff_policy_type: FIXED|EXP|EXP_JITTER|DECOR_JITTER) */ String backoffPolicyTypeCode,
            /* 初始延迟毫秒（首个��试） */ Integer initialDelayMillis,
            /* 单次重试最大延迟毫秒 */ Integer maxDelayMillis,
            /* 指数倍率（EXP 系列） */ Double expMultiplierValue,
            /* 抖动因子（0~1） */ Double jitterFactorRatio,
            /* 可重试 HTTP 状态码 JSON 数组 */ String retryHttpStatusJson,
            /* 放弃 HTTP 状态码 JSON 数组 */ String giveupHttpStatusJson,
            /* 网络错误是否重试 */ boolean retryOnNetworkError,
            /* 断路器阈值（连续失败次数） */ Integer circuitBreakThreshold,
            /* 断路器冷却毫秒（半开前等待） */ Integer circuitCooldownMillis
    ) {
    }

    /**
     * 限流与并发（reg_prov_rate_limit_cfg）。
     * 字典：scope = SOURCE|TASK；bucket_granularity_scope = GLOBAL|PER_KEY|PER_ENDPOINT|PER_IP|PER_TASK；lifecycle_status 同上。
     */
    public record RateLimitConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 每秒令牌速率（QPS；NULL=默认/不限） */ Integer rateTokensPerSecond,
            /* 突发桶容量（瞬时峰值缓冲） */ Integer burstBucketCapacity,
            /* 最大并发请求数（NULL=默认） */ Integer maxConcurrentRequests,
            /* 单凭证 QPS 上限（多凭证分摊） */ Integer perCredentialQpsLimit,
            /* 令牌桶粒度 (bucket_granularity_scope: GLOBAL|PER_KEY|PER_ENDPOINT|PER_IP|PER_TASK) */
                         String bucketGranularityScopeCode,
            /* 平滑窗口毫秒（发放/统计平滑） */ Integer smoothingWindowMillis,
            /* 是否遵守服务端 Rate 头 (Retry-After / X-RateLimit-*) */ boolean respectServerRateHeader,
            /* 绑定端点ID（可空） */ Long endpointId,
            /* 绑定凭证名（可空） */ String credentialName
    ) {
    }

    /**
     * 凭证配置（reg_prov_credential）。
     * 字典：scope = SOURCE|TASK；inbound_location = HEADER|QUERY|BODY；lifecycle_status = DRAFT|ACTIVE|DEPRECATED|RETIRED。
     * auth_type 示例：API_KEY / BASIC / OAUTH2_CLIENT_CREDENTIALS（可扩展）��
     */
    public record CredentialConfig(
            /* 主键ID */ Long id,
            /* 来源ID */ Long provenanceId,
            /* 作用域 (scope: SOURCE|TASK) */ String scopeCode,
            /* 任务类型（可空） */ String taskType,
            /* 标准化任务键（NULL→ALL） */ String taskTypeKey,
            /* 绑定端点ID（可空） */ Long endpointId,
            /* 凭证名称（选择标签） */ String credentialName,
            /* 认证类型（API_KEY/BASIC/OAUTH2_CLIENT_CREDENTIALS 等） */ String authType,
            /* 注入位置 (inbound_location: HEADER|QUERY|BODY) */ String inboundLocationCode,
            /* 注入字段名 */ String credentialFieldName,
            /* 值前缀（如 Bearer） */ String credentialValuePrefix,
            /* 密钥值引用（外部配置中心 Key） */ String credentialValueRef,
            /* BASIC 用户名引用 */ String basicUsernameRef,
            /* BASIC 密码引用 */ String basicPasswordRef,
            /* OAuth Token URL */ String oauthTokenUrl,
            /* OAuth clientId 引用 */ String oauthClientIdRef,
            /* OAuth clientSecret 引用 */ String oauthClientSecretRef,
            /* OAuth scope */ String oauthScope,
            /* OAuth audience */ String oauthAudience,
            /* 额外扩展 JSON */ String extraJson,
            /* 生效起（含） */ Instant effectiveFrom,
            /* 生效止（不含；NULL=长期） */ Instant effectiveTo,
            /* 是否默认优先 */ boolean defaultPreferred,
            /* 生命周期状态 (lifecycle_status: DRAFT|ACTIVE|DEPRECATED|RETIRED) */ String lifecycleStatusCode
    ) {
    }
}
