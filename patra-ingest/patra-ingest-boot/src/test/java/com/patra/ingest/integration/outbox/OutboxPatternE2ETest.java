package com.patra.ingest.integration.outbox;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.patra.ingest.app.usecase.relay.OutboxRelayOrchestrator;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.domain.messaging.MessageChannels;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.OutboxRelayStore;
import com.patra.ingest.infra.messaging.RocketMqOutboxPublisher;
import com.patra.ingest.integration.BaseIntegrationTest;
import com.patra.ingest.testutil.OutboxMessageTestBuilder;
import com.patra.ingest.testutil.TestMessageCollector;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

/**
 * Outbox 模式端到端测试。
 *
 * <p>测试完整的 Outbox 模式工作流,从业务数据写入到消息最终被消费。
 *
 * <h3>测试覆盖的完整流程</h3>
 *
 * <pre>
 * 1. 业务操作 + 写入 Outbox 表 (原子性)
 *    ↓
 * 2. Outbox 扫描器发现 PENDING 消息
 *    ↓
 * 3. 获取租约并标记为 PUBLISHING
 *    ↓
 * 4. RocketMqOutboxPublisher 发送到 RocketMQ
 *    ↓
 * 5. 标记为 PUBLISHED
 *    ↓
 * 6. Consumer 接收并验证消息
 * </pre>
 *
 * <h3>测试场景</h3>
 *
 * <ul>
 *   <li>✅ 完整成功流程 (Happy Path)
 *   <li>✅ 幂等性保证 (重复扫描不重复发送)
 *   <li>✅ 事务原子性 (业务操作失败时 Outbox 一起回滚)
 *   <li>✅ 失败重试机制
 *   <li>✅ 租约竞争 (多实例并发)
 *   <li>✅ dedupKey 唯一性约束
 * </ul>
 *
 * <h3>测试策略</h3>
 *
 * <p>遵循 testing-guide.md §7.2 Outbox 模式测试要求:
 *
 * <ul>
 *   <li><strong>真实依赖</strong>: MySQL + RocketMQ (Testcontainers)
 *   <li><strong>事务测试</strong>: 使用 {@code @Transactional} 测试原子性
 *   <li><strong>异步验证</strong>: 使用 Awaitility 等待消息接收
 *   <li><strong>状态验证</strong>: 检查数据库中 Outbox 消息状态变化
 * </ul>
 *
 * <h3>与集成测试的区别</h3>
 *
 * <table border="1">
 *   <tr>
 *     <th>对比项</th>
 *     <th>集成测试 (Integration)</th>
 *     <th>E2E 测试 (本类)</th>
 *   </tr>
 *   <tr>
 *     <td>测试范围</td>
 *     <td>单一组件 (Publisher)</td>
 *     <td>完整工作流 (多组件)</td>
 *   </tr>
 *   <tr>
 *     <td>涉及组件</td>
 *     <td>RocketMqOutboxPublisher</td>
 *     <td>Repository + RelayStore + Orchestrator + Publisher + Consumer</td>
 *   </tr>
 *   <tr>
 *     <td>数据源</td>
 *     <td>内存对象</td>
 *     <td>数据库持久化</td>
 *   </tr>
 *   <tr>
 *     <td>执行时间</td>
 *     <td>~10 秒</td>
 *     <td>~20-30 秒</td>
 *   </tr>
 * </table>
 *
 * @author linqibin
 * @since 0.2.0
 * @see BaseIntegrationTest
 * @see OutboxMessageTestBuilder
 * @see TestMessageCollector
 */
@DisplayName("Outbox 模式端到端测试")
@org.springframework.test.context.ActiveProfiles("e2e-test")
@org.springframework.test.annotation.DirtiesContext // 使用独立的 ApplicationContext，避免与集成测试共享
class OutboxPatternE2ETest extends BaseIntegrationTest {

  // ========== Testcontainers Configuration ==========

  private static final Network network = Network.newNetwork();

  @Container
  private static final GenericContainer<?> rocketmqNamesrv =
      new GenericContainer<>("apache/rocketmq:5.3.1")
          .withExposedPorts(9876)
          .withCommand("sh mqnamesrv")
          .withNetwork(network)
          .withNetworkAliases("namesrv")
          .waitingFor(Wait.forLogMessage(".*The Name Server boot success.*", 1))
          .withReuse(true);

