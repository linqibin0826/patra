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
import com.patra.ingest.testutil.TestRocketMQConsumer;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * RocketMQ Outbox 发布器集成测试（使用 Spring 管理的 Consumer）。
 *
 * <p>本测试使用 Spring 管理的 {@link TestRocketMQConsumer} 代替手动创建的 Consumer，
 * 避免手动创建 Consumer 在 Spring Boot 上下文中的初始化延迟问题。
 *
 * <h3>与 RocketMqOutboxPublisherIT 的区别</h3>
 *
 * <ul>
 *   <li>❌ 旧方案: 手动创建 DefaultMQPushConsumer，存在初始化延迟问题
 *   <li>✅ 新方案: 使用 Spring 管理的 @RocketMQMessageListener，自动初始化
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 * @see TestRocketMQConsumer
 * @see RocketMqOutboxPublisher
 */
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
@DisplayName("RocketMQ Outbox 发布器集成测试（Spring Consumer）")
@org.springframework.test.context.ActiveProfiles("integration-test")
@org.springframework.test.annotation.DirtiesContext
class RocketMqOutboxPublisherSpringConsumerIT {

  @Autowired private RocketMqOutboxPublisher publisher;

  /** Mock 对象存储模板 */
  @MockBean private com.patra.starter.objectstorage.ObjectStorageTemplate objectStorageTemplate;

  /** 测试用的 Spring 管理 Consumer */
  @Autowired private TestRocketMQConsumer testConsumer;

  @BeforeEach
  void setUp() {
    testConsumer.clear();
  }

  @AfterEach
  void tearDown() {
    testConsumer.clear();
  }

  @Test
  @DisplayName("应该成功发送普通消息并被 Spring Consumer 接收")
  void shouldSendAndReceiveNormalMessage() {
    // 准备
    OutboxMessage outboxMsg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .channel(MessageChannels.TASK_READY)
            .dedupKey("test-spring-001")
            .opType("CREATE")
            .payloadJson("{\"taskId\":1001,\"status\":\"READY\"}")
            .build();

    // 执行
    publisher.publish(outboxMsg, null);

    // 断言: 等待消息被接收 (最多 10 秒)
    await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(testConsumer.hasMessage("test-spring-001")).isTrue());

    // 验证消息内容
    MessageExt receivedMsg = testConsumer.getMessage("test-spring-001");
    assertThat(receivedMsg).isNotNull();
    assertThat(new String(receivedMsg.getBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"taskId\":1001,\"status\":\"READY\"}");
    assertThat(receivedMsg.getTags()).isEqualTo("CREATE");
    assertThat(receivedMsg.getKeys()).isEqualTo("test-spring-001");
  }

  @Test
  @DisplayName("应该正确映射消息元数据 (KEYS, TAGS, UserProperties)")
  void shouldMapMessageMetadataCorrectly() {
    // 准备
    OutboxMessage outboxMsg =
        OutboxMessageTestBuilder.aValidPendingMessage()
            .dedupKey("test-spring-002")
            .opType("UPDATE")
            .partitionKey("partition-A")
            .headersJson("{\"traceId\":\"trace-123\",\"userId\":\"user-456\"}")
            .build();

    // 执行
    publisher.publish(outboxMsg, null);

    // 断言
    await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(testConsumer.hasMessage("test-spring-002")).isTrue());

    MessageExt msg = testConsumer.getMessage("test-spring-002");

    // 验证 KEYS (dedupKey)
    assertThat(msg.getKeys()).isEqualTo("test-spring-002");

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
              .dedupKey("test-spring-batch-" + i)
              .payloadJson("{\"seq\":" + i + "}")
              .build();
      publisher.publish(msg, null);
    }

    // 断言: 等待所有消息被接收
    await()
        .atMost(15, SECONDS)
        .untilAsserted(
            () ->
                assertThat(testConsumer.getMessageCount())
                    .isGreaterThanOrEqualTo(batchSize));

    // 验证每条消息都收到了
    for (int i = 0; i < batchSize; i++) {
      assertThat(testConsumer.hasMessage("test-spring-batch-" + i)).isTrue();
    }
  }
}
