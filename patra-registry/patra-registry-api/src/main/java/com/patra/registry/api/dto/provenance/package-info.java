/// Provenance 配置 API 数据传输对象包 - REST API 契约层。
/// 
/// 本包包含数据源配置相关的 REST API 响应 DTOs,定义了对外暴露的数据传输对象契约。 这些 DTOs 隔离了内部领域模型与外部 API 表示,确保 API 的稳定性和向后兼容性。
/// 
/// ## 职责
/// 
/// - 定义数据源配置的 API 响应格式
///   - 隔离领域模型与 API 表示,防止内部变更影响外部契约
///   - 提供清晰的 API 文档(通过 record 字段和 Javadoc)
///   - 支持 JSON 序列化/反序列化(Jackson)
///   - 支持 Feign 客户端集成
/// 
/// ## 核心 DTOs
/// 
/// - {@link com.patra.registry.api.dto.provenance.ProvenanceResp} - 数据源元数据响应, 包含唯一标识、代码、名称、默认
///       URL 和时区等核心信息
///   - {@link com.patra.registry.api.dto.provenance.ProvenanceConfigResp} - 配置聚合响应, 包含数据源元数据和 7
///       个配置维度的完整视图
///   - {@link com.patra.registry.api.dto.provenance.HttpConfigResp} - HTTP 客户端配置响应
///   - {@link com.patra.registry.api.dto.provenance.RetryConfigResp} - 重试策略配置响应
///   - {@link com.patra.registry.api.dto.provenance.PaginationConfigResp} - 分页策略配置响应
///   - {@link com.patra.registry.api.dto.provenance.BatchingConfigResp} - 批处理配置响应
///   - {@link com.patra.registry.api.dto.provenance.RateLimitConfigResp} - 速率限制配置响应
///   - {@link com.patra.registry.api.dto.provenance.WindowOffsetResp} - 时间窗口偏移配置响应
/// 
/// ## DTO 设计原则
/// 
/// - **不可变性**: 所有 DTOs 使用 `record` 实现,一旦创建不可修改
///   - **扁平化**: 简化嵌套结构,便于 JSON 序列化和客户端使用
///   - **字段完整**: 包含所有必需字段,清晰的 Javadoc 说明
///   - **命名规范**: 使用 `*Resp` 后缀,表示响应对象
///   - **无业务逻辑**: 纯数据传输对象,不包含验证或业务逻辑
/// 
/// ## ProvenanceConfigResp 聚合响应
/// 
/// 配置聚合响应 DTO 包含 7 个配置维度:
/// 
/// - **provenance** - 数据源元数据(必需)
///   - windowOffset - 时间窗口偏移配置(可选)
///   - pagination - 分页策略配置(可选)
///   - http - HTTP 客户端配置(可选)
///   - batching - 批处理配置(可选)
///   - retry - 重试策略配置(可选)
///   - rateLimit - 速率限制配置(可选)
/// 
/// 所有配置字段(除 provenance 外)都可能为 null,表示该维度未配置。
/// 
/// ## 与领域模型的映射
/// 
/// DTOs 由适配器层(`patra-registry-adapter`)从领域模型转换:
/// 
/// - {@link com.patra.registry.domain.model.vo.provenance.Provenance} → {@link
///       com.patra.registry.api.dto.provenance.ProvenanceResp}
///   - {@link com.patra.registry.domain.model.aggregate.ProvenanceConfiguration} → {@link
///       com.patra.registry.api.dto.provenance.ProvenanceConfigResp}
///   - {@link com.patra.registry.domain.model.vo.provenance.HttpConfig} → {@link
///       com.patra.registry.api.dto.provenance.HttpConfigResp}
///   - 其他配置值对象同理
/// 
/// ## 使用场景
/// 
/// - **Feign 客户端**: 下游服务(如 patra-ingest)通过 Feign 客户端接收这些 DTOs
///   - **REST API**: 适配器层返回这些 DTOs 作为 HTTP 响应体
///   - **JSON 序列化**: Jackson 自动序列化这些 DTOs 为 JSON
/// 
/// ## 使用示例
/// 
/// ```java
/// // Feign 客户端调用
/// @FeignClient(name = "patra-registry")
/// public interface ProvenanceClient extends ProvenanceEndpoint {
/// 
/// @Autowired
/// private ProvenanceClient client;
/// 
/// // 查询数据源元数据
/// ProvenanceResp pubmed = client.getProvenance(ProvenanceCode.PUBMED);
/// 
/// // 查询完整配置聚合
/// ProvenanceConfigResp config = client.getConfiguration(
///     ProvenanceCode.PUBMED,
///     "HARVEST",
///     Instant.now()
/// );
/// 
/// // 访问配置维度
/// if (config.http() != null) {
///     HttpConfigResp httpConfig = config.http();
///     Integer timeout = httpConfig.timeoutReadMillis();
/// ```
/// 
/// ## JSON 响应示例
/// 
/// ```java
/// {
///   "provenance": {
///     "id": 1,
///     "code": "PUBMED",
///     "name": "PubMed",
///     "baseUrlDefault": "https://eutils.ncbi.nlm.nih.gov",
///     "timezoneDefault": "UTC",
///     "active": true,
///     "lifecycleStatusCode": "ACTIVE",
///   "http": {
///     "timeoutConnectMillis": 5000,
///     "timeoutReadMillis": 10000,
///     "timeoutTotalMillis": 30000,
///     "tlsVerifyEnabled": true,
///     "retryAfterPolicyCode": "RESPECT",
///   "retry": {
///     "maxAttempts": 3,
///     "backoffMultiplier": 2.0
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.api.dto.provenance;
