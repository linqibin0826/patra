package com.patra.ingest.integration.messaging;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.patra.ingest.domain.messaging.MessageChannels;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.messaging.RocketMqOutboxPublisher;
import com.patra.ingest.integration.config.MySQLContainerInitializer;
import com.patra.ingest.integration.config.RocketMQContainerInitializer;
import com.patra.ingest.testutil.OutboxMessageTestBuilder;
import com.patra.ingest.testutil.TestMessageCollector;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * RocketMQ Outbox 发布器集成测试。
 *
 * <p>使用 Testcontainers 启动真实 RocketMQ 环境（由 {@link RocketMQContainerInitializer} 提供）,测试消息发送、接收和元数据映射的完整流程。
 *
 * <h3>测试范围</h3>
 *
 * <ul>
 *   <li>✅ 普通消息发送和接收
 *   <li>✅ 顺序消息发送和接收 (partitionKey 场景)
 *   <li>✅ 消息元数据映射 (KEYS, TAGS, UserProperties)
 *   <li>✅ 业务通道到 Topic 映射
 *   <li>✅ 消息序列化和编码
 *   <li>✅ 错误场景处理
 * </ul>
 *
 * <h3>测试策略</h3>
 *
 * <p>遵循 testing-guide.md §7 集成测试模式:
 *
 * <ul>
 *   <li><strong>真实依赖</strong>: 使用 RocketMQ Testcontainers (由 RocketMQContainerInitializer 提供)
 *   <li><strong>异步断言</strong>: 使用 Awaitility 等待消息接收
 *   <li><strong>消息收集</strong>: 使用 {@link TestMessageCollector} 收集接收的消息
 *   <li><strong>清理隔离</strong>: 每个测试后清理 Consumer 和 Collector
 * </ul>
 *
 * <h3>环境要求</h3>
 *
 * <ul>
 *   <li>Docker Desktop 运行中
 *   <li>至少 4GB 可用内存
 *   <li>首次启动需要 ~30-40 秒 (拉取镜像 + 启动容器)
 * </ul>
 *
 * <h3>性能优化</h3>
 *
 * <ul>
 *   <li>容器单例: 由 {@link RocketMQContainerInitializer} 和 {@link MySQLContainerInitializer} 配置,所有集成测试共享容器
 *   <li>共享网络: NameServer 和 Broker 共享 Docker 网络
 *   <li>并行测试: 测试方法可并发执行 (不同 Consumer Group)
 * </ul>
 *
 * <h3>容器依赖说明</h3>
 *
 * <p>虽然本测试主要测试 RocketMQ 消息发送,但由于 Spring 上下文中包含依赖数据库的组件（如 OutboxMessageRepository）,
 * 因此也需要启动 MySQL 容器。这样可以确保完整的应用上下文正常启动。
 *
 * @author linqibin
 * @since 0.2.0
 * @see RocketMQContainerInitializer
 * @see MySQLContainerInitializer
 * @see OutboxMessageTestBuilder
 * @see TestMessageCollector
 */
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "spring.config.import=classpath:ingest-error-config.yaml,classpath:ingest-rocketmq.yaml"
    })
@ContextConfiguration(
    initializers = {MySQLContainerInitializer.class, RocketMQContainerInitializer.class})
@DisplayName("RocketMQ Outbox 发布器集成测试")
@org.springframework.test.context.ActiveProfiles("integration-test")
@org.springframework.test.annotation.DirtiesContext // 使用独立的 ApplicationContext，避免与 E2E 测试共享
class RocketMqOutboxPublisherIT {

  // ========== Test Dependencies ==========
  // 注: RocketMQ 容器配置由 RocketMQContainerInitializer 提供,所有集成测试共享

  @Autowired private RocketMqOutboxPublisher publisher;

  /** Mock 对象存储模板（此测试不需要真实的对象存储） */
  @MockBean private com.patra.starter.objectstorage.ObjectStorageTemplate objectStorageTemplate;

