/// Feign 错误解码器包。
/// 
/// 实现基于 RFC 7807 ProblemDetail 的 Feign ErrorDecoder，支持智能解析和优雅降级。
/// 
/// ## 职责
/// 
/// - 解析 `application/problem+json` 格式的错误响应
///   - 提取业务错误代码、跟踪标识符和扩展属性
///   - 在宽容模式下优雅处理非标准响应
///   - 记录解析性能和指标
/// 
/// ## 核心组件
/// 
/// - {@link ProblemDetailErrorDecoder} - ProblemDetail 错误解码器
/// 
/// ## 解码策略
/// 
/// ```
/// 
/// 1. 检查 Content-Type
///    ├── application/problem+json
///    │   ├── 解析 ProblemDetail
///    │   │   ├── 提取 status、detail、title
///    │   │   ├── 提取 extensions (errorCode, traceId, ...)
///    │   │   └── 构造 RemoteCallException(problemDetail)
///    │   └── 解析失败 → 进入宽容模式
///    └── 其他 Content-Type → 进入宽容模式
/// 
/// 2. 宽容模式（tolerant=true）
///    ├── 读取响应体（最多 max-error-body-size 字节）
///    ├── 尝试从 Header 提取 TraceId
///    ├── 构造 RemoteCallException(status, message, traceId)
///    └── 记录降级指标
/// 
/// 3. 严格模式（tolerant=false）
///    └── 抛出 FeignException.errorStatus()
/// 
/// ```
/// 
/// ## TraceId 提取优先级
/// 
/// ## 性能优化
/// 
/// - 响应体读取限制 `max-error-body-size`，防止 OOM
///   - 记录解析耗时，识别慢操作
///   - 缓存 Content-Type 解析结果
/// 
/// ## 使用示例
/// 
/// ```java
/// // 自定义 ErrorDecoder（如需）
/// @Bean
/// @ConditionalOnMissingBean
/// public ErrorDecoder feignErrorDecoder(
///     ObjectMapper objectMapper,
///     FeignErrorProperties properties,
///     FeignErrorObservationRecorder recorder) {
///     return new ProblemDetailErrorDecoder(objectMapper, properties, recorder);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error.decoder;
