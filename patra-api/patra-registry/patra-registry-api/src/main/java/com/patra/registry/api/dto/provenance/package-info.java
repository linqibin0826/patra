/**
 * Provenance 配置 API 数据传输对象包 - REST API 契约层。
 *
 * <p>本包包含数据源配置相关的 REST API 响应 DTOs,定义了对外暴露的数据传输对象契约。
 * 这些 DTOs 隔离了内部领域模型与外部 API 表示,确保 API 的稳定性和向后兼容性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义数据源配置的 API 响应格式
 *   <li>隔离领域模型与 API 表示,防止内部变更影响外部契约
 *   <li>提供清晰的 API 文档(通过 record 字段和 Javadoc)
 *   <li>支持 JSON 序列化/反序列化(Jackson)
 *   <li>支持 Feign 客户端集成
 * </ul>
 *
 * <h2>核心 DTOs</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.api.dto.provenance.ProvenanceResp} - 数据源元数据响应,
 *       包含唯一标识、代码、名称、默认 URL 和时区等核心信息
 *   <li>{@link com.patra.registry.api.dto.provenance.ProvenanceConfigResp} - 配置聚合响应,
 *       包含数据源元数据和 7 个配置维度的完整视图
 *   <li>{@link com.patra.registry.api.dto.provenance.HttpConfigResp} - HTTP 客户端配置响应
 *   <li>{@link com.patra.registry.api.dto.provenance.RetryConfigResp} - 重试策略配置响应
 *   <li>{@link com.patra.registry.api.dto.provenance.PaginationConfigResp} - 分页策略配置响应
 *   <li>{@link com.patra.registry.api.dto.provenance.BatchingConfigResp} - 批处理配置响应
 *   <li>{@link com.patra.registry.api.dto.provenance.RateLimitConfigResp} - 速率限制配置响应
 *   <li>{@link com.patra.registry.api.dto.provenance.WindowOffsetResp} - 时间窗口偏移配置响应
 * </ul>
 *
 * <h2>DTO 设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 所有 DTOs 使用 {@code record} 实现,一旦创建不可修改
 *   <li><strong>扁平化</strong>: 简化嵌套结构,便于 JSON 序列化和客户端使用
 *   <li><strong>字段完整</strong>: 包含所有必需字段,清晰的 Javadoc 说明
 *   <li><strong>命名规范</strong>: 使用 {@code *Resp} 后缀,表示响应对象
 *   <li><strong>无业务逻辑</strong>: 纯数据传输对象,不包含验证或业务逻辑
 * </ul>
 *
 * <h2>ProvenanceConfigResp 聚合响应</h2>
 *
 * <p>配置聚合响应 DTO 包含 7 个配置维度:
 *
 * <ul>
 *   <li><strong>provenance</strong> - 数据源元数据(必需)
 *   <li>windowOffset - 时间窗口偏移配置(可选)
 *   <li>pagination - 分页策略配置(可选)
 *   <li>http - HTTP 客户端配置(可选)
 *   <li>batching - 批处理配置(可选)
 *   <li>retry - 重试策略配置(可选)
 *   <li>rateLimit - 速率限制配置(可选)
 * </ul>
 *
 * <p>所有配置字段(除 provenance 外)都可能为 null,表示该维度未配置。
 *
 * <h2>与领域模型的映射</h2>
 *
 * <p>DTOs 由适配器层({@code patra-registry-adapter})从领域模型转换:
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.model.vo.provenance.Provenance} → {@link com.patra.registry.api.dto.provenance.ProvenanceResp}
 *   <li>{@link com.patra.registry.domain.model.aggregate.ProvenanceConfiguration} → {@link com.patra.registry.api.dto.provenance.ProvenanceConfigResp}
 *   <li>{@link com.patra.registry.domain.model.vo.provenance.HttpConfig} → {@link com.patra.registry.api.dto.provenance.HttpConfigResp}
 *   <li>其他配置值对象同理
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>Feign 客户端</strong>: 下游服务(如 patra-ingest)通过 Feign 客户端接收这些 DTOs
 *   <li><strong>REST API</strong>: 适配器层返回这些 DTOs 作为 HTTP 响应体
 *   <li><strong>JSON 序列化</strong>: Jackson 自动序列化这些 DTOs 为 JSON
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // Feign 客户端调用
 * @FeignClient(name = "patra-registry")
 * public interface ProvenanceClient extends ProvenanceEndpoint {}
 *
 * @Autowired
 * private ProvenanceClient client;
 *
 * // 查询数据源元数据
 * ProvenanceResp pubmed = client.getProvenance(ProvenanceCode.PUBMED);
 *
 * // 查询完整配置聚合
 * ProvenanceConfigResp config = client.getConfiguration(
 *     ProvenanceCode.PUBMED,
 *     "HARVEST",
 *     Instant.now()
 * );
 *
 * // 访问配置维度
 * if (config.http() != null) {
 *     HttpConfigResp httpConfig = config.http();
 *     Integer timeout = httpConfig.timeoutReadMillis();
 * }
 * }</pre>
 *
 * <h2>JSON 响应示例</h2>
 *
 * <pre>{@code
 * {
 *   "provenance": {
 *     "id": 1,
 *     "code": "PUBMED",
 *     "name": "PubMed",
 *     "baseUrlDefault": "https://eutils.ncbi.nlm.nih.gov",
 *     "timezoneDefault": "UTC",
 *     "active": true,
 *     "lifecycleStatusCode": "ACTIVE"
 *   },
 *   "http": {
 *     "timeoutConnectMillis": 5000,
 *     "timeoutReadMillis": 10000,
 *     "timeoutTotalMillis": 30000,
 *     "tlsVerifyEnabled": true,
 *     "retryAfterPolicyCode": "RESPECT"
 *   },
 *   "retry": {
 *     "maxAttempts": 3,
 *     "backoffMultiplier": 2.0
 *   }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.api.dto.provenance;
