/// Provenance Registry REST API 端点实现。
/// 
/// 本包包含驱动适配器,接收 HTTP 请求并将其转换为应用层用例调用。所有类都是六边形架构适配器层的一部分(外部 → 系统方向)。
/// 
/// ## 职责
/// 
/// - 实现 `patra-registry-api` 中定义的 OpenAPI 端点契约
///   - 使用 `@Valid` 注解验证请求 DTO
///   - 委托给应用层编排器
///   - 转换领域结果为 API 响应 DTO
///   - 映射领域异常为 HTTP ProblemDetail 响应
/// 
/// ## API 端点
/// 
/// - `ProvenanceEndpointImpl` - Provenance 配置 CRUD 操作
///   - `ExprEndpointImpl` - 表达式编译和验证
/// 
/// ## 命名约定
/// 
/// - 端点实现: `*EndpointImpl`
///   - API 转换器: `*ApiConverter`
/// 
/// ## 示例
/// 
/// ```java
/// @RestController
/// @RequiredArgsConstructor
/// public class ProvenanceEndpointImpl implements ProvenanceEndpoint {
///     private final ProvenanceOrchestrator orchestrator;
///     private final ProvenanceApiConverter converter;
/// 
///     @Override
///     public ProvenanceResponse createProvenance(@Valid @RequestBody CreateProvenanceRequest request) {
///         var command = converter.toCommand(request);
///         var result = orchestrator.createProvenance(command);
///         return converter.toResponse(result);
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.registry.adapter.rest;