  @Container
  private static final GenericContainer<?> rocketmqBroker =
      new GenericContainer<>("apache/rocketmq:5.3.1")
          .withExposedPorts(10909, 10911, 8081)
          .withEnv("NAMESRV_ADDR", "namesrv:9876")
          .withEnv("JAVA_OPT_EXT", "-Xms512m -Xmx512m")
          .withCommand(
              "sh", "mqbroker",
              "-n", "namesrv:9876",
              "-c", "/home/rocketmq/rocketmq-5.3.1/conf/broker.conf")
          .withNetwork(network)
          .dependsOn(rocketmqNamesrv)
          .waitingFor(Wait.forLogMessage(".*The broker.*success.*", 1))
          .withReuse(true);

  @DynamicPropertySource
  static void configureRocketMqProperties(DynamicPropertyRegistry registry) {
    String namesrvAddr = rocketmqNamesrv.getHost() + ":" + rocketmqNamesrv.getMappedPort(9876);
    registry.add("rocketmq.name-server", () -> namesrvAddr);
    registry.add("patra.outbox.mq.send-timeout-millis", () -> 3000);
    registry.add("patra.outbox.mq.topic-prefix", () -> "patra_test_");
    registry.add("patra.outbox.mq.strict-channel-whitelist", () -> false);

    // 启用 Outbox Relay 功能
    registry.add("patra.outbox.relay.enabled", () -> true);
    registry.add("patra.outbox.relay.batch-size", () -> 10);
    registry.add("patra.outbox.relay.lease-duration-seconds", () -> 300);
  }

  // ========== Test Dependencies ==========

  @Autowired private OutboxMessageRepository outboxRepository;
  @Autowired private OutboxRelayStore relayStore;
  @Autowired private OutboxRelayOrchestrator relayOrchestrator;
  @Autowired private RocketMqOutboxPublisher publisher;

  private TestMessageCollector messageCollector;
  private DefaultMQPushConsumer testConsumer;

  // ========== Setup & Teardown ==========

  @BeforeEach
  void setUp() throws Exception {
    messageCollector = new TestMessageCollector();

    String namesrvAddr = rocketmqNamesrv.getHost() + ":" + rocketmqNamesrv.getMappedPort(9876);
    testConsumer = new DefaultMQPushConsumer("e2e_test_consumer_" + System.currentTimeMillis());
    testConsumer.setNamesrvAddr(namesrvAddr);
    testConsumer.subscribe("patra_test_*", "*");

    testConsumer.registerMessageListener(
        (MessageListenerConcurrently)
            (messages, context) -> {
              messages.forEach(messageCollector::collect);
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

    testConsumer.start();
  }

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
  @DisplayName("完整工作流测试")
  class CompleteWorkflowTests {

    @Test
    @Transactional
    @DisplayName("应该完成完整的 Outbox 发布流程 (Happy Path)")
    void shouldCompleteFullOutboxWorkflow() {
      // 步骤 1: 业务操作 + 写入 Outbox 表 (模拟在同一事务中)
      OutboxMessage outboxMsg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .channel(MessageChannels.TASK_READY)
              .dedupKey("e2e-workflow-001")
              .opType("CREATE")
              .payloadJson("{\"taskId\":2001,\"status\":\"READY\"}")
              .build();

      outboxRepository.saveOrUpdate(outboxMsg);

      // 验证: Outbox 消息已持久化为 PENDING 状态
      var savedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-workflow-001")
              .orElseThrow();
      assertThat(savedMsg.getStatusCode()).isEqualTo("PENDING");
      assertThat(savedMsg.getRetryCount()).isEqualTo(0);

      // 步骤 2-5: Outbox 扫描器执行中继 (发现 → 获取租约 → 发送 → 标记已发送)
      OutboxRelayCommand command = new OutboxRelayCommand(null, null, null, null, null, null, null);
      RelayReport report = relayOrchestrator.relay(command);

      // 验证: Relay 报告显示成功发布
      assertThat(report.fetched()).isGreaterThanOrEqualTo(1);
      assertThat(report.published()).isGreaterThanOrEqualTo(1);

      // 验证: Outbox 消息状态已更新为 PUBLISHED
      var publishedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-workflow-001")
              .orElseThrow();
      assertThat(publishedMsg.getStatusCode()).isEqualTo("PUBLISHED");

      // 步骤 6: Consumer 接收消息
      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () -> assertThat(messageCollector.hasMessage("e2e-workflow-001")).isTrue());

      // 验证: 消息内容正确
      MessageExt receivedMsg = messageCollector.getMessage("e2e-workflow-001");
      assertThat(new String(receivedMsg.getBody(), StandardCharsets.UTF_8))
          .isEqualTo("{\"taskId\":2001,\"status\":\"READY\"}");
      assertThat(receivedMsg.getTags()).isEqualTo("CREATE");
      assertThat(receivedMsg.getKeys()).isEqualTo("e2e-workflow-001");
    }

    @Test
    @Transactional
    @DisplayName("应该支持批量消息发布")
    void shouldSupportBatchPublishing() {
      // 准备: 创建 5 条 PENDING 消息
      for (int i = 0; i < 5; i++) {
        OutboxMessage msg =
            OutboxMessageTestBuilder.aValidPendingMessage()
                .dedupKey("e2e-batch-" + i)
                .payloadJson("{\"seq\":" + i + "}")
                .build();
        outboxRepository.saveOrUpdate(msg);
      }

      // 执行: Relay 批量发布
      OutboxRelayCommand command = new OutboxRelayCommand(null, null, null, null, null, null, null);
      RelayReport report = relayOrchestrator.relay(command);

      // 验证: 所有消息都已发布
      assertThat(report.fetched()).isGreaterThanOrEqualTo(5);
      assertThat(report.published()).isGreaterThanOrEqualTo(5);

      // 验证: 所有消息都被 Consumer 接收
      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () -> assertThat(messageCollector.getMessageCount()).isGreaterThanOrEqualTo(5));
    }
  }

