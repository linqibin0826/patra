/**
 * RocketMQ message consumers that trigger task execution workflows.
 *
 * <p>This package contains driving adapters that receive messages from RocketMQ topics and
 * translate them into application use case calls. All classes here are part of the Hexagonal
 * Architecture's adapter layer (External → System direction).
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Consume messages from RocketMQ topics
 *   <li>Parse and validate message payloads
 *   <li>Extract tracing context (traceId, spanId) from message headers
 *   <li>Delegate to {@code TaskExecutionUseCase} or other orchestrators
 *   <li>Handle adapter-level error mapping and dead letter queue (DLQ) routing
 * </ul>
 *
 * <h2>Message Topics</h2>
 *
 * <ul>
 *   <li>{@code INGEST_TASK_READY} - Task ready for execution (consumed by {@code
 *       IngestStreamConsumers})
 * </ul>
 *
 * <h2>Configuration</h2>
 *
 * Message consumers are configured as Spring Cloud Stream {@code @Bean} methods that return {@code
 * Consumer<Message<String>>}. The framework handles subscription, message delivery, and error
 * handling.
 *
 * <h2>Example</h2>
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
