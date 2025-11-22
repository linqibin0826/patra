package com.patra.ingest.integration.messaging;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.usecase.execution.TaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.integration.config.MySQLContainerInitializer;
import com.patra.ingest.integration.config.RocketMQContainerInitializer;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// 任务就绪消息监听器集成测试。
///
/// 使用 Testcontainers 启动真实 RocketMQ 环境（由 {@link RocketMQContainerInitializer} 提供）， 测试
// `TaskReadyMessageListener` 的消息消费和解析功能。
///
/// ### 测试范围
///
/// - ✅ 正常消息消费和用例调用
///   - ✅ 消息元数据提取 (KEYS, TAGS, UserProperties)
///   - ✅ 无效消息处理 (缺少必填字段)
///   - ✅ 用例执行失败处理 (异常传播)
///
/// ### 测试策略
///
/// 遵循 testing-guide.md §7 集成测试模式：
///
/// - **真实依赖**: 使用 RocketMQ Testcontainers (由 RocketMQContainerInitializer 提供)
///   - **Mock 业务用例**: 使用 @MockitoBean Mock {@link TaskExecutionUseCase}，避免执行真实业务逻辑
///   - **异步断言**: 使用 Awaitility 等待消息消费完成
///   - **参数捕获**: 使用 Mockito ArgumentCaptor 验证传递给用例的 Command 对象
///
/// ### 环境要求
///
/// - Docker Desktop 运行中
///   - 至少 4GB 可用内存
///   - 首次启动需要 ~30-40 秒 (拉取镜像 + 启动容器)
///
/// ### 容器依赖说明
///
/// 本测试需要启动 RocketMQ 和 MySQL 容器：
///
/// - RocketMQ: 用于测试消息监听器
///   - MySQL: 应用上下文依赖数据库组件（如 Repository）
///
/// @author linqibin
/// @since 0.1.0
/// @see RocketMQContainerInitializer
/// @see MySQLContainerInitializer
/// @see TaskExecutionUseCase
@Slf4j
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "spring.config.import=classpath:ingest-error-config.yaml,classpath:ingest-rocketmq.yaml",
      // 启用测试目标：TaskReadyMessageListener
      "patra.ingest.listener.task-ready.enabled=true",
      // 配置 Topic 和 Consumer Group
      "patra.ingest.mq.topics.task-ready=INGEST_TASK_READY",
      "patra.ingest.mq.consumer-groups.task-ready=test-task-ready-consumer-group"
    })
@ContextConfiguration(
    initializers = {MySQLContainerInitializer.class, RocketMQContainerInitializer.class})
@org.springframework.test.context.ActiveProfiles("integration-test")
// 移除 @DirtiesContext: 共享 ApplicationContext 以提升测试性能
// 测试隔离通过不同的 Consumer Group (test-task-ready-consumer-group) 保证
@DisplayName("任务就绪消息监听器集成测试")
class TaskReadyMessageListenerIT {

  @Autowired private RocketMQTemplate rocketMQTemplate;

  /// Mock 业务用例，避免执行真实业务逻辑
  @MockitoBean private TaskExecutionUseCase taskExecutionUseCase;

  @Autowired private ObjectMapper objectMapper;

  /// 测试用的 Topic 名称 (与配置文件保持一致)
  private static final String TOPIC = "INGEST_TASK_READY";

  @BeforeEach
  void setUp() {
    // 重置 Mock 对象
    reset(taskExecutionUseCase);
  }

  @Test
  @DisplayName("应该成功消费任务就绪消息并调用用例")
  void shouldConsumeAndProcessTaskReadyMessage() throws Exception {
    // 准备：构建消息负载
    Map<String, Object> payload = new HashMap<>();
    payload.put("taskId", 1001L);
    payload.put("idempotentKey", "test-idempotent-key-001");

    String payloadJson = objectMapper.writeValueAsString(payload);

    // 准备：构建 RocketMQ 消息（包含 KEYS）
    // 注意：TAGS 通过 destination 参数传递（格式："topic:tags"）
    Message<String> message =
        MessageBuilder.withPayload(payloadJson)
            .setHeader("KEYS", "test-dedup-key-001") // dedupKey
            .build();

    // 执行：发送消息到 RocketMQ（TAGS 通过 destination 传递）
    String destination = TOPIC + ":CREATE"; // destination 格式：topic:tags
    SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
    log.info("消息已发送, msgId={}", sendResult.getMsgId());

    // 断言：等待 TaskExecutionUseCase.execute() 被调用 (最多 10 秒)
    ArgumentCaptor<TaskReadyCommand> commandCaptor =
        ArgumentCaptor.forClass(TaskReadyCommand.class);
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> verify(taskExecutionUseCase, times(1)).execute(commandCaptor.capture()));