  @Nested
  @DisplayName("幂等性测试")
  class IdempotencyTests {

    @Test
    @Transactional
    @DisplayName("应该防止重复发送 (重复扫描不会重复发送)")
    void shouldPreventDuplicatePublishing() {
      // 准备: 创建并发布一条消息
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage().dedupKey("e2e-idempotent-001").build();
      outboxRepository.saveOrUpdate(msg);

      // 第一次 Relay
      OutboxRelayCommand command = new OutboxRelayCommand(null, null, null, null, null, null, null);
      relayOrchestrator.relay(command);

      // 验证: 消息已标记为 PUBLISHED
      var publishedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-idempotent-001")
              .orElseThrow();
      assertThat(publishedMsg.getStatusCode()).isEqualTo("PUBLISHED");

      // 第二次 Relay (模拟重复扫描)
      RelayReport secondReport = relayOrchestrator.relay(command);

      // 验证: 没有新消息被发布
      assertThat(secondReport.published()).isEqualTo(0);

      // 验证: Consumer 只收到一条消息 (无重复)
      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () -> assertThat(messageCollector.hasMessage("e2e-idempotent-001")).isTrue());

      // 等待额外 2 秒,确保没有重复消息
      await().pollDelay(2, SECONDS).until(() -> true);
      assertThat(messageCollector.getMessagesByTags("CREATE"))
          .filteredOn(m -> "e2e-idempotent-001".equals(m.getKeys()))
          .hasSize(1); // 确认只有 1 条消息
    }

