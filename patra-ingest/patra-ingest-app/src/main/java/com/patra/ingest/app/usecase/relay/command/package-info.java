/// Outbox 中继命令包。
/// 
/// 本包定义 Outbox 中继用例的输入命令对象。
/// 
/// ## 职责
/// 
/// - 封装中继用例的输入参数
///   - 支持按通道过滤（channel）或处理所有通道（channel=null）
///   - 支持自定义批次大小
/// 
/// ## 核心组件
/// 
/// - `OutboxRelayCommand` - Outbox 中继命令
///       
/// - `channel`: 目标通道（可选，null 表示所有通道）
///         - `batchSize`: 批次大小（如 100）
/// 
/// ## 使用示例
/// 
/// ### 中继所有通道
/// 
/// ```java
/// var command = OutboxRelayCommand.builder()
///     .batchSize(100)
///     .build();  // channel=null，处理所有通道
/// 
/// var report = outboxRelayUseCase.relay(command);
/// ```
/// 
/// ### 中继特定通道
/// 
/// ```java
/// var command = OutboxRelayCommand.builder()
///     .channel(OutboxChannels.INGEST_TASK_READY)
///     .batchSize(200)
///     .build();
/// 
/// var report = outboxRelayUseCase.relay(command);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.command;
