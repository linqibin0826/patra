/**
 * Provenance 配置模型包。
 *
 * <p>定义数据源配置记录和配置提供者，封装 HTTP 客户端设置、分页参数、时间窗口、
 * 批处理、重试策略和限流配置。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装单个数据源的完整配置信息
 *   <li>提供配置合并和默认值填充
 *   <li>支持从 Spring Boot 配置属性中读取配置
 *   <li>验证配置完整性和有效性
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ProvenanceConfig} - 数据源配置记录
 *   <li>{@link DefaultConfigProvider} - 默认配置提供者
 *   <li>{@link HttpConfig} - HTTP 客户端配置
 *   <li>{@link PaginationConfig} - 分页配置
 *   <li>{@link WindowOffsetConfig} - 时间窗口配置
 *   <li>{@link BatchingConfig} - 批处理配置
 *   <li>{@link RetryConfig} - 重试策略配置
 *   <li>{@link RateLimitConfig} - 限流配置
 * </ul>
 *
 * <h2>配置层级</h2>
 *
 * <pre>
 * ProvenanceConfig (数据源配置)
 * ├── baseUrl (必需)
 * ├── HttpConfig (HTTP 设置)
 * │   ├── headers (默认请求头)
 * │   ├── connectTimeout
 * │   ├── readTimeout
 * │   └── writeTimeout
 * ├── PaginationConfig (分页)
 * │   ├── pageSize
 * │   └── maxResults
 * ├── WindowOffsetConfig (时间窗口)
 * │   └── windowSize
 * ├── BatchingConfig (批处理)
 * │   └── batchSize
 * ├── RetryConfig (重试)
 * │   ├── maxAttempts
 * │   └── backoff
 * └── RateLimitConfig (限流)
 *     ├── requestsPerSecond
 *     └── burstCapacity
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 从配置提供者获取配置
 * DefaultConfigProvider provider = new DefaultConfigProvider(properties);
 * ProvenanceConfig config = provider.getConfig("pubmed");
 *
 * // 使用配置
 * String baseUrl = config.baseUrl();
 * int pageSize = config.pagination().pageSize();
 * int timeout = config.http().connectTimeout();
 * }</pre>
 *
 * <h2>配置合并策略</h2>
 *
 * <p>支持三层配置合并（优先级从高到低）：
 *
 * <ol>
 *   <li>运行时传入的配置覆盖
 *   <li>数据源特定配置（如 {@code patra.provenance.pubmed.*}）
 *   <li>全局默认配置
 * </ol>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.config;