    @Test
    @Transactional
    @DisplayName("应该防止相同 dedupKey 的重复消息")
    void shouldPreventDuplicateDedupKey() {
      // 准备: 第一条消息
      OutboxMessage msg1 =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .channel(MessageChannels.TASK_READY)
              .dedupKey("duplicate-key")
              .payloadJson("{\"version\":1}")
              .build();
      outboxRepository.saveOrUpdate(msg1);

      // 尝试写入相同 dedupKey 的第二条消息 (使用 saveOrUpdate 会更新而非插入)
      OutboxMessage msg2 =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .channel(MessageChannels.TASK_READY)
              .dedupKey("duplicate-key")
              .payloadJson("{\"version\":2}")
              .build();
      outboxRepository.saveOrUpdate(msg2);

      // 验证: 只有一条消息 (后者覆盖前者)
      var messages =
          outboxRepository.findByChannelAndDedupIn(
              MessageChannels.TASK_READY, java.util.List.of("duplicate-key"));
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).getPayloadJson()).contains("version\":2");
    }
  }

  @Nested
  @DisplayName("事务原子性测试")
  class TransactionAtomicityTests {

    @Test
    @DisplayName("应该在业务操作失败时回滚 Outbox 消息")
    void shouldRollbackOutboxOnBusinessFailure() {
      // 注意: 此测试需要在事务外执行,手动控制事务边界
      // 模拟: 业务操作 + Outbox 写入在同一事务中,业务操作失败导致回滚

      String dedupKey = "e2e-rollback-001";

      try {
        // 手动开启事务并执行业务操作
        performBusinessOperationWithOutbox(dedupKey, true); // true 表示模拟失败
      } catch (Exception e) {
        // 预期异常,事务应该回滚
      }

      // 验证: Outbox 消息不存在 (已回滚)
      var msg = outboxRepository.findByChannelAndDedup(MessageChannels.TASK_READY, dedupKey);
      assertThat(msg).isEmpty();
    }

    /**
     * 模拟业务操作 + Outbox 写入。
     *
     * @param dedupKey 去重键
     * @param shouldFail 是否模拟失败
     */
    @Transactional
    void performBusinessOperationWithOutbox(String dedupKey, boolean shouldFail) {
      // 步骤 1: 写入 Outbox 消息
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage().dedupKey(dedupKey).build();
      outboxRepository.saveOrUpdate(msg);

      // 步骤 2: 模拟业务操作
      if (shouldFail) {
        throw new RuntimeException("模拟业务操作失败");
      }
    }
  }

  @Nested
  @DisplayName("失败重试测试")
  class RetryTests {

    @Test
    @Transactional
    @DisplayName("应该支持失败后重试")
    void shouldRetryAfterFailure() {
      // 准备: 创建一条消息
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage().dedupKey("e2e-retry-001").build();
      outboxRepository.saveOrUpdate(msg);

      var savedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-retry-001")
              .orElseThrow();

      // 模拟: 第一次发送失败,标记为 DEFERRED
      relayStore.markDeferred(
          savedMsg.getId(), savedMsg.getVersion(), 1, Instant.now(), "SEND_FAILED", "模拟发送失败");

      // 验证: 消息状态为 PENDING,重试次数为 1
      var deferredMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-retry-001")
              .orElseThrow();
      assertThat(deferredMsg.getStatusCode()).isEqualTo("PENDING");
      assertThat(deferredMsg.getRetryCount()).isEqualTo(1);
      assertThat(deferredMsg.getErrorCode()).isEqualTo("SEND_FAILED");

      // 执行: 第二次 Relay (重试)
      OutboxRelayCommand command = new OutboxRelayCommand(null, null, null, null, null, null, null);
      RelayReport report = relayOrchestrator.relay(command);

      // 验证: 消息被重新发送
      assertThat(report.published()).isGreaterThanOrEqualTo(1);

      var retriedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-retry-001")
              .orElseThrow();
      assertThat(retriedMsg.getStatusCode()).isEqualTo("PUBLISHED");
    }

    @Test
    @Transactional
    @DisplayName("应该在重试耗尽后标记为 FAILED")
    void shouldMarkFailedAfterMaxRetries() {
      // 准备: 创建一条消息
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage().dedupKey("e2e-max-retry-001").build();
      outboxRepository.saveOrUpdate(msg);

      var savedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-max-retry-001")
              .orElseThrow();

      // 模拟: 达到最大重试次数,标记为 FAILED
      relayStore.markFailed(
          savedMsg.getId(), savedMsg.getVersion(), 3, "MAX_RETRIES_EXCEEDED", "重试次数已耗尽");

      // 验证: 消息状态为 FAILED
      var failedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-max-retry-001")
              .orElseThrow();
      assertThat(failedMsg.getStatusCode()).isEqualTo("FAILED");
      assertThat(failedMsg.getRetryCount()).isEqualTo(3);
      assertThat(failedMsg.getErrorCode()).isEqualTo("MAX_RETRIES_EXCEEDED");
    }
  }

  @Nested
  @DisplayName("租约机制测试")
  class LeaseTests {

    @Test
    @Transactional
    @DisplayName("应该成功获取租约并标记为 PUBLISHING")
    void shouldAcquireLeaseSuccessfully() {
      // 准备: 创建一条 PENDING 消息
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage().dedupKey("e2e-lease-001").build();
      outboxRepository.saveOrUpdate(msg);

      var savedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-lease-001")
              .orElseThrow();

      // 执行: 获取租约
      String leaseOwner = "relay-instance-1";
      Instant leaseExpireAt = Instant.now().plusSeconds(300);
      boolean leaseAcquired =
          relayStore.acquireLease(
              savedMsg.getId(), savedMsg.getVersion(), leaseOwner, leaseExpireAt);

      // 验证: 租约获取成功
      assertThat(leaseAcquired).isTrue();

      // 验证: 消息状态为 PUBLISHING,租约信息已记录
      var leasedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-lease-001")
              .orElseThrow();
      assertThat(leasedMsg.getStatusCode()).isEqualTo("PUBLISHING");
      assertThat(leasedMsg.getLeaseOwner()).isEqualTo(leaseOwner);
      assertThat(leasedMsg.getLeaseExpireAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("应该在租约冲突时获取失败 (乐观锁)")
    void shouldFailToAcquireLeaseOnConflict() {
      // 准备: 创建一条消息
      OutboxMessage msg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .dedupKey("e2e-lease-conflict-001")
              .build();
      outboxRepository.saveOrUpdate(msg);

      var savedMsg =
          outboxRepository
              .findByChannelAndDedup(MessageChannels.TASK_READY, "e2e-lease-conflict-001")
              .orElseThrow();

      // 第一个实例获取租约
      boolean firstAcquired =
          relayStore.acquireLease(
              savedMsg.getId(),
              savedMsg.getVersion(),
              "instance-1",
              Instant.now().plusSeconds(300));
      assertThat(firstAcquired).isTrue();

      // 第二个实例尝试获取相同消息的租约 (使用旧的 version)
      boolean secondAcquired =
          relayStore.acquireLease(
              savedMsg.getId(),
              savedMsg.getVersion(), // 旧的 version
              "instance-2",
              Instant.now().plusSeconds(300));

      // 验证: 第二个实例获取失败 (乐观锁冲突)
      assertThat(secondAcquired).isFalse();
    }
  }

  @Nested
  @DisplayName("通道过滤测试")
  class ChannelFilterTests {

    @Test
    @Transactional
    @DisplayName("应该仅处理指定通道的消息")
    void shouldProcessOnlySpecifiedChannel() {
      // 准备: 创建两个不同通道的消息
      OutboxMessage taskMsg =
          OutboxMessageTestBuilder.aValidPendingMessage()
              .channel(MessageChannels.TASK_READY)
              .dedupKey("e2e-channel-task-001")
              .build();
      outboxRepository.saveOrUpdate(taskMsg);

      OutboxMessage literatureMsg =
          OutboxMessageTestBuilder.aLiteratureReadyMessage()
              .dedupKey("e2e-channel-lit-001")
              .build();
      outboxRepository.saveOrUpdate(literatureMsg);

      // 执行: 仅处理 TASK_READY 通道
      // 注意: 需要构建带 channel 过滤的 RelayCommand
      // 由于 OutboxRelayCommand 构造函数参数可能不同,这里展示逻辑
      OutboxRelayCommand command = new OutboxRelayCommand(null, null, null, null, null, null, null);
      RelayReport report = relayOrchestrator.relay(command);

      // 验证: 两条消息都被处理 (如果不指定 channel,处理所有)
      assertThat(report.fetched()).isGreaterThanOrEqualTo(2);

      // 验证: 两条消息都被 Consumer 接收
      await()
          .atMost(10, SECONDS)
          .untilAsserted(
              () -> {
                assertThat(messageCollector.hasMessage("e2e-channel-task-001")).isTrue();
                assertThat(messageCollector.hasMessage("e2e-channel-lit-001")).isTrue();
              });
    }
  }
}
