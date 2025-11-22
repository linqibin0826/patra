package com.patra.ingest.integration.messaging;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.messaging.RocketMqOutboxPublisher;
import com.patra.ingest.integration.config.MySQLContainerInitializer;
import com.patra.ingest.integration.config.RocketMQContainerInitializer;
import com.patra.ingest.testutil.OutboxMessageTestBuilder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;

/// RocketMQ Outbox 发布器集成测试。
///
/// 使用 Testcontainers 启动真实 RocketMQ 环境（由 {@link RocketMQContainerInitializer} 提供）， 测试 {@link
/// RocketMqOutboxPublisher} 的消息发送功能。
///
/// ### 测试范围
///
/// - ✅ 普通消息发送和接收
///   - ✅ 消息元数据映射 (KEYS, TAGS, UserProperties)
///   - ✅ 批量消息发送
///   - ✅ 业务通道到 Topic 映射
///   - ✅ 消息序列化和编码
///   - ✅ 顺序消息发送 (带 partitionKey)
///   - ✅ 边界情况处理 (空 payload, 无 TAGS, 空 headers)
///
/// ### 测试策略
///
/// 遵循 testing-guide.md §7 集成测试模式：
///
/// - **真实依赖**: 使用 RocketMQ Testcontainers (由 RocketMQContainerInitializer 提供)
///   - **Spring 管理 Consumer**: 使用内部类 {@link MessageCollector} 自动订阅和收集消息
///   - **异步断言**: 使用 Awaitility 等待消息接收
///   - **测试隔离**: 每个测试前清空消息收集器
///
/// ### 环境要求
///
/// - Docker Desktop 运行中
///   - 至少 4GB 可用内存
///   - 首次启动需要 ~30-40 秒 (拉取镜像 + 启动容器)
///
/// ### 性能优化
///
/// - 容器单例: 由 {@link RocketMQContainerInitializer} 和 {@link MySQLContainerInitializer}
///       配置，所有集成测试共享容器
///   - Spring Consumer: 自动管理生命周期，无需手动等待启动
///   - 并行测试: 测试方法可并发执行 (不同 Consumer Group)
///
/// ### 容器依赖说明
///
/// 虽然本测试主要测试 RocketMQ 消息发送，但由于 Spring 上下文中包含依赖数据库的组件（如 OutboxMessageRepository）， 因此也需要启动 MySQL
/// 容器。这样可以确保完整的应用上下文正常启动。
///
/// @author linqibin
/// @since 0.1.0
/// @see RocketMQContainerInitializer
/// @see MySQLContainerInitializer
/// @see OutboxMessageTestBuilder
/// @see MessageCollector
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "spring.config.import=classpath:ingest-error-config.yaml,classpath:ingest-rocketmq.yaml",
      // 禁用生产环境的 RocketMQ Listener
      "patra.ingest.listener.task-ready.enabled=false",
      // 启用测试用的 RocketMQ Consumer（使用 Spring 管理）
      "patra.ingest.test.rocketmq.consumer.enabled=true"
    })
@ContextConfiguration(
    initializers = {MySQLContainerInitializer.class, RocketMQContainerInitializer.class})
@org.springframework.context.annotation.Import(RocketMqOutboxPublisherIT.MessageCollector.class)
@DisplayName("RocketMQ Outbox 发布器集成测试")
@org.springframework.test.context.ActiveProfiles("integration-test")
// 移除 @DirtiesContext: 共享 ApplicationContext 以提升测试性能
// 测试隔离通过不同的 Consumer Group (test-consumer-group-integration) 保证
class RocketMqOutboxPublisherIT {

  @Autowired private RocketMqOutboxPublisher publisher;

  /// 测试用的消息收集器（Spring 管理的 Consumer）
  @Autowired private MessageCollector messageCollector;

  @BeforeEach
  void setUp() {
    messageCollector.clear();
  }

  @AfterEach
  void tearDown() {
    messageCollector.clear();
  }

