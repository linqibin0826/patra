/**
 * Feign 错误处理可观测性包。
 *
 * <p>集成 Micrometer 记录 Feign 错误处理的性能指标和业务指标。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>记录错误解码耗时和成功率
 *   <li>记录 ProblemDetail 解析性能
 *   <li>记录 TraceId 提取成功率
 *   <li>记录响应体读取大小和截断次数
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link FeignErrorObservationRecorder} - 指标记录接口
 *   <li>{@link MicrometerFeignErrorObservationRecorder} - Micrometer 实现
 * </ul>
 *
 * <h2>指标类别</h2>
 *
 * <h3>1. 解码指标</h3>
 *
 * <ul>
 *   <li><b>feign.error.decoding.outcome</b> - 解码结果计数器
 *       <ul>
 *         <li>标签：{@code methodKey}, {@code httpStatus}, {@code success}, {@code tolerantMode}
 *       </ul>
 *   <li><b>feign.error.decoding.duration</b> - 解码耗时（毫秒）
 *       <ul>
 *         <li>标签：{@code methodKey}, {@code httpStatus}
 *       </ul>
 * </ul>
 *
 * <h3>2. ProblemDetail 解析指标</h3>
 *
 * <ul>
 *   <li><b>feign.error.problemdetail.parsing</b> - 解析结果计数器
 *       <ul>
 *         <li>标签：{@code methodKey}, {@code httpStatus}, {@code success}
 *       </ul>
 *   <li><b>feign.error.problemdetail.parsing.duration</b> - 解析耗时（毫秒）
 *       <ul>
 *         <li>标签：{@code methodKey}
 *       </ul>
 * </ul>
 *
 * <h3>3. TraceId 提取指标</h3>
 *
 * <ul>
 *   <li><b>feign.error.traceid.extraction</b> - 提取结果计数器
 *       <ul>
 *         <li>标签：{@code methodKey}, {@code success}, {@code headerName}
 *       </ul>
 * </ul>
 *
 * <h3>4. 响应体读取指标</h3>
 *
 * <ul>
 *   <li><b>feign.error.response.body.read</b> - 读取计数器
 *       <ul>
 *         <li>标签：{@code methodKey}, {@code truncated}
 *       </ul>
 *   <li><b>feign.error.response.body.size</b> - 响应体大小（字节）
 *       <ul>
 *         <li>标签：{@code methodKey}
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class CustomFeignErrorHandler {
 *     private final FeignErrorObservationRecorder recorder;
 *
 *     public void handleError(Response response) {
 *         long start = System.currentTimeMillis();
 *         try {
 *             // 处理错误...
 *             recorder.recordDecodingOutcome("CustomClient#method", 500, true, false);
 *         } finally {
 *             long duration = System.currentTimeMillis() - start;
 *             // 记录耗时...
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Grafana 仪表盘查询示例</h2>
 *
 * <pre>{@code
 * // 错误解码成功率
 * sum(rate(feign_error_decoding_outcome_total{success="true"}[5m]))
 * /
 * sum(rate(feign_error_decoding_outcome_total[5m]))
 *
 * // ProblemDetail 解析平均耗时
 * avg(feign_error_problemdetail_parsing_duration_milliseconds)
 *
 * // TraceId 提取成功率（按 Header）
 * sum by (headerName) (
 *   rate(feign_error_traceid_extraction_total{success="true"}[5m])
 * )
 *
 * // 响应体截断次数
 * sum(rate(feign_error_response_body_read_total{truncated="true"}[5m]))
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.error.observation;
