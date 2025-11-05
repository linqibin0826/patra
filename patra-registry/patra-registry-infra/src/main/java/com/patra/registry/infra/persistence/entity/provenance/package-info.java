/**
 * 数据源实体包 - Provenance 相关数据库实体。
 *
 * <p>本包包含数据源(Provenance)及其运营配置的数据库实体对象,映射 {@code reg_prov_*} 系列表。所有配置实体支持时态特性,通过 {@code effective_from} 和 {@code effective_until} 字段实现配置的时间有效性管理。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>映射数据源元数据表({@code reg_provenance})
 *   <li>映射运营配置表(HTTP、分页、重试、速率限制等)
 *   <li>支持时态查询和配置版本管理
 *   <li>支持配置作用域层次(SOURCE/TASK)
 * </ul>
 *
 * <h2>核心实体</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO} - 数据源元数据
 *       <ul>
 *         <li>表: {@code reg_provenance}</li>
 *         <li>字段: {@code provenance_code}, {@code provenance_name}, {@code default_base_url}, {@code is_active}</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO} - 时间窗口偏移配置
 *       <ul>
 *         <li>表: {@code reg_prov_window_offset_cfg}</li>
 *         <li>时态字段: {@code effective_from}, {@code effective_until}</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO} - 分页配置
 *       <ul>
 *         <li>表: {@code reg_prov_pagination_cfg}</li>
 *         <li>策略: {@code cursor_based}, {@code page_based}</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO} - HTTP 配置
 *       <ul>
 *         <li>表: {@code reg_prov_http_cfg}</li>
 *         <li>配置: 超时、自定义头、用户代理等</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO} - 批处理配置
 *       <ul>
 *         <li>表: {@code reg_prov_batching_cfg}</li>
 *         <li>配置: 批大小、并发数、延迟等</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO} - 重试配置
 *       <ul>
 *         <li>表: {@code reg_prov_retry_cfg}</li>
 *         <li>策略: 最大重试次数、退避策略、可重试状态码</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO} - 速率限制配置
 *       <ul>
 *         <li>表: {@code reg_prov_rate_limit_cfg}</li>
 *         <li>配置: 请求速率、时间窗口、并发限制</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>时态配置模型</h2>
 *
 * <p>所有运营配置实体包含以下时态字段:
 *
 * <pre>{@code
 * @TableField("effective_from")
 * private Instant effectiveFrom;  // 配置生效时间
 *
 * @TableField("effective_until")
 * private Instant effectiveUntil; // 配置失效时间(NULL 表示永久有效)
 * }</pre>
 *
 * <h2>配置作用域模型</h2>
 *
 * <p>所有运营配置实体包含以下作用域字段:
 *
 * <pre>{@code
 * @TableField("scope_key")
 * private String scopeKey;  // 作用域键,格式: "SOURCE" 或 "TASK:{operationType}"
 * }</pre>
 *
 * <h2>数据库表设计</h2>
 *
 * <table border="1">
 *   <caption>数据源配置表</caption>
 *   <tr>
 *     <th>表名</th>
 *     <th>用途</th>
 *     <th>关键字段</th>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_provenance}</td>
 *     <td>数据源元数据</td>
 *     <td>{@code provenance_code}, {@code is_active}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_window_offset_cfg}</td>
 *     <td>时间窗口偏移</td>
 *     <td>{@code scope_key}, {@code effective_from/until}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_pagination_cfg}</td>
 *     <td>分页策略</td>
 *     <td>{@code cursor_based}, {@code page_based}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_http_cfg}</td>
 *     <td>HTTP 客户端设置</td>
 *     <td>{@code timeout_seconds}, {@code custom_headers}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_batching_cfg}</td>
 *     <td>批处理规则</td>
 *     <td>{@code batch_size}, {@code max_concurrent_batches}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_retry_cfg}</td>
 *     <td>重试策略</td>
 *     <td>{@code max_retries}, {@code backoff_strategy}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code reg_prov_rate_limit_cfg}</td>
 *     <td>速率限制</td>
 *     <td>{@code requests_per_second}, {@code max_concurrent_requests}</td>
 *   </tr>
 * </table>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.entity.provenance;