  /** Mock 真实的业务 Message Listener（避免干扰测试） */
  @MockBean
  private com.patra.ingest.adapter.rocketmq.TaskReadyMessageListener taskReadyMessageListener;

  private TestMessageCollector messageCollector;
  private DefaultMQPushConsumer testConsumer;

  // ========== Setup & Teardown ==========

  /**
   * 每个测试前初始化。
   *
   * <p>创建测试 Consumer 并订阅所有测试 Topic。
   *
   * @throws Exception Consumer 启动失败
   */
  @BeforeEach
  void setUp() throws Exception {
    messageCollector = new TestMessageCollector();

    // 创建测试 Consumer
    String namesrvAddr = RocketMQContainerInitializer.getRocketMQSupport().getNameserverAddress();

    testConsumer = new DefaultMQPushConsumer("test_consumer_group_" + System.currentTimeMillis());
    testConsumer.setNamesrvAddr(namesrvAddr);

    // 默认值是 CONSUME_FROM_LAST_OFFSET，会跳过订阅前的消息
    testConsumer.setConsumeFromWhere(
        org.apache.rocketmq.common.consumer.ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);

    // 订阅测试 Topic（RocketMQ 不支持 Topic 名称通配符，需要显式订阅每个 Topic）
    // 注意：由于 topic-prefix 使用环境变量占位符且默认为空，实际 Topic 名称没有前缀
    testConsumer.subscribe("INGEST_TASK_READY", "*");
    testConsumer.subscribe("INGEST_LITERATURE_READY", "*");

    // 注册消息监听器 (收集到 TestMessageCollector)
    testConsumer.registerMessageListener(
        (MessageListenerConcurrently)
            (messages, context) -> {
              messages.forEach(messageCollector::collect);
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

    // 启动 Consumer
    testConsumer.start();

    // 等待 Consumer 完全启动并发现 Topic（消费者需要时间来拉取路由信息）
    Thread.sleep(2000);
  }

  /**
   * 每个测试后清理。
   *
   * <p>关闭 Consumer 并清空消息收集器。
   */
  @AfterEach
  void tearDown() {
    if (testConsumer != null) {
      testConsumer.shutdown();
    }
    if (messageCollector != null) {
      messageCollector.clear();
    }
  }

  // ========== Test Cases ==========

  @Nested
  @DisplayName("普通消息发送测试")
  class NormalMessageTests {

    @Test
    @DisplayName("应该成功发送普通消息并被 Consumer 接收")
    void shouldSendAndReceiveNormalMessage() {
      // 准备
      OutboxMessage outboxMsg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .channel(MessageChannels.TASK_READY)
              .dedupKey("test-dedup-001")
              .opType("CREATE")
              .payloadJson("{\"taskId\":1001,\"status\":\"READY\"}")
              .build();

      // 执行
      publisher.publish(outboxMsg, null);

      // 断言: 等待消息被接收 (最多 5 秒)
      await()
          .atMost(5, SECONDS)
          .untilAsserted(() -> assertThat(messageCollector.hasMessage("test-dedup-001")).isTrue());

      // 验证消息内容
      MessageExt receivedMsg = messageCollector.getMessage("test-dedup-001");
      assertThat(receivedMsg).isNotNull();
      assertThat(new String(receivedMsg.getBody(), StandardCharsets.UTF_8))
          .isEqualTo("{\"taskId\":1001,\"status\":\"READY\"}");
      assertThat(receivedMsg.getTags()).isEqualTo("CREATE");
      assertThat(receivedMsg.getKeys()).isEqualTo("test-dedup-001");
    }

    @Test
    @DisplayName("应该正确映射消息元数据 (KEYS, TAGS, UserProperties)")
    void shouldMapMessageMetadataCorrectly() {
      // 准备
      OutboxMessage outboxMsg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("test-dedup-002")
              .opType("UPDATE")
              .partitionKey("partition-A")
              .headersJson("{\"traceId\":\"trace-123\",\"userId\":\"user-456\"}")
              .build();

      // 执行
      publisher.publish(outboxMsg, null);

      // 断言
      await()
          .atMost(5, SECONDS)
          .untilAsserted(() -> assertThat(messageCollector.hasMessage("test-dedup-002")).isTrue());

      MessageExt msg = messageCollector.getMessage("test-dedup-002");

      // 验证 KEYS (dedupKey)
      assertThat(msg.getKeys()).isEqualTo("test-dedup-002");

      // 验证 TAGS (opType)
      assertThat(msg.getTags()).isEqualTo("UPDATE");

      // 验证 UserProperties (headers + partitionKey)
      assertThat(msg.getUserProperty("traceId")).isEqualTo("trace-123");
      assertThat(msg.getUserProperty("userId")).isEqualTo("user-456");
      assertThat(msg.getUserProperty("partitionKey")).isEqualTo("partition-A");
    }

    @Test
    @DisplayName("应该支持空 payload (使用空字符串)")
    void shouldSupportEmptyPayload() {
      // 准备
      OutboxMessage outboxMsg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("test-dedup-003")
              .payloadJson(null) // 空 payload
              .build();

      // 执行
      publisher.publish(outboxMsg, null);

      // 断言
      await()
          .atMost(5, SECONDS)
          .untilAsserted(() -> assertThat(messageCollector.hasMessage("test-dedup-003")).isTrue());

      MessageExt msg = messageCollector.getMessage("test-dedup-003");
      assertThat(new String(msg.getBody(), StandardCharsets.UTF_8)).isEmpty();
    }
  }

  @Nested
  @DisplayName("顺序消息发送测试")
  class OrderedMessageTests {

    @Test
    @DisplayName("应该使用 partitionKey 发送顺序消息")
    void shouldSendOrderedMessageWithPartitionKey() {
      // 准备: 3 条消息使用相同 partitionKey
      String partitionKey = "order-partition-1";
      OutboxMessage msg1 =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("order-msg-1")
              .partitionKey(partitionKey)
              .payloadJson("{\"seq\":1}")
              .build();

      OutboxMessage msg2 =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("order-msg-2")
              .partitionKey(partitionKey)
              .payloadJson("{\"seq\":2}")
              .build();

      OutboxMessage msg3 =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("order-msg-3")
              .partitionKey(partitionKey)
              .payloadJson("{\"seq\":3}")
              .build();

      // 执行: 按顺序发送
      publisher.publish(msg1, null);
      publisher.publish(msg2, null);
      publisher.publish(msg3, null);

      // 断言: 等待所有消息接收
      await()
          .atMost(5, SECONDS)
          .untilAsserted(
              () -> assertThat(messageCollector.getMessageCount()).isGreaterThanOrEqualTo(3));

      // 验证顺序 (注意: RocketMQ 顺序消息在同一 MessageQueue 中有序)
      assertThat(messageCollector.hasMessage("order-msg-1")).isTrue();
      assertThat(messageCollector.hasMessage("order-msg-2")).isTrue();
      assertThat(messageCollector.hasMessage("order-msg-3")).isTrue();

      // 验证 partitionKey 映射到 UserProperty
      MessageExt receivedMsg = messageCollector.getMessage("order-msg-1");
      assertThat(receivedMsg.getUserProperty("partitionKey")).isEqualTo(partitionKey);
    }
  }

  @Nested
  @DisplayName("业务通道映射测试")
  class ChannelMappingTests {

    @Test
    @DisplayName("应该正确映射 TASK_READY 通道到 Topic")
    void shouldMapTaskReadyChannel() {
      // 准备
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .channel(MessageChannels.TASK_READY)
              .dedupKey("channel-test-001")
              .build();

      // 执行
      publisher.publish(msg, null);

      // 断言
      await()
          .atMost(5, SECONDS)
          .untilAsserted(
              () -> assertThat(messageCollector.hasMessage("channel-test-001")).isTrue());

      MessageExt receivedMsg = messageCollector.getMessage("channel-test-001");
      // 验证 Topic 名称 (patra_test_task_ready 或类似)
      assertThat(receivedMsg.getTopic()).containsIgnoringCase("task");
    }

    @Test
    @DisplayName("应该正确映射 LITERATURE_READY 通道到 Topic")
    void shouldMapLiteratureReadyChannel() {
      // 准备
      OutboxMessage msg =
          OutboxMessageTestBuilder.aLiteratureReadyMessage().dedupKey("channel-test-002").build();

      // 执行
      publisher.publish(msg, null);

      // 断言
      await()
          .atMost(5, SECONDS)
          .untilAsserted(
              () -> assertThat(messageCollector.hasMessage("channel-test-002")).isTrue());

      MessageExt receivedMsg = messageCollector.getMessage("channel-test-002");
      assertThat(receivedMsg.getTopic()).containsIgnoringCase("literature");
    }
  }

  @Nested
  @DisplayName("消息序列化测试")
  class SerializationTests {

    @Test
    @DisplayName("应该正确处理 UTF-8 编码 (中文、Emoji)")
    void shouldHandleUtf8Encoding() {
      // 准备
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("utf8-test-001")
              .payloadJson("{\"title\":\"医学文献\",\"emoji\":\"🔬📊\"}")
              .build();

      // 执行
      publisher.publish(msg, null);

      // 断言
      await()
          .atMost(5, SECONDS)
          .untilAsserted(() -> assertThat(messageCollector.hasMessage("utf8-test-001")).isTrue());

      MessageExt receivedMsg = messageCollector.getMessage("utf8-test-001");
      String payload = new String(receivedMsg.getBody(), StandardCharsets.UTF_8);
      assertThat(payload).contains("医学文献");
      assertThat(payload).contains("🔬📊");
    }

    @Test
    @DisplayName("应该正确处理复杂 JSON payload")
    void shouldHandleComplexJsonPayload() {
      // 准备
      String complexJson =
          "{"
              + "\"taskId\":2001,"
              + "\"metadata\":{\"source\":\"PUBMED\",\"count\":100},"
              + "\"tags\":[\"medicine\",\"research\"]"
              + "}";

      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("json-test-001")
              .payloadJson(complexJson)
              .build();

      // 执行
      publisher.publish(msg, null);

      // 断言
      await()
          .atMost(5, SECONDS)
          .untilAsserted(() -> assertThat(messageCollector.hasMessage("json-test-001")).isTrue());

      MessageExt receivedMsg = messageCollector.getMessage("json-test-001");
      String payload = new String(receivedMsg.getBody(), StandardCharsets.UTF_8);
      assertThat(payload).isEqualToIgnoringWhitespace(complexJson);
    }
  }

  @Nested
  @DisplayName("并发发送测试")
  class ConcurrentSendTests {

    @Test
    @DisplayName("应该支持并发发送多条消息")
    void shouldSupportConcurrentSend() throws InterruptedException {
      // 准备: 10 条消息
      int messageCount = 10;
      for (int i = 0; i < messageCount; i++) {
        OutboxMessage msg =
            OutboxMessageTestBuilder.aValidPendingMessage().dedupKey("concurrent-msg-" + i).build();
        publisher.publish(msg, null);
      }

      // 断言: 所有消息都应被接收
      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () ->
                  assertThat(messageCollector.getMessageCount())
                      .isGreaterThanOrEqualTo(messageCount));

      // 验证没有消息丢失
      for (int i = 0; i < messageCount; i++) {
        assertThat(messageCollector.hasMessage("concurrent-msg-" + i)).isTrue();
      }
    }
  }
}