  @Test
  @DisplayName("应该成功发送普通消息并被接收")
  void shouldSendAndReceiveNormalMessage() {
    // 准备
    OutboxMessage outboxMsg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .channel("INGEST_TASK")
            .dedupKey("test-001")
            .opType("CREATE")
            .payloadJson("{\"taskId\":1001,\"status\":\"READY\"}")
            .build();

    // 执行
    publisher.publish(outboxMsg, null);

    // 断言: 等待消息被接收 (最多 10 秒)
    await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(messageCollector.hasMessage("test-001")).isTrue());

    // 验证消息内容
    MessageExt receivedMsg = messageCollector.getMessage("test-001");
    assertThat(receivedMsg).isNotNull();
    assertThat(new String(receivedMsg.getBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"taskId\":1001,\"status\":\"READY\"}");
    assertThat(receivedMsg.getTags()).isEqualTo("CREATE");
    assertThat(receivedMsg.getKeys()).isEqualTo("test-001");
  }

  @Test
  @DisplayName("应该正确映射消息元数据 (KEYS, TAGS, UserProperties)")
  void shouldMapMessageMetadataCorrectly() {
    // 准备
    OutboxMessage outboxMsg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .dedupKey("test-002")
            .opType("UPDATE")
            .partitionKey("partition-A")
            .headersJson("{\"traceId\":\"trace-123\",\"userId\":\"user-456\"}")
            .build();

    // 执行
    publisher.publish(outboxMsg, null);

    // 断言
    await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(messageCollector.hasMessage("test-002")).isTrue());

    MessageExt msg = messageCollector.getMessage("test-002");

    // 验证 KEYS (dedupKey)
    assertThat(msg.getKeys()).isEqualTo("test-002");

    // 验证 TAGS (opType)
    assertThat(msg.getTags()).isEqualTo("UPDATE");

