package com.patra.ingest.adapter.rocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.cqrs.CommandBus;
import com.patra.ingest.adapter.rocketmq.dto.TaskReadyPayload;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/// RocketMQ 任务就绪消息监听器。
///
/// 使用 RocketMQ 官方 Spring Boot Starter 订阅 INGEST_TASK 主题（READY 标签）并启动任务执行工作流。
///
/// **架构特性**:
///
/// - 使用 {@link MessageExt} 保留完整的消息元数据(KEYS, TAGS, UserProperties)
///   - 通过 {@link RocketMQMessageListener} 注解配置消费者组和 TAGS 过滤
///   - 直接 API 访问,性能优异
///
/// **职责**:
///
/// - 订阅 INGEST_TASK 主题的 READY 标签(任务就绪事件)
///   - 从 {@link MessageExt} 提取完整元数据(KEYS, TAGS, messageId, UserProperties)
///   - 解析 JSON 消息体为 {@link TaskReadyPayload} DTO
///   - 验证必填字段(taskId, idempotentKey)
///   - 组装 {@link TaskReadyCommand} 并委托给应用层用例
///   - 处理消费失败(抛出异常触发 RocketMQ 自动重试)
///
/// **消息元数据**:
///
/// - KEYS - 从 {@link MessageExt#getKeys()} 获取,对应发送端的 dedupKey
///   - TAGS - 从 {@link MessageExt#getTags()} 获取,用于消费端过滤
///   - messageId - 从 {@link MessageExt#getMsgId()} 获取
///   - UserProperties - 从 {@link MessageExt#getUserProperty(String)} 获取自定义属性
///
/// **错误处理**: 消费失败抛出 RuntimeException,由 RocketMQ 框架处理重试和死信队列路由。
///
/// @author linqibin
/// @since 0.1.0
/// @see TaskReadyCommand
@Slf4j
@Component
@ConditionalOnProperty(
    name = "patra.ingest.listener.task-ready.enabled",
    havingValue = "true",
    matchIfMissing = true // 默认启用（生产环境）
    )
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "${patra.ingest.mq.topics.task}", // INGEST_TASK
    consumerGroup = "${patra.ingest.mq.consumer-groups.task}", // 从配置读取消费者组
    selectorExpression = "READY" // 只订阅 READY 标签
    )
public class TaskReadyMessageListener implements RocketMQListener<MessageExt> {

  private final CommandBus commandBus;
  private final ObjectMapper objectMapper;

  @Override
  public void onMessage(MessageExt message) {
    try {
      // 1. 提取并记录消息元数据
      logMessageMetadata(message);

      // 2. 解析消息体并构建命令
      TaskReadyCommand command = parseMessage(message);

      // 3. 通过 CommandBus 委托给应用层处理器执行任务
      commandBus.handle(command);

      log.info("任务就绪消息消费成功, taskId={} msgId={}", command.taskId(), message.getMsgId());

    } catch (Exception e) {
      log.error(
          "从主题 [{}] 消费任务就绪消息失败, msgId={} KEYS={} TAGS={} reconsumeTimes={} error={}",
          message.getTopic(),
          message.getMsgId(),
          message.getKeys(),
          message.getTags(),
          message.getReconsumeTimes(),
          e.getMessage(),
          e);
      // 抛出异常触发 RocketMQ 重试机制
      throw new RuntimeException("消息消费失败", e);
    }
  }

  /// 从 RocketMQ MessageExt 提取并记录关键消息元数据。
  ///
  /// @param message RocketMQ 消息
  private void logMessageMetadata(MessageExt message) {
    String topic = message.getTopic();
    String keys = message.getKeys(); // 对应发送端的 dedupKey
    String tags = message.getTags(); // 对应发送端的 opType
    String msgId = message.getMsgId();
    String partitionKey = message.getUserProperty("partitionKey"); // 自定义属性
    String channel = message.getUserProperty("channel"); // 业务通道

    log.info(
        "正在从主题 [{}] 消费任务就绪事件, KEYS={} TAGS={} msgId={} partitionKey={} channel={}",
        topic,
        keys,
        tags,
        msgId,
        partitionKey,
        channel);

    // DEBUG 级别记录所有 UserProperties
    if (log.isDebugEnabled()) {
      Map<String, String> userProperties = message.getProperties();
      log.debug("完整消息属性: {}", userProperties);
    }
  }

  /// 将 RocketMQ MessageExt 解析为 TaskReadyCommand。
  ///
  /// @param message RocketMQ 消息
  /// @return TaskReadyCommand
  /// @throws Exception 当解析失败时
  private TaskReadyCommand parseMessage(MessageExt message) throws Exception {
    // 1. 解析 JSON 消息体
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    TaskReadyPayload dto = objectMapper.readValue(payload, TaskReadyPayload.class);

    // 2. 验证必填字段
    dto.validate();

    // 3. 构建消息头 Map (包含 RocketMQ 元数据)
    Map<String, Object> headers = new HashMap<>();
    headers.put("msgId", message.getMsgId());
    headers.put("KEYS", message.getKeys());
    headers.put("TAGS", message.getTags());
    headers.put("topic", message.getTopic());

    // 4. 添加所有 UserProperties
    Map<String, String> userProperties = message.getProperties();
    if (userProperties != null) {
      headers.putAll(userProperties);
    }

    return new TaskReadyCommand(dto.getTaskId(), dto.getIdempotentKey(), headers);
  }
}
