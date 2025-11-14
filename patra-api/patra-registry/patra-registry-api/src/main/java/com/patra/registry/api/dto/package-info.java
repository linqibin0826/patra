/**
 * Registry API 数据传输对象根包 - DTO 层组织结构。
 *
 * <p>本包是 Registry API 所有数据传输对象(DTO)的根包,按业务领域组织请求和响应对象。 DTOs 作为 API 契约的核心组成部分,隔离了内部领域模型与外部 API 表示,
 * 确保 API 的稳定性和向后兼容性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义 REST API 的请求和响应数据结构
 *   <li>隔离领域模型与 API 表示,防止内部变更影响外部契约
 *   <li>支持 JSON 序列化/反序列化(Jackson)
 *   <li>提供清晰的 API 文档(通过 record 字段和 Javadoc)
 *   <li>支持 Feign 客户端的类型安全调用
 * </ul>
 *
 * <h2>子包结构</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.api.dto.provenance} - 数据源配置 DTOs, 包含数据源元数据和运营配置(HTTP、重试、分页等)的响应对象
 *   <li>{@link com.patra.registry.api.dto.expr} - 表达式 DTOs, 包含表达式快照、字段定义、渲染规则、参数映射等响应对象
 *   <li>{@link com.patra.registry.api.dto.dict} - 字典 DTOs, 包含字典类型、字典项等响应对象
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li><strong>响应对象</strong>: {@code *Resp} 后缀,表示 API 响应 DTO
 *   <li><strong>请求对象</strong>: {@code *Req} 后缀,表示 API 请求 DTO
 *   <li><strong>查询对象</strong>: {@code *Query} 后缀,表示查询参数对象
 * </ul>
 *
 * <h2>DTO 设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 所有 DTOs 使用 {@code record} 实现,创建后不可修改
 *   <li><strong>扁平化</strong>: 简化嵌套结构,便于 JSON 序列化和客户端使用
 *   <li><strong>字段完整</strong>: 包含所有必需字段,清晰的 Javadoc 说明
 *   <li><strong>无业务逻辑</strong>: 纯数据传输对象,不包含验证或业务逻辑
 *   <li><strong>向后兼容</strong>: 字段添加必须保持向后兼容,避免删除或重命名字段
 * </ul>
 *
 * <h2>与领域模型的关系</h2>
 *
 * <p>DTOs 与领域模型是独立的,通过适配器层转换:
 *
 * <ul>
 *   <li><strong>领域模型</strong>: {@code patra-registry-domain} 中的值对象和聚合根
 *   <li><strong>API DTOs</strong>: 本包中的 {@code *Resp} 和 {@code *Req} 对象
 *   <li><strong>转换层</strong>: {@code patra-registry-adapter} 中的 {@code *Converter} 类
 * </ul>
 *
 * <h2>JSON 序列化</h2>
 *
 * <p>所有 DTOs 都支持 Jackson 序列化/反序列化:
 *
 * <ul>
 *   <li>使用 {@code record} 自动支持 JSON 序列化
 *   <li>字段名称直接映射为 JSON 属性名
 *   <li>支持嵌套对象和集合的序列化
 *   <li>支持 {@code Instant} 等 Java 8 时间类型
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>REST API</strong>: 作为 HTTP 请求体和响应体
 *   <li><strong>Feign 客户端</strong>: 作为 Feign 方法的参数和返回值
 *   <li><strong>OpenAPI 文档</strong>: 自动生成 API 文档的数据结构
 *   <li><strong>测试</strong>: 作为 API 测试的预期响应类型
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // REST API 控制器使用
 * @RestController
 * public class ProvenanceEndpointImpl implements ProvenanceEndpoint {
 *     @Override
 *     public ProvenanceResp getProvenance(ProvenanceCode code) {
 *         Provenance domain = service.findByCode(code);
 *         return converter.toResp(domain);  // 领域模型 → DTO
 *     }
 * }
 *
 * // Feign 客户端使用
 * @FeignClient(name = "patra-registry")
 * public interface ProvenanceClient extends ProvenanceEndpoint {}
 *
 * ProvenanceResp resp = client.getProvenance(ProvenanceCode.PUBMED);
 *
 * // JSON 反序列化
 * String json = """
 *     {
 *       "id": 1,
 *       "code": "PUBMED",
 *       "name": "PubMed"
 *     }
 *     """;
 * ProvenanceResp dto = objectMapper.readValue(json, ProvenanceResp.class);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.api.dto;
