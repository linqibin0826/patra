/// Provenance 指标监控包。
///
/// 集成 Micrometer 记录数据源调用的性能指标和业务指标。
///
/// ## 职责
///
/// - 记录 API 调用次数、耗时、成功率
///   - 记录数据检索量、批次大小
///   - 提供自定义指标扩展点
///
/// ## 核心组件
///
/// - {@link ProvenanceMetrics} - Provenance 指标记录器
///
/// ## 指标类别
///
/// - **API 调用指标** - 调用次数、耗时、HTTP 状态
///   - **数据量指标** - 检索记录数、批次大小
///   - **错误指标** - 失败次数、错误类型
///
/// ## 使用示例
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class PubMedClientImpl implements PubMedClient {
///     private final ProvenanceMetrics metrics;
///
///     public ESearchResponse esearch(ESearchRequest request) {
///         Timer.Sample sample = Timer.start();
///         try {
///             ESearchResponse response = doEsearch(request);
///             metrics.recordApiCall("pubmed", "esearch", true);
///             return response; catch (Exception ex) {
///             metrics.recordApiCall("pubmed", "esearch", false);
///             throw ex; finally {
///             sample.stop(metrics.getApiCallTimer("pubmed", "esearch"));
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.metrics;
