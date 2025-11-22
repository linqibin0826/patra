/// Outbox 中继数据传输对象包。
/// 
/// 本包定义 Outbox 中继用例的输出结果对象。
/// 
/// ## 职责
/// 
/// - 封装中继用例的输出结果
///   - 提供中继统计信息（成功数、失败数、租约丢失数）
///   - 隔离领域模型和外部表示
/// 
/// ## 核心组件
/// 
/// - `RelayReport` - 中继报告
///       
/// - `channel`: 处理的通道
///         - `fetched`: 获取的消息数
///         - `published`: 成功发布的消息数
///         - `failed`: 发布失败的消息数
///         - `leaseLost`: 租约丢失的消息数
///         - `retried`: 重试的消息数
///         - `elapsedMillis`: 执行时长（毫秒）
/// 
/// ## 使用示例
/// 
/// ```java
/// public RelayReport relay(OutboxRelayCommand command) {
///     // 执行中继逻辑
///     var result = relayExecutor.execute(plan);
/// 
///     // 返回报告
///     return RelayReport.builder()
///         .channel(result.getChannel())
///         .fetched(result.getFetchedCount())
///         .published(result.getPublishedCount())
///         .failed(result.getFailedCount())
///         .leaseLost(result.getLeaseLostCount())
///         .retried(result.getRetriedCount())
///         .elapsedMillis(result.getElapsedMillis())
///         .build();
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.dto;
