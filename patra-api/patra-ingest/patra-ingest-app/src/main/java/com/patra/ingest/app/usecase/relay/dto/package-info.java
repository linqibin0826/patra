/**
 * Outbox 中继数据传输对象包。
 *
 * <p>本包定义 Outbox 中继用例的输出结果对象。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装中继用例的输出结果
 *   <li>提供中继统计信息（成功数、失败数、租约丢失数）
 *   <li>隔离领域模型和外部表示
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code RelayReport} - 中继报告
 *       <ul>
 *         <li>{@code channel}: 处理的通道
 *         <li>{@code fetched}: 获取的消息数
 *         <li>{@code published}: 成功发布的消息数
 *         <li>{@code failed}: 发布失败的消息数
 *         <li>{@code leaseLost}: 租约丢失的消息数
 *         <li>{@code retried}: 重试的消息数
 *         <li>{@code elapsedMillis}: 执行时长（毫秒）
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * public RelayReport relay(OutboxRelayCommand command) {
 *     // 执行中继逻辑
 *     var result = relayExecutor.execute(plan);
 *
 *     // 返回报告
 *     return RelayReport.builder()
 *         .channel(result.getChannel())
 *         .fetched(result.getFetchedCount())
 *         .published(result.getPublishedCount())
 *         .failed(result.getFailedCount())
 *         .leaseLost(result.getLeaseLostCount())
 *         .retried(result.getRetriedCount())
 *         .elapsedMillis(result.getElapsedMillis())
 *         .build();
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.dto;
