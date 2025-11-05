/**
 * Provenance 指标监控包。
 *
 * <p>集成 Micrometer 记录数据源调用的性能指标和业务指标。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>记录 API 调用次数、耗时、成功率
 *   <li>记录数据检索量、批次大小
 *   <li>提供自定义指标扩展点
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ProvenanceMetrics} - Provenance 指标记录器
 * </ul>
 *
 * <h2>指标类别</h2>
 *
 * <ul>
 *   <li><b>API 调用指标</b> - 调用次数、耗时、HTTP 状态
 *   <li><b>数据量指标</b> - 检索记录数、批次大小
 *   <li><b>错误指标</b> - 失败次数、错误类型
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class PubMedClientImpl implements PubMedClient {
 *     private final ProvenanceMetrics metrics;
 *
 *     public ESearchResponse esearch(ESearchRequest request) {
 *         Timer.Sample sample = Timer.start();
 *         try {
 *             ESearchResponse response = doEsearch(request);
 *             metrics.recordApiCall("pubmed", "esearch", true);
 *             return response;
 *         } catch (Exception ex) {
 *             metrics.recordApiCall("pubmed", "esearch", false);
 *             throw ex;
 *         } finally {
 *             sample.stop(metrics.getApiCallTimer("pubmed", "esearch"));
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.metrics;
