/// Provenance 配置模型包。
///
/// 定义数据源配置记录和配置提供者，封装 HTTP 客户端设置、分页参数、时间窗口、 批处理、重试策略和限流配置。
///
/// ## 职责
///
/// - 封装单个数据源的完整配置信息
///   - 提供配置合并和默认值填充
///   - 支持从 Spring Boot 配置属性中读取配置
///   - 验证配置完整性和有效性
///
/// ## 核心组件
///
/// - {@link ProvenanceConfig} - 数据源配置记录
///   - {@link DefaultConfigProvider} - 默认配置提供者
///   - {@link HttpConfig} - HTTP 客户端配置
///   - {@link PaginationConfig} - 分页配置
///   - {@link WindowOffsetConfig} - 时间窗口配置
///   - {@link BatchingConfig} - 批处理配置
///   - {@link RetryConfig} - 重试策略配置
///   - {@link RateLimitConfig} - 限流配置
///
/// ## 配置层级
///
/// ```
///
/// ProvenanceConfig (数据源配置)
/// ├── baseUrl (必需)
/// ├── HttpConfig (HTTP 设置)
/// │   ├── headers (默认请求头)
/// │   ├── connectTimeout
/// │   ├── readTimeout
/// │   └── writeTimeout
/// ├── PaginationConfig (分页)
/// │   ├── pageSize
/// │   └── maxResults
/// ├── WindowOffsetConfig (时间窗口)
/// │   └── windowSize
/// ├── BatchingConfig (批处理)
/// │   └── batchSize
/// ├── RetryConfig (重试)
/// │   ├── maxAttempts
/// │   └── backoff
/// └── RateLimitConfig (限流)
///     ├── requestsPerSecond
///     └── burstCapacity
///
/// ```
///
/// ## 使用示例
///
/// ```java
/// // 从配置提供者获取配置
/// DefaultConfigProvider provider = new DefaultConfigProvider(properties);
/// ProvenanceConfig config = provider.getConfig("pubmed");
///
/// // 使用配置
/// String baseUrl = config.baseUrl();
/// int pageSize = config.pagination().pageSize();
/// int timeout = config.http().connectTimeout();
/// ```
///
/// ## 配置合并策略
///
/// 支持三层配置合并（优先级从高到低）：
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.config;
