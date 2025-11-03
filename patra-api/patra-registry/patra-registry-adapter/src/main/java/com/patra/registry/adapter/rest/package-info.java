/**
 * Provenance Registry REST API 端点实现。
 *
 * <p>本包包含驱动适配器,接收 HTTP 请求并将其转换为应用层用例调用。所有类都是六边形架构适配器层的一部分(外部 → 系统方向)。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现 {@code patra-registry-api} 中定义的 OpenAPI 端点契约
 *   <li>使用 {@code @Valid} 注解验证请求 DTO
 *   <li>委托给应用层编排器
 *   <li>转换领域结果为 API 响应 DTO
 *   <li>映射领域异常为 HTTP ProblemDetail 响应
 * </ul>
 *
 * <h2>API 端点</h2>
 *
 * <ul>
 *   <li>{@code ProvenanceEndpointImpl} - Provenance 配置 CRUD 操作
 *   <li>{@code ExprEndpointImpl} - 表达式编译和验证
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>端点实现: {@code *EndpointImpl}
 *   <li>API 转换器: {@code *ApiConverter}
 * </ul>
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class ProvenanceEndpointImpl implements ProvenanceEndpoint {
 *     private final ProvenanceOrchestrator orchestrator;
 *     private final ProvenanceApiConverter converter;
 *
 *     @Override
 *     public ProvenanceResponse createProvenance(@Valid @RequestBody CreateProvenanceRequest request) {
 *         var command = converter.toCommand(request);
 *         var result = orchestrator.createProvenance(command);
 *         return converter.toResponse(result);
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.registry.adapter.rest;
