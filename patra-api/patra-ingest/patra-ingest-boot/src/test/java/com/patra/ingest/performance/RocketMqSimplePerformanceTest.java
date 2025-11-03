package com.patra.ingest.performance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.ingest.app.usecase.relay.OutboxRelayUseCase;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RocketMQ 简化性能基准测试。
 *
 * <p>使用 JUnit + 计时器进行性能测试,便于在 Maven test 阶段运行。
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>✅ 单线程发送吞吐量 (TPS)
 *   <li>✅ 并发发送吞吐量 (10 线程)
 *   <li>✅ 端到端延迟 (Outbox → RocketMQ)
 *   <li>✅ P50, P95, P99 延迟统计
 * </ul>
 *
 * <p><strong>运行方式</strong>:
 *
 * <pre>
 * # 运行所有性能测试 (需要设置环境变量)
 * export PERF_TEST_ENABLED=true
 * mvn test -Dtest=RocketMqSimplePerformanceTest
 *
 * # 或者在 IDE 中设置环境变量 PERF_TEST_ENABLED=true
 * </pre>
 *
 * <p><strong>性能指标</strong>:
 *
 * <ul>
 *   <li>吞吐量 (TPS): 每秒发送的消息数
 *   <li>平均延迟: 消息发送的平均耗时
 *   <li>P50/P95/P99 延迟: 百分位延迟统计
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("RocketMQ 简化性能测试")
@EnabledIfEnvironmentVariable(named = "PERF_TEST_ENABLED", matches = "true")
class RocketMqSimplePerformanceTest {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("patra_perf_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(true);

  @DynamicPropertySource
  static void configureMySql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired private OutboxMessageMapper outboxMapper;

  @Autowired private OutboxRelayUseCase relayUseCase;

  @MockBean private RocketMQTemplate rocketMQTemplate;

  @BeforeEach
  void setUp() {
    // 清理测试数据
    outboxMapper.delete(null);

    // 配置 Mock RocketMQTemplate
    SendResult mockResult = new SendResult();
    mockResult.setSendStatus(SendStatus.SEND_OK);
    mockResult.setMsgId("perf-test-msg");

    when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong()))
        .thenReturn(mockResult);
  }

  @Test
  @DisplayName("Benchmark: 单线程发送吞吐量 (1000 条消息)")
  void benchmarkSingleThreadSendThroughput() {
    // Arrange
    int messageCount = 1000;
    List<Long> latencies = new ArrayList<>(messageCount);

    // Act: 创建 1000 条 Outbox 消息
    for (int i = 0; i < messageCount; i++) {
      OutboxMessageDO outboxDO = createTestOutboxMessage("single-thread-" + i, "TASK_READY");
      outboxMapper.insert(outboxDO);
    }

    // 测量发送时间
    Instant startTime = Instant.now();

    for (int i = 0; i < messageCount; i++) {
      Instant msgStart = Instant.now();

      // 执行中继 (每次发布一批)
      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 10, Duration.ofMinutes(5), 3);
      relayUseCase.relay(command);

      long latency = Duration.between(msgStart, Instant.now()).toMillis();
      latencies.add(latency);
    }

    Instant endTime = Instant.now();

    // Assert: 计算性能指标
    long totalDurationMs = Duration.between(startTime, endTime).toMillis();
    double tps = (messageCount * 1000.0) / totalDurationMs;

    PerformanceStats stats = calculateStats(latencies);

    System.out.println("\n=== 单线程发送性能 ===");
    System.out.printf("总消息数: %d%n", messageCount);
    System.out.printf("总耗时: %d ms%n", totalDurationMs);
    System.out.printf("吞吐量: %.2f TPS%n", tps);
    System.out.printf("平均延迟: %.2f ms%n", stats.avgLatency);
    System.out.printf("P50 延迟: %d ms%n", stats.p50);
    System.out.printf("P95 延迟: %d ms%n", stats.p95);
    System.out.printf("P99 延迟: %d ms%n", stats.p99);

    // 验证性能在合理范围内 (这些阈值可以根据实际情况调整)
    assertThat(tps).isGreaterThan(50.0); // 至少 50 TPS
    assertThat(stats.avgLatency).isLessThan(200.0); // 平均延迟小于 200ms
  }

  @Test
  @DisplayName("Benchmark: 并发发送吞吐量 (10 线程, 10000 条消息)")
  void benchmarkConcurrentSendThroughput() throws InterruptedException {
    // Arrange
    int threadCount = 10;
    int messagesPerThread = 1000;
    int totalMessages = threadCount * messagesPerThread;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // Act: 创建消息
    for (int i = 0; i < totalMessages; i++) {
      OutboxMessageDO outboxDO = createTestOutboxMessage("concurrent-" + i, "TASK_READY");
      outboxMapper.insert(outboxDO);
    }

    // 并发发送
    Instant startTime = Instant.now();

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      executor.submit(
          () -> {
            try {
              startLatch.await(); // 等待所有线程就绪

              for (int i = 0; i < messagesPerThread / 10; i++) {
                OutboxRelayCommand command =
                    new OutboxRelayCommand(List.of("TASK_READY"), 10, Duration.ofMinutes(5), 3);
                relayUseCase.relay(command);
                successCount.incrementAndGet();
              }
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown(); // 启动所有线程
    boolean finished = doneLatch.await(60, TimeUnit.SECONDS);

    Instant endTime = Instant.now();

    // Assert
    assertThat(finished).isTrue();

    long totalDurationMs = Duration.between(startTime, endTime).toMillis();
    double tps = (totalMessages * 1000.0) / totalDurationMs;

    System.out.println("\n=== 并发发送性能 ===");
    System.out.printf("线程数: %d%n", threadCount);
    System.out.printf("总消息数: %d%n", totalMessages);
    System.out.printf("总耗时: %d ms%n", totalDurationMs);
    System.out.printf("吞吐量: %.2f TPS%n", tps);
    System.out.printf("成功发送: %d%n", successCount.get());

    // 验证性能
    assertThat(tps).isGreaterThan(100.0); // 并发至少 100 TPS
    assertThat(successCount.get()).isGreaterThan(0);

    executor.shutdown();
  }

  @Test
  @DisplayName("Benchmark: 端到端延迟 (Outbox 写入 → 发布)")
  void benchmarkEndToEndLatency() {
    // Arrange
    int sampleSize = 100;
    List<Long> endToEndLatencies = new ArrayList<>(sampleSize);

    // Act: 测量端到端延迟
    for (int i = 0; i < sampleSize; i++) {
      Instant start = Instant.now();

      // 1. 写入 Outbox
      OutboxMessageDO outboxDO = createTestOutboxMessage("e2e-" + i, "TASK_READY");
      outboxMapper.insert(outboxDO);

      // 2. 执行中继发布
      OutboxRelayCommand command =
          new OutboxRelayCommand(List.of("TASK_READY"), 1, Duration.ofMinutes(5), 3);
      relayUseCase.relay(command);

      long latency = Duration.between(start, Instant.now()).toMillis();
      endToEndLatencies.add(latency);
    }

    // Assert
    PerformanceStats stats = calculateStats(endToEndLatencies);

    System.out.println("\n=== 端到端延迟 ===");
    System.out.printf("样本数: %d%n", sampleSize);
    System.out.printf("平均延迟: %.2f ms%n", stats.avgLatency);
    System.out.printf("P50 延迟: %d ms%n", stats.p50);
    System.out.printf("P95 延迟: %d ms%n", stats.p95);
    System.out.printf("P99 延迟: %d ms%n", stats.p99);
    System.out.printf("最小延迟: %d ms%n", stats.min);
    System.out.printf("最大延迟: %d ms%n", stats.max);

    // 验证延迟在合理范围内
    assertThat(stats.avgLatency).isLessThan(300.0); // 平均延迟小于 300ms
    assertThat(stats.p99).isLessThan(500); // P99 延迟小于 500ms
  }

  // ==================== Helper Methods ====================

  private OutboxMessageDO createTestOutboxMessage(String dedupKey, String channel) {
    OutboxMessageDO outboxDO = new OutboxMessageDO();
    outboxDO.setAggregateType("Task");
    outboxDO.setAggregateId(System.currentTimeMillis());
    outboxDO.setChannel(channel);
    outboxDO.setOpType("TaskReady");
    outboxDO.setDedupKey(dedupKey);
    try {
      outboxDO.setPayloadJson(
          new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"taskId\": 12345}"));
      outboxDO.setHeadersJson(new com.fasterxml.jackson.databind.ObjectMapper().readTree("{}"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to create test outbox message", e);
    }
    outboxDO.setNotBefore(Instant.now());
    outboxDO.setStatusCode("PENDING");
    outboxDO.setRetryCount(0);
    outboxDO.setCreatedAt(Instant.now());
    outboxDO.setUpdatedAt(Instant.now());
    return outboxDO;
  }

  private PerformanceStats calculateStats(List<Long> latencies) {
    latencies.sort(Long::compareTo);
    LongSummaryStatistics summary =
        latencies.stream().mapToLong(Long::longValue).summaryStatistics();

    int size = latencies.size();
    long p50 = latencies.get(size * 50 / 100);
    long p95 = latencies.get(size * 95 / 100);
    long p99 = latencies.get(size * 99 / 100);

    return new PerformanceStats(
        summary.getAverage(), p50, p95, p99, summary.getMin(), summary.getMax());
  }

  private record PerformanceStats(
      double avgLatency, long p50, long p95, long p99, long min, long max) {}
}