    // 验证：Command 对象的字段
    TaskReadyCommand capturedCommand = commandCaptor.getValue();
    assertThat(capturedCommand.taskId()).isEqualTo(1001L);
    assertThat(capturedCommand.idempotentKey()).isEqualTo("test-idempotent-key-001");

    // 验证：headers 包含 RocketMQ 元数据
    assertThat(capturedCommand.headers()).isNotNull();
    assertThat(capturedCommand.headers()).containsEntry("KEYS", "test-dedup-key-001");
    assertThat(capturedCommand.headers()).containsEntry("TAGS", "CREATE");
    assertThat(capturedCommand.headers()).containsEntry("topic", TOPIC);
    assertThat(capturedCommand.headers()).containsKey("msgId"); // RocketMQ 消息 ID
  }

  @Test
  @DisplayName("应该正确提取消息元数据（KEYS、TAGS、UserProperties）")
  void shouldExtractMessageMetadataCorrectly() throws Exception {
    // 准备：构建消息负载
    Map<String, Object> payload = new HashMap<>();
    payload.put("taskId", 2001L);
    payload.put("idempotentKey", "test-idempotent-key-002");

    String payloadJson = objectMapper.writeValueAsString(payload);

    // 准备：构建包含自定义属性的消息
    Message<String> message =
        MessageBuilder.withPayload(payloadJson)
            .setHeader("KEYS", "test-dedup-key-002")
            .setHeader("partitionKey", "partition-A") // 自定义属性
            .setHeader("channel", "TASK_READY") // 业务通道
            .setHeader("traceId", "trace-12345") // 链路追踪 ID
            .build();

    // 执行：发送消息（TAGS 通过 destination 传递）
    String destination = TOPIC + ":UPDATE"; // destination 格式：topic:tags
    rocketMQTemplate.syncSend(destination, message);

    // 断言：等待用例被调用
    ArgumentCaptor<TaskReadyCommand> commandCaptor =
        ArgumentCaptor.forClass(TaskReadyCommand.class);
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> verify(taskExecutionUseCase, times(1)).execute(commandCaptor.capture()));

    // 验证：Command 中的 headers 包含所有元数据
    TaskReadyCommand capturedCommand = commandCaptor.getValue();
    Map<String, Object> headers = capturedCommand.headers();

    // 验证 RocketMQ 标准元数据
    assertThat(headers).containsEntry("KEYS", "test-dedup-key-002");
    assertThat(headers).containsEntry("TAGS", "UPDATE");
    assertThat(headers).containsEntry("topic", TOPIC);

    // 验证自定义 UserProperties
    assertThat(headers).containsEntry("partitionKey", "partition-A");
    assertThat(headers).containsEntry("channel", "TASK_READY");
    assertThat(headers).containsEntry("traceId", "trace-12345");
  }

  @Test
  @DisplayName("应该处理无效消息负载（缺少 taskId）")
  void shouldHandleInvalidPayload_MissingTaskId() throws Exception {
    // 准备：构建缺少 taskId 的无效消息
    Map<String, Object> invalidPayload = new HashMap<>();
    invalidPayload.put("idempotentKey", "test-idempotent-key-003");
    // taskId 缺失

    String payloadJson = objectMapper.writeValueAsString(invalidPayload);

    // 准备：构建消息
    Message<String> message =
        MessageBuilder.withPayload(payloadJson).setHeader("KEYS", "test-dedup-key-003").build();

    // 执行：发送消息
    rocketMQTemplate.syncSend(TOPIC, message);

    // 断言：等待一段时间，确保 TaskExecutionUseCase 不会被调用
    await()
        .pollDelay(2, SECONDS) // 等待 2 秒
        .atMost(5, SECONDS)
        .untilAsserted(() -> verify(taskExecutionUseCase, never()).execute(any()));

    // 说明：由于消息解析失败，Listener 会抛出异常，RocketMQ 会重试
    // 在测试中，我们验证用例不会被调用（因为验证失败在用例调用之前）
  }

  @Test
  @DisplayName("应该处理无效消息负载（缺少 idempotentKey）")
  void shouldHandleInvalidPayload_MissingIdempotentKey() throws Exception {
    // 准备：构建缺少 idempotentKey 的无效消息
    Map<String, Object> invalidPayload = new HashMap<>();
    invalidPayload.put("taskId", 3001L);
    // idempotentKey 缺失

    String payloadJson = objectMapper.writeValueAsString(invalidPayload);

    // 准备：构建消息
    Message<String> message =
        MessageBuilder.withPayload(payloadJson).setHeader("KEYS", "test-dedup-key-004").build();

    // 执行：发送消息
    rocketMQTemplate.syncSend(TOPIC, message);

    // 断言：等待一段时间，确保 TaskExecutionUseCase 不会被调用
    await()
        .pollDelay(2, SECONDS)
        .atMost(5, SECONDS)
        .untilAsserted(() -> verify(taskExecutionUseCase, never()).execute(any()));

    // 说明：由于验证失败，Listener 会抛出异常，RocketMQ 会重试
  }

  @Test
  @DisplayName("应该处理用例执行失败（异常传播触发 RocketMQ 重试）")
  void shouldHandleUseCaseExecutionFailure() throws Exception {
    // 准备：Mock TaskExecutionUseCase 抛出异常
    doThrow(new RuntimeException("模拟业务执行失败"))
        .when(taskExecutionUseCase)
        .execute(any(TaskReadyCommand.class));

    // 准备：构建消息负载
    Map<String, Object> payload = new HashMap<>();
    payload.put("taskId", 4001L);
    payload.put("idempotentKey", "test-idempotent-key-005");

    String payloadJson = objectMapper.writeValueAsString(payload);

    // 准备：构建消息
    Message<String> message =
        MessageBuilder.withPayload(payloadJson).setHeader("KEYS", "test-dedup-key-005").build();

    // 执行：发送消息
    rocketMQTemplate.syncSend(TOPIC, message);

    // 断言：等待 TaskExecutionUseCase.execute() 被调用
    await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> verify(taskExecutionUseCase, atLeastOnce()).execute(any()));

    // 说明：由于用例抛出异常，Listener 会传播异常给 RocketMQ，触发重试
    // 在测试中，我们验证用例至少被调用 1 次（可能因为重试被调用多次）
    log.info("用例执行失败，RocketMQ 将触发重试机制");
  }

  @Test
  @DisplayName("应该正确解析包含所有字段的完整消息")
  void shouldParseCompleteMessage() throws Exception {
    // 准备：构建完整的消息负载
    Map<String, Object> payload = new HashMap<>();
    payload.put("taskId", 5001L);
    payload.put("idempotentKey", "test-idempotent-key-006");

    String payloadJson = objectMapper.writeValueAsString(payload);

    // 准备：构建包含完整元数据的消息
    Message<String> message =
        MessageBuilder.withPayload(payloadJson)
            .setHeader("KEYS", "test-dedup-key-006")
            .setHeader("partitionKey", "partition-B")
            .setHeader("channel", "TASK_READY")
            .setHeader("traceId", "trace-67890")
            .setHeader("userId", "user-123")
            .build();

    // 执行：发送消息（TAGS 通过 destination 传递）
    String destination = TOPIC + ":EXECUTE"; // destination 格式：topic:tags
    rocketMQTemplate.syncSend(destination, message);

    // 断言：等待用例被调用
    ArgumentCaptor<TaskReadyCommand> commandCaptor =
        ArgumentCaptor.forClass(TaskReadyCommand.class);
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> verify(taskExecutionUseCase, times(1)).execute(commandCaptor.capture()));

    // 验证：Command 对象包含所有字段
    TaskReadyCommand capturedCommand = commandCaptor.getValue();

    // 验证核心字段
    assertThat(capturedCommand.taskId()).isEqualTo(5001L);
    assertThat(capturedCommand.idempotentKey()).isEqualTo("test-idempotent-key-006");

    // 验证 headers 包含所有元数据
    Map<String, Object> headers = capturedCommand.headers();
    assertThat(headers)
        .containsEntry("KEYS", "test-dedup-key-006")
        .containsEntry("TAGS", "EXECUTE")
        .containsEntry("topic", TOPIC)
        .containsEntry("partitionKey", "partition-B")
        .containsEntry("channel", "TASK_READY")
        .containsEntry("traceId", "trace-67890")
        .containsEntry("userId", "user-123")
        .containsKey("msgId"); // RocketMQ 生成的消息 ID
  }
}
