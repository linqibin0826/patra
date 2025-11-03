/**
 * RocketMQ 消息监听器适配器层。
 *
 * <p>本包包含基于 RocketMQ 官方 Spring Boot Starter 的消息消费者实现。
 *
 * <p><strong>架构设计</strong>:
 *
 * <ul>
 *   <li>使用 {@link org.apache.rocketmq.spring.annotation.RocketMQMessageListener} 注解配置消费者
 *   <li>接收 {@link org.apache.rocketmq.common.message.MessageExt} 保留完整消息元数据
 *   <li>直接 API 访问,减少抽象层,提升性能
 * </ul>
 *
 * <p><strong>消息流</strong>:
 *
 * <pre>
 * RocketMQ Topic → TaskReadyMessageListener → TaskExecutionUseCase → 任务执行
 * </pre>
 *
 * <p><strong>关键组件</strong>:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.adapter.rocketmq.TaskReadyMessageListener} - 任务就绪消息监听器
 * </ul>
 *
 * @since 0.2.0
 * @see com.patra.ingest.app.usecase.execution.TaskExecutionUseCase
 */
package com.patra.ingest.adapter.rocketmq;