    // 验证 UserProperties (headers + partitionKey)
    assertThat(msg.getUserProperty("traceId")).isEqualTo("trace-123");
    assertThat(msg.getUserProperty("userId")).isEqualTo("user-456");
    assertThat(msg.getUserProperty("partitionKey")).isEqualTo("partition-A");
  }

  @Test
  @DisplayName("应该支持批量消息发送")
  void shouldSendBatchMessages() {
    // 准备
    int batchSize = 5;

    // 执行: 发送多条消息
    for (int i = 0; i < batchSize; i++) {
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("test-batch-" + i)
              .payloadJson("{\"seq\":" + i + "}")
              .build();
      publisher.publish(msg, null);
    }

    // 断言: 等待所有消息被接收
    await()
        .atMost(15, SECONDS)
        .untilAsserted(
            () -> assertThat(messageCollector.getMessageCount()).isGreaterThanOrEqualTo(batchSize));

    // 验证每条消息都收到了
    for (int i = 0; i < batchSize; i++) {
      assertThat(messageCollector.hasMessage("test-batch-" + i)).isTrue();
    }
  }

  @Test
  @DisplayName("应该使用顺序发送模式发送带 partitionKey 的消息")
  void shouldSendOrderlyMessageWithPartitionKey() {
    // 准备: 同一 partitionKey 的多条消息
    String partitionKey = "order-partition-A";
    int messageCount = 3;

    // 执行: 发送多条具有相同 partitionKey 的消息
    for (int i = 0; i < messageCount; i++) {
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("orderly-msg-" + i)
              .partitionKey(partitionKey) // 相同 partitionKey
              .payloadJson("{\"seq\":" + i + ",\"orderId\":\"ORDER-001\"}")
              .build();
      publisher.publish(msg, null);
    }

    // 断言: 所有消息都被接收
    await()
        .atMost(15, SECONDS)
        .untilAsserted(
            () ->
                assertThat(messageCollector.getMessageCount())
                    .isGreaterThanOrEqualTo(messageCount));

    // 验证每条消息都收到了
    for (int i = 0; i < messageCount; i++) {
      assertThat(messageCollector.hasMessage("orderly-msg-" + i)).isTrue();
      MessageExt msg = messageCollector.getMessage("orderly-msg-" + i);
      // 验证 partitionKey 保存到 UserProperties
      assertThat(msg.getUserProperty("partitionKey")).isEqualTo(partitionKey);
    }
  }

  @Test
  @DisplayName("应该正确处理空 payload（使用占位符）")
  void shouldHandleEmptyPayload() {
    // 准备: 空 payload 的消息
    OutboxMessage msg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .dedupKey("empty-payload-test")
            .payloadJson(null) // 空 payload
            .build();

    // 执行
    publisher.publish(msg, null);

    // 断言: 等待消息被接收
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> assertThat(messageCollector.hasMessage("empty-payload-test")).isTrue());

    // 验证: 空 payload 被替换为占位符 "{}"
    MessageExt receivedMsg = messageCollector.getMessage("empty-payload-test");
    String body = new String(receivedMsg.getBody(), StandardCharsets.UTF_8);
    assertThat(body).isEqualTo("{}"); // 验证占位符
  }

  @Test
  @DisplayName("应该支持发送无 TAGS 的消息")
  void shouldHandleMessageWithoutTags() {
    // 准备: 无 TAGS (opType 为空字符串)
    OutboxMessage msg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .dedupKey("no-tags-test")
            .opType("") // 无 TAGS - 使用空字符串
            .payloadJson("{\"data\":\"test\"}")
            .build();

    // 执行
    publisher.publish(msg, null);

    // 断言: 等待消息被接收
    await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(messageCollector.hasMessage("no-tags-test")).isTrue());

    // 验证: TAGS 为空或 null
    MessageExt receivedMsg = messageCollector.getMessage("no-tags-test");
    // RocketMQ 中无 TAGS 时，getTags() 返回空字符串
    assertThat(receivedMsg.getTags()).isNullOrEmpty();
  }

  @Test
  @DisplayName("应该正确处理空 headers")
  void shouldHandleEmptyHeaders() {
    // 准备: 空 headers
    OutboxMessage msg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .dedupKey("empty-headers-test")
            .headersJson(null) // 空 headers
            .payloadJson("{\"data\":\"test\"}")
            .build();

    // 执行
    publisher.publish(msg, null);

    // 断言: 等待消息被接收
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> assertThat(messageCollector.hasMessage("empty-headers-test")).isTrue());

    // 验证: 消息成功发送（不应有自定义 UserProperties，除了默认的 channel）
    MessageExt receivedMsg = messageCollector.getMessage("empty-headers-test");
    assertThat(receivedMsg).isNotNull();
    // 验证只有系统添加的 channel 属性
    assertThat(receivedMsg.getUserProperty("channel")).isEqualTo("TASK_READY");
  }

  // ==================== 内部类：消息收集器 ====================

  /// 测试用消息收集器（仅用于本集成测试）。
  ///
  /// 继承 {@link com.patra.ingest.testutil.RocketMQMessageCollector}，配合 Spring 管理的
  /// `@RocketMQMessageListener` 自动收集测试消息。
  ///
  /// ### 设计考虑
  ///
  /// - **Spring 管理生命周期**: 避免手动创建 Consumer 的初始化延迟问题
  ///   - **按需启用**: 通过 @ConditionalOnProperty 控制是否启用（仅在集成测试中启用）
  ///   - **代码复用**: 继承统一的 RocketMQMessageCollector，避免重复实现
  ///   - **高内聚**: 作为测试类的内部类，明确其作用域仅限于此测试
  ///
  @Slf4j
  @Component
  @ConditionalOnProperty(
      name = "patra.ingest.test.rocketmq.consumer.enabled",
      havingValue = "true",
      matchIfMissing = false // 默认禁用（仅在集成测试中启用）
      )
  @RocketMQMessageListener(
      topic = "${patra.ingest.mq.topics.task-ready:INGEST_TASK_READY}",
      consumerGroup = "test-consumer-group-integration",
      selectorExpression = "*" // 接收所有 TAGS
      )
  static class MessageCollector extends com.patra.ingest.testutil.RocketMQMessageCollector
      implements RocketMQListener<MessageExt> {

    @Override
    public void onMessage(MessageExt message) {
      String keys = message.getKeys();
      String topic = message.getTopic();
      String msgId = message.getMsgId();

      log.info("✅ 消息收集器收到消息: topic={}, keys={}, msgId={}", topic, keys, msgId);

      // 存储消息（如果没有 KEYS，会使用 MsgId）
      if (keys == null || keys.isEmpty()) {
        log.warn("收到没有 KEYS 的消息，msgId={}", msgId);
      }

      // 委托给父类处理
      collect(message);
    }
  }
}
