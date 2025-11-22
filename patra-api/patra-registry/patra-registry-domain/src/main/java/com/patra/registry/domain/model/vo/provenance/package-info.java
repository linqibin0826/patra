/// Provenance 配置值对象包 - Registry Domain 层。
/// 
/// 本包包含数据源配置的核心值对象,定义了多维度的运营配置策略。所有值对象都是不可变的(immutable), 通过 `record`
/// 实现,在构造时执行业务规则验证,确保领域不变性。
/// 
/// ## 职责
/// 
/// - 定义数据源元数据值对象({@link com.patra.registry.domain.model.vo.provenance.Provenance})
///   - 定义运营配置策略值对象(HTTP、重试、分页、批处理、限流、时间窗口)
///   - 封装业务约束和验证逻辑
///   - 提供时态配置支持(effectiveFrom/effectiveTo)
///   - 支持配置作用域优先级(TASK > OPERATION > SOURCE)
/// 
/// ## 核心值对象
/// 
/// - {@link com.patra.registry.domain.model.vo.provenance.Provenance} - 数据源元数据,包含唯一标识、代码、名称、默认
///       URL 和时区
///   - {@link com.patra.registry.domain.model.vo.provenance.HttpConfig} - HTTP
///       客户端配置,定义超时、代理、TLS、Retry-After 处理等策略
///   - {@link com.patra.registry.domain.model.vo.provenance.RetryConfig} -
///       重试策略配置,包含退避算法、最大重试次数、可重试条件
///   - {@link com.patra.registry.domain.model.vo.provenance.PaginationConfig} -
///       分页策略配置,定义分页参数、cursor 模式等
///   - {@link com.patra.registry.domain.model.vo.provenance.BatchingConfig} - 批处理配置,用于优化详情获取等批量操作
///   - {@link com.patra.registry.domain.model.vo.provenance.RateLimitConfig} - 速率限制配置,定义 API
///       调用频率控制策略
///   - {@link com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig} -
///       时间窗口偏移配置,用于基于时间分段的数据采集
/// 
/// ## 设计原则
/// 
/// - **不可变性**: 所有值对象使用 `record` 实现,一旦创建不可修改
///   - **自验证**: 通过规范构造器强制执行业务约束,使用 {@link
///       com.patra.registry.domain.exception.DomainValidationException} 快速失败
///   - **值语义**: 基于字段值比较相等性,而非对象引用
///   - **时态支持**: 所有配置都有生效时间范围(effectiveFrom/effectiveTo),支持时态查询
///   - **框架无关**: 纯 Java 对象,不依赖 Spring、JPA 等框架
/// 
/// ## 时态配置
/// 
/// 所有运营配置值对象都包含时间有效性范围:
/// 
/// - `effectiveFrom` - 配置生效时间(包含),标记此配置开始生效的时刻
///   - `effectiveTo` - 配置失效时间(不包含),null 表示永久有效
/// 
/// 通过时态查询,可以在任意时间点获取当时有效的配置,支持配置的安全更新和审计。
/// 
/// ## 配置作用域
/// 
/// 配置按作用域分为三级,优先级从高到低:
/// 
/// - **TASK 级**: 任务特定配置,最高优先级
///   - **OPERATION 级**: 操作类型特定配置(HARVEST/UPDATE/BACKFILL)
///   - **SOURCE 级**: 数据源默认配置,最低优先级
/// 
/// 高优先级配置覆盖低优先级配置,实现灵活的配置管理。
/// 
/// ## 使用示例
/// 
/// ```java
/// // 创建数据源元数据
/// Provenance pubmed = new Provenance(
///     1L,
///     "PUBMED",
///     "PubMed",
///     "https://eutils.ncbi.nlm.nih.gov",
///     "UTC",
///     "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
///     true,
///     "ACTIVE"
/// );
/// 
/// // 创建 HTTP 配置
/// HttpConfig httpConfig = new HttpConfig(
///     1L,
///     1L,
///     "HARVEST",
///     Instant.now(),
///     null,
///     null,
///     5000,
///     10000,
///     30000,
///     true,
///     null,
///     "RESPECT",
///     60000,
///     null,
///     null
/// );
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.domain.model.vo.provenance;
