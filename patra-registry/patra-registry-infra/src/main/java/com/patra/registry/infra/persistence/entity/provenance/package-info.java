/// 数据源实体包 - Provenance 相关数据库实体。
/// 
/// 本包包含数据源(Provenance)及其运营配置的数据库实体对象,映射 `reg_prov_*` 系列表。所有配置实体支持时态特性,通过 `effective_from` 和 `effective_until` 字段实现配置的时间有效性管理。
/// 
/// ## 职责
/// 
/// - 映射数据源元数据表(`reg_provenance`)
///   - 映射运营配置表(HTTP、分页、重试、速率限制等)
///   - 支持时态查询和配置版本管理
///   - 支持配置作用域层次(SOURCE/TASK)
/// 
/// ## 核心实体
/// 
/// - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO} - 数据源元数据
///       
/// - 表: `reg_provenance`
///         - 字段: `provenance_code`, `provenance_name`, `default_base_url`,
///             `is_active`
/// 
///   - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO} -
///       时间窗口偏移配置
///       
/// - 表: `reg_prov_window_offset_cfg`
///         - 时态字段: `effective_from`, `effective_until`
/// 
///   - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO} -
///       分页配置
///       
/// - 表: `reg_prov_pagination_cfg`
///         - 策略: `cursor_based`, `page_based`
/// 
///   - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO} - HTTP 配置
///       
/// - 表: `reg_prov_http_cfg`
///         - 配置: 超时、自定义头、用户代理等
/// 
///   - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO} - 批处理配置
///       
/// - 表: `reg_prov_batching_cfg`
///         - 配置: 批大小、并发数、延迟等
/// 
///   - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO} - 重试配置
///       
/// - 表: `reg_prov_retry_cfg`
///         - 策略: 最大重试次数、退避策略、可重试状态码
/// 
///   - {@link com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO} -
///       速率限制配置
///       
/// - 表: `reg_prov_rate_limit_cfg`
///         - 配置: 请求速率、时间窗口、并发限制
/// 
/// ## 时态配置模型
/// 
/// 所有运营配置实体包含以下时态字段:
/// 
/// ```java
/// @TableField("effective_from")
/// private Instant effectiveFrom;  // 配置生效时间
/// 
/// @TableField("effective_until")
/// private Instant effectiveUntil; // 配置失效时间(NULL 表示永久有效)
/// ```
/// 
/// ## 配置作用域模型
/// 
/// 所有运营配置实体包含以下作用域字段:
/// 
/// ```java
/// @TableField("scope_key")
/// private String scopeKey;  // 作用域键,格式: "SOURCE" 或 "TASK:{operationType"
/// ```
/// 
/// ## 数据库表设计
/// 
/// <table border="1">
///   <caption>数据源配置表</caption>
///   <tr>
///     <th>表名</th>
///     <th>用途</th>
///     <th>关键字段</th>
///   </tr>
///   <tr>
///     <td>`reg_provenance`</td>
///     <td>数据源元数据</td>
///     <td>`provenance_code`, `is_active`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_window_offset_cfg`</td>
///     <td>时间窗口偏移</td>
///     <td>`scope_key`, `effective_from/until`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_pagination_cfg`</td>
///     <td>分页策略</td>
///     <td>`cursor_based`, `page_based`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_http_cfg`</td>
///     <td>HTTP 客户端设置</td>
///     <td>`timeout_seconds`, `custom_headers`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_batching_cfg`</td>
///     <td>批处理规则</td>
///     <td>`batch_size`, `max_concurrent_batches`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_retry_cfg`</td>
///     <td>重试策略</td>
///     <td>`max_retries`, `backoff_strategy`</td>
///   </tr>
///   <tr>
///     <td>`reg_prov_rate_limit_cfg`</td>
///     <td>速率限制</td>
///     <td>`requests_per_second`, `max_concurrent_requests`</td>
///   </tr>
/// </table>
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.entity.provenance;
