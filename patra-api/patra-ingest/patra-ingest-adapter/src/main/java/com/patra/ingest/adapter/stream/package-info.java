/**
 * RocketMQ 消息消费者,触发任务执行工作流。
 *
 * <p>此包包含驱动适配器,接收来自 RocketMQ 主题的消息并将它们转换为应用用例调用。这里的所有类都是六边形架构适配器层的一部分(外部 → 系统方向)。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>从 RocketMQ 主题消费消息
 *   <li>解析和验证消息负载
 *   <li>从消息头提取追踪上下文(traceId, spanId)
 *   <li>委托给 {@code TaskExecutionUseCase} 或其他编排器
 *   <li>处理适配器层错误映射和死信队列(DLQ)路由
 * </ul>
 *
 * <h2>消息主题</h2>
 *
 * <ul>
 *   <li>{@code INGEST_TASK_READY} - 任务准备执行(由 {@code IngestStreamConsumers} 消费)
 * </ul>
 *
 * <h2>配置</h2>
 *
 * 消息消费者配置为 Spring Cloud Stream {@code @Bean} 方法,返回 {@code
 * Consumer<Message<String>>}。框架处理订阅、消息投递和错误处理。
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * @Configuration
 * public class IngestStreamConsumers {
 *     @Bean
 *     public Consumer<Message<String>> ingestTaskReadyConsumer() {
 *         return message -> {
 *             TaskReadyPayload payload = parsePayload(message);
 *             TaskReadyCommand command = toCommand(payload, message.getHeaders());
 *             taskExecutionUseCase.execute(command);
 *         };
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.adapter.stream;
