package com.patra.ingest.domain.model.snapshot;

import java.time.Instant;

/**
 * Provenance 配置聚合快照 Value Object。
 *
 * <p>将注册中心多个子域表的配置合并为单一的 Immutable 视图,确保调度器执行时观察到一致、可重放的配置。
 *
 * <p><b>业务价值:</b>
 *
 * <ul>
 *   <li>配置时点快照 - 捕获某一时刻的完整配置状态
 *   <li>可重放性 - 支持历史配置回放和故障排查
 *   <li>一致性保证 - 避免执行过程中配置变更导致的不一致
 * </ul>
 *
 * <p><b>选择规则:</b> 对于每个维度,选择满足以下条件的最新行:
 *
 * <ul>
 *   <li>{@code NOW()} 落在 {@code [effective_from, effective_to)} 时间窗口内
 *   <li>{@code lifecycle=ACTIVE} 且 {@code deleted=0}
 *   <li>按 {@code effective_from DESC, id DESC} 排序后取第一行
 *   <li>凭证维度可能返回多行(已从快照中移除)
 * </ul>
 *
 * <p><b>包含的维度:</b> (表 → 嵌套 record)
 *
 * <ul>
 *   <li>reg_provenance → ProvenanceInfo
 *   <li>reg_prov_window_offset_cfg → WindowOffsetConfig
 *   <li>reg_prov_pagination_cfg → PaginationConfig
 *   <li>reg_prov_http_cfg → HttpConfig
 *   <li>reg_prov_batching_cfg → BatchingConfig
 *   <li>reg_prov_retry_cfg → RetryConfig
 *   <li>reg_prov_rate_limit_cfg → RateLimitConfig
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigSnapshot(
    /* 数据源元数据 */ ProvenanceInfo provenance,
    /* 时间窗口/增量偏移配置(可为 null) */ WindowOffsetConfig windowOffset,
    /* 分页/游标配置(可为 null) */ PaginationConfig pagination,
    /* HTTP 策略配置(可为 null) */ HttpConfig http,
    /* 批处理/请求塑形配置(可为 null) */ BatchingConfig batching,
    /* 重试和退避配置(可为 null) */ RetryConfig retry,
    /* 速率限制和并发配置(可为 null) */ RateLimitConfig rateLimit) {

  /**
   * Provenance 元数据 (reg_provenance)。
   *
   * <p>字典: lifecycle_status = DRAFT|ACTIVE|DEPRECATED|RETIRED
   */
  public record ProvenanceInfo(
      /* 主键 ID */ Long id,
      /* Provenance 代码(全局唯一,例如 pubmed/crossref) */ String code,
      /* 人类可读的 Provenance 名称 */ String name,
      /* 默认基础 URL(当 HTTP 配置未覆盖时使用) */ String baseUrlDefault,
      /* 默认时区(IANA,例如 UTC/Asia/Shanghai) */ String timezoneDefault,
      /* 文档/官方 URL(用于调试参考) */ String docsUrl,
      /* 激活标识(快速开关) */ boolean active,
      /* 生命周期状态(lifecycle_status: DRAFT|ACTIVE|DEPRECATED|RETIRED) */ String lifecycleStatusCode) {}

  /**
   * 窗口与偏移配置 (reg_prov_window_offset_cfg)。
   *
   * <p>字典: window_mode = SLIDING|CALENDAR; time_unit = SECOND|MINUTE|HOUR|DAY; offset_type =
   * DATE|ID|COMPOSITE; lifecycle_status 同上。
   */
  public record WindowOffsetConfig(
      /* 主键 ID */ Long id,
      /* Provenance ID */ Long provenanceId,
      /* 操作类型(可为 null) */ String operationType,
      /* 生效起始时间(包含) */ Instant effectiveFrom,
      /* 生效结束时间(不包含;NULL=长期有效) */ Instant effectiveTo,
      /* 窗口模式(window_mode: SLIDING|CALENDAR) */ String windowModeCode,
      /* 窗口长度值(例如 1/7/30) */ Integer windowSizeValue,
      /* 窗口长度单位(time_unit: SECOND|MINUTE|HOUR|DAY) */ String windowSizeUnitCode,
      /* CALENDAR 对齐粒度(HOUR|DAY|WEEK|MONTH,可为 null) */ String calendarAlignTo,
      /* 回溯长度值(补偿延迟数据) */ Integer lookbackValue,
      /* 回溯长度单位(time_unit) */ String lookbackUnitCode,
      /* 窗口重叠长度值(晚到数据覆盖) */ Integer overlapValue,
      /* 窗口重叠单位(time_unit) */ String overlapUnitCode,
      /* 水位线滞后秒数(允许乱序延迟) */ Integer watermarkLagSeconds,
      /* 偏移类型(offset_type: DATE|ID|COMPOSITE) */ String offsetTypeCode,
      /* 统一字段键(std_key,按偏移类型解释) */ String offsetFieldKey,
      /* DATE 偏移格式(例如 ISO_INSTANT/epochMillis/yyyyMMdd) */ String offsetDateFormat,
      /* 统一日期字段键(std_key,当存在多个候选时) */ String windowDateFieldKey,
      /* 每个窗口最大 ID 数(超过则重新拆分) */ Integer maxIdsPerWindow,
      /* 窗口最大跨度秒数(超过则强制拆分) */ Integer maxWindowSpanSeconds) {}

  /**
   * 分页 / 游标 / 令牌 / 滚动配置 (reg_prov_pagination_cfg)。
   *
   * <p>字典: pagination_mode = PAGE_NUMBER|CURSOR|TOKEN|SCROLL; lifecycle_status 同上。
   */
  public record PaginationConfig(
      /* 主键 ID */ Long id,
      /* Provenance ID */ Long provenanceId,
      /* 操作类型(可为 null) */ String operationType,
      /* 生效起始时间(包含) */ Instant effectiveFrom,
      /* 生效结束时间(不包含;NULL=长期有效) */ Instant effectiveTo,
      /* 分页模式(pagination_mode: PAGE_NUMBER|CURSOR|TOKEN|SCROLL) */ String paginationModeCode,
      /* 页大小值(PAGE_NUMBER/SCROLL 模式,NULL=应用默认) */ Integer pageSizeValue,
      /* 每次执行最大页数(NULL=无限制或上层控制) */ Integer maxPagesPerExecution,
      /* 排序字段参数名(例如 sort) */ String sortFieldParamName,
      /* 排序方向(1=ASC, 0=DESC) */ Integer sortingDirection) {}

  /**
   * HTTP 策略配置 (reg_prov_http_cfg)。
   *
   * <p>字典: retry_after_policy = IGNORE|RESPECT|CLAMP; lifecycle_status 同上。
   */
  public record HttpConfig(
      /* 主键 ID */ Long id,
      /* Provenance ID */ Long provenanceId,
      /* 操作类型(可为 null) */ String operationType,
      /* 生效起始时间(包含) */ Instant effectiveFrom,
      /* 生效结束时间(不包含;NULL=长期有效) */ Instant effectiveTo,
      /* 默认 Headers JSON(运行时合并) */ String defaultHeadersJson,
      /* 连接超时毫秒数 */ Integer timeoutConnectMillis,
      /* 读取超时毫秒数 */ Integer timeoutReadMillis,
      /* 总超时毫秒数(整体请求上限) */ Integer timeoutTotalMillis,
      /* 是否验证 TLS 证书 */ boolean tlsVerifyEnabled,
      /* 代理地址(支持 http(s)/socks5) */ String proxyUrlValue,
      /* Retry-After 处理策略(retry_after_policy: IGNORE|RESPECT|CLAMP) */ String retryAfterPolicyCode,
      /* Retry-After 最大等待上限毫秒数(CLAMP/RESPECT) */ Integer retryAfterCapMillis,
      /* 幂等性 Header 名称(例如 Idempotency-Key) */ String idempotencyHeaderName,
      /* 幂等性键 TTL 秒数(如果客户端/服务器支持) */ Integer idempotencyTtlSeconds) {}

  /**
   * 批处理与请求塑形配置 (reg_prov_batching_cfg)。
   *
   * <p>字典: lifecycle_status 同上。
   */
  public record BatchingConfig(
      /* 主键 ID */ Long id,
      /* Provenance ID */ Long provenanceId,
      /* 操作类型(可为 null) */ String operationType,
      /* 生效起始时间(包含) */ Instant effectiveFrom,
      /* 生效结束时间(不包含;NULL=长期有效) */ Instant effectiveTo,
      /* 详情获取批次大小(NULL=应用默认) */ Integer detailFetchBatchSize,
      /* IDs 参数名称(可为 null) */ String idsParamName,
      /* IDs 连接分隔符(默认逗号) */ String idsJoinDelimiter,
      /* 每个请求最大 IDs 数量(硬限制) */ Integer maxIdsPerRequest) {}

  /**
   * 重试与退避配置 (reg_prov_retry_cfg)。
   *
   * <p>字典: scope = SOURCE|TASK; backoff_policy_type = FIXED|EXP|EXP_JITTER|DECOR_JITTER;
   * lifecycle_status 同上。
   */
  public record RetryConfig(
      /* 主键 ID */ Long id,
      /* Provenance ID */ Long provenanceId,
      /* 操作类型区分符(可为 null) */ String operationType,
      /* 生效起始时间(包含) */ Instant effectiveFrom,
      /* 生效结束时间(不包含;NULL=长期有效) */ Instant effectiveTo,
      /* 最大重试次数(NULL=默认值; 0=禁用) */ Integer maxRetryTimes,
      /* 退避策略(FIXED|EXP|EXP_JITTER|DECOR_JITTER) */ String backoffPolicyTypeCode,
      /* 初始延迟毫秒数(首次重试) */ Integer initialDelayMillis,
      /* 每次重试最大延迟毫秒数 */ Integer maxDelayMillis,
      /* 指数乘数(EXP 系列) */ Double expMultiplierValue,
      /* 抖动因子比率(0~1) */ Double jitterFactorRatio,
      /* 重试 HTTP 状态列表 JSON */ String retryHttpStatusJson,
      /* 放弃 HTTP 状态列表 JSON */ String giveupHttpStatusJson,
      /* 网络错误时重试 */ boolean retryOnNetworkError,
      /* 熔断器阈值(连续失败次数) */ Integer circuitBreakThreshold,
      /* 熔断器冷却毫秒数 */ Integer circuitCooldownMillis) {}

  /**
   * 速率限制与并发配置 (reg_prov_rate_limit_cfg)。
   *
   * <p>字典: lifecycle_status 同上。
   */
  public record RateLimitConfig(
      /* 主键 ID */ Long id,
      /* Provenance ID */ Long provenanceId,
      /* 操作类型区分符(可为 null) */ String operationType,
      /* 生效起始时间(包含) */ Instant effectiveFrom,
      /* 生效结束时间(不包含;NULL=长期有效) */ Instant effectiveTo,
      /* 最大并发 HTTP 请求数(NULL=引擎默认) */ Integer maxConcurrentRequests,
      /* 每个凭证 QPS 上限(可为 null) */ Integer perCredentialQpsLimit) {}

  // 凭证维度已从快照中移除。
}
