/// Registry 适配器层根包 - 外部交互适配器。
///
/// 本包是六边形架构适配器层的根包,包含驱动适配器(REST 端点)和被驱动适配器(如需要)。适配器负责将外部请求转换为应用层用例调用,并将领域结果转换为外部响应格式。
///
/// ## 架构定位
///
/// 在六边形架构中,本层位于系统边界,连接外部世界和应用核心:
///
/// - **上游**: 接收来自其他微服务的 Feign 调用或外部 HTTP 请求
///   - **下游**: 调用 `patra-registry-app` 应用层编排器
///   - **实现契约**: 实现 `patra-registry-api` 中定义的端点接口
///
/// ## 包结构
///
/// - `rest` - REST API 端点实现(驱动适配器)
///   - `rest.converter` - 查询 DTO 到 API 响应 DTO 的转换器
///
/// ## 核心职责
///
/// - 实现 `patra-registry-api` 定义的 REST 端点契约
///   - 验证请求参数(使用 `@Valid` 注解)
///   - 委托应用层编排器执行业务用例
///   - 将查询 DTO 转换为 API 响应 DTO
///   - 映射领域异常为 HTTP ProblemDetail 响应
///
/// ## 设计原则
///
/// - **薄适配器**: 仅负责协议转换,不包含业务逻辑
///   - **契约实现**: 严格实现 API 模块定义的接口,不额外扩展
///   - **异常映射**: 通过 `ErrorMappingContributor` 统一处理异常转换
///   - **框架隔离**: 将 Spring 等框架细节限制在适配器层
///
/// ## 典型交互流程
///
/// ```java
/// [外部客户端] → [REST Endpoint Impl]
///     ↓ 调用
/// [Application Orchestrator]
///     ↓ 返回 Query DTO
/// [API Converter]
///     ↓ 转换
/// [API Response DTO] → [外部客户端]
/// ```
///
/// ## 使用示例
///
/// ```java
/// @RestController
/// @RequiredArgsConstructor
/// public class ProvenanceEndpointImpl implements ProvenanceEndpoint {
///     private final ProvenanceConfigOrchestrator orchestrator;
///     private final ProvenanceApiConverter converter;
///
///     @Override
///     public ProvenanceResp getProvenance(@PathVariable String code) {
///         ProvenanceCode pc = ProvenanceCode.fromCode(code);
///         return orchestrator.findProvenance(pc)
///             .map(converter::toResp)
///             .orElseThrow(() -> new ProvenanceNotFoundException(code));
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.adapter;
