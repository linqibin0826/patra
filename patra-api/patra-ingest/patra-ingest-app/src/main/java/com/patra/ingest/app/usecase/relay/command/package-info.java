/**
 * Outbox 中继命令包。
 *
 * <p>本包定义 Outbox 中继用例的输入命令对象。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装中继用例的输入参数
 *   <li>支持按通道过滤（channel）或处理所有通道（channel=null）
 *   <li>支持自定义批次大小
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code OutboxRelayCommand} - Outbox 中继命令
 *       <ul>
 *         <li>{@code channel}: 目标通道（可选，null 表示所有通道）
 *         <li>{@code batchSize}: 批次大小（如 100）
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>中继所有通道</h3>
 *
 * <pre>{@code
 * var command = OutboxRelayCommand.builder()
 *     .batchSize(100)
 *     .build();  // channel=null，处理所有通道
 *
 * var report = outboxRelayUseCase.relay(command);
 * }</pre>
 *
 * <h3>中继特定通道</h3>
 *
 * <pre>{@code
 * var command = OutboxRelayCommand.builder()
 *     .channel(OutboxChannels.INGEST_TASK_READY)
 *     .batchSize(200)
 *     .build();
 *
 * var report = outboxRelayUseCase.relay(command);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.command;
