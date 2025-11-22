/// Feign 错误处理可观测性包。
/// 
/// 集成 Micrometer 记录 Feign 错误处理的性能指标和业务指标。
/// 
/// ## 职责
/// 
/// - 记录错误解码耗时和成功率
///   - 记录 ProblemDetail 解析性能
///   - 记录 TraceId 提取成功率
///   - 记录响应体读取大小和截断次数
/// 
/// ## 核心组件
/// 
/// - {@link FeignErrorObservationRecorder} - 指标记录接口
///   - {@link MicrometerFeignErrorObservationRecorder} - Micrometer 实现
/// 
/// ## 指标类别
/// 
/// ### 1. 解码指标
/// 
/// - **feign.error.decoding.outcome** - 解码结果计数器
///       
/// - 标签：`methodKey`, `httpStatus`, `success`, `tolerantMode`
/// 
///   - **feign.error.decoding.duration** - 解码耗时（毫秒）
///       
/// - 标签：`methodKey`, `httpStatus`
/// 
/// ### 2. ProblemDetail 解析指标
/// 
/// - **feign.error.problemdetail.parsing** - 解析结果计数器
///       
/// - 标签：`methodKey`, `httpStatus`, `success`
/// 
///   - **feign.error.problemdetail.parsing.duration** - 解析耗时（毫秒）
///       
/// - 标签：`methodKey`
/// 
/// ### 3. TraceId 提取指标
/// 
/// - **feign.error.traceid.extraction** - 提取结果计数器
///       
/// - 标签：`methodKey`, `success`, `headerName`
/// 
/// ### 4. 响应体读取指标
/// 
/// - **feign.error.response.body.read** - 读取计数器
///       
/// - 标签：`methodKey`, `truncated`
/// 
///   - **feign.error.response.body.size** - 响应体大小（字节）
///       
/// - 标签：`methodKey`
/// 
/// ## 使用示例
/// 
/// ```java
/// @Component
/// public class CustomFeignErrorHandler {
///     private final FeignErrorObservationRecorder recorder;
/// 
///     public void handleError(Response response) {
///         long start = System.currentTimeMillis();
///         try {
///             // 处理错误...
///             recorder.recordDecodingOutcome("CustomClient#method", 500, true, false); finally {
///             long duration = System.currentTimeMillis() - start;
///             // 记录耗时...
/// ```
/// 
/// ## Grafana 仪表盘查询示例
/// 
/// ```java
/// // 错误解码成功率
/// sum(rate(feign_error_decoding_outcome_total{success="true"[5m]))
/// /
/// sum(rate(feign_error_decoding_outcome_total[5m]))
/// 
/// // ProblemDetail 解析平均耗时
/// avg(feign_error_problemdetail_parsing_duration_milliseconds)
/// 
/// // TraceId 提取成功率（按 Header）
/// sum by (headerName) (
///   rate(feign_error_traceid_extraction_total{success="true"[5m])
/// )
/// 
/// // 响应体截断次数
/// sum(rate(feign_error_response_body_read_total{truncated="true"[5m]))
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error.observation;
