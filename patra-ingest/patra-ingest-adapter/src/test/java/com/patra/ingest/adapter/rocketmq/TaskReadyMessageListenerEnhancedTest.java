package com.patra.ingest.adapter.rocketmq;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.common.cqrs.CommandBus;
import com.patra.ingest.adapter.rocketmq.dto.TaskReadyPayload;
import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/// TaskReadyMessageListener 增强测试套件。
///
/// 补充原有测试中缺失的边界条件、重试机制、幂等性验证等高级测试场景。
///
/// 测试覆盖：
///
/// - 边界条件：空消息、超大消息、编码异常、格式错误
/// - 并发消费：多消息并发处理
/// - 重试机制：消费失败后的异常抛出
/// - 幂等性：重复消息处理验证（通过 idempotentKey）
/// - 元数据验证：UserProperties 各种组合
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskReadyMessageListener 增强测试")
class TaskReadyMessageListenerEnhancedTest {

  @Mock private CommandBus commandBus;
  @Mock private ObjectMapper objectMapper;

  @Captor private ArgumentCaptor<TaskReadyCommand> commandCaptor;

  private TaskReadyMessageListener listener;

  @BeforeEach
  void setUp() {
    listener = new TaskReadyMessageListener(commandBus, objectMapper);
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应拒绝空消息体")
    void shouldRejectEmptyMessageBody() throws Exception {
      // Given: 空消息体
      MessageExt message = createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-001", "");

      when(objectMapper.readValue(eq(""), eq(TaskReadyPayload.class)))
          .thenThrow(new tools.jackson.core.exc.StreamReadException(null, "No content"));

      // When & Then
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败");

      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }

    @Test
    @DisplayName("应处理超大消息体 (> 4MB)")
    void shouldHandleLargeMessageBody() throws Exception {
      // Given: 构造 4MB+ 的 JSON 消息体
      long taskId = 999L;
      String idempotentKey = "large-msg-key";

      // 创建大型 payload (4MB)
      StringBuilder largeData = new StringBuilder();
      for (int i = 0; i < 4 * 1024; i++) { // 4K 行
        largeData.append("x".repeat(1024)); // 每行 1KB
      }
      String largePayloadJson =
          "{\"taskId\":"
              + taskId
              + ",\"idempotentKey\":\""
              + idempotentKey
              + "\",\"data\":\""
              + largeData
              + "\"}";

      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(taskId);
      payload.setIdempotentKey(idempotentKey);

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-large", largePayloadJson);

      when(objectMapper.readValue(eq(largePayloadJson), eq(TaskReadyPayload.class)))
          .thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then: 应成功消费大消息
      verify(commandBus).handle(commandCaptor.capture());
      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.taskId()).isEqualTo(taskId);
      assertThat(command.idempotentKey()).isEqualTo(idempotentKey);
    }

    @Test
    @DisplayName("应拒绝格式错误的 JSON")
    void shouldRejectMalformedJson() throws Exception {
      // Given: 格式错误的 JSON
      String malformedJson = "{\"taskId\":123,\"idempotentKey\":"; // 缺失闭合

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-malformed", malformedJson);

      when(objectMapper.readValue(eq(malformedJson), eq(TaskReadyPayload.class)))
          .thenThrow(
              new tools.jackson.core.exc.StreamReadException(null, "Unexpected end-of-input"));

      // When & Then
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败")
          .hasCauseInstanceOf(tools.jackson.core.exc.StreamReadException.class);

      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }

    @Test
    @DisplayName("应处理非 UTF-8 编码的消息体")
    void shouldHandleNonUtf8Encoding() throws Exception {
      // Given: GBK 编码的消息体
      String chineseText = "任务就绪";
      String jsonTemplate = "{\"taskId\":888,\"idempotentKey\":\"" + chineseText + "\"}";

      // 使用 GBK 编码
      byte[] gbkBytes = jsonTemplate.getBytes("GBK");

      MessageExt message = new MessageExt();
      message.setTopic("INGEST_TASK_READY");
      message.setTags("TASK_READY");
      message.setKeys("key-gbk");
      message.setBody(gbkBytes); // GBK 编码
      message.setMsgId("test-msg-gbk");

      // 解析时会使用 UTF-8，可能导致乱码或异常
      String decodedJson = new String(gbkBytes, StandardCharsets.UTF_8);

      when(objectMapper.readValue(eq(decodedJson), eq(TaskReadyPayload.class)))
          .thenThrow(new tools.jackson.core.exc.StreamReadException(null, "Invalid UTF-8"));

      // When & Then: 应抛出异常
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败");

      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }

    @Test
    @DisplayName("应拒绝缺少必填字段的消息")
    void shouldRejectMessageWithMissingRequiredFields() throws Exception {
      // Given: 缺少 idempotentKey
      String incompleteJson = "{\"taskId\":777}";

      TaskReadyPayload invalidPayload = new TaskReadyPayload();
      invalidPayload.setTaskId(777L);
      invalidPayload.setIdempotentKey(null); // 缺失

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-incomplete", incompleteJson);

      when(objectMapper.readValue(eq(incompleteJson), eq(TaskReadyPayload.class)))
          .thenReturn(invalidPayload);

      // When & Then: validate() 会抛出异常
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败");

      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }

    @Test
    @DisplayName("应处理 null KEYS")
    void shouldHandleNullKeys() throws Exception {
      // Given: KEYS 为 null
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(100L);
      payload.setIdempotentKey("idem-100");
      String payloadJson = "{\"taskId\":100,\"idempotentKey\":\"idem-100\"}";

      MessageExt message = new MessageExt();
      message.setTopic("INGEST_TASK_READY");
      message.setTags("TASK_READY");
      message.setKeys((String) null); // null KEYS - 显式类型转换
      message.setBody(payloadJson.getBytes(StandardCharsets.UTF_8));
      message.setMsgId("test-msg-null-keys");

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then: 应成功消费，headers 中 KEYS 为 null
      verify(commandBus).handle(commandCaptor.capture());
      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.headers().get("KEYS")).isNull();
    }

    @Test
    @DisplayName("应处理 null TAGS")
    void shouldHandleNullTags() throws Exception {
      // Given: TAGS 为 null
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(200L);
      payload.setIdempotentKey("idem-200");
      String payloadJson = "{\"taskId\":200,\"idempotentKey\":\"idem-200\"}";

      MessageExt message = new MessageExt();
      message.setTopic("INGEST_TASK_READY");
      message.setTags(null); // null TAGS
      message.setKeys("key-200");
      message.setBody(payloadJson.getBytes(StandardCharsets.UTF_8));
      message.setMsgId("test-msg-null-tags");
      message.getProperties().put("KEYS", "key-200");

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then: 应成功消费，headers 中 TAGS 为 null
      verify(commandBus).handle(commandCaptor.capture());
      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.headers().get("TAGS")).isNull();
    }
  }

  @Nested
  @DisplayName("并发消费测试")
  class ConcurrentConsumptionTests {

    @Test
    @DisplayName("应安全处理并发消息消费")
    void shouldHandleConcurrentMessageConsumption() throws Exception {
      // Given: 准备 10 个并发消息
      int messageCount = 10;
      CountDownLatch latch = new CountDownLatch(messageCount);
      ExecutorService executor = Executors.newFixedThreadPool(5);

      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger failureCount = new AtomicInteger(0);

      when(objectMapper.readValue(anyString(), eq(TaskReadyPayload.class)))
          .thenAnswer(
              invocation -> {
                String json = invocation.getArgument(0);
                TaskReadyPayload payload = new TaskReadyPayload();
                // 从 JSON 提取 taskId
                Long taskId = Long.parseLong(json.replaceAll(".*\"taskId\":(\\d+).*", "$1"));
                payload.setTaskId(taskId);
                payload.setIdempotentKey("idem-" + taskId);
                return payload;
              });

      doAnswer(
              invocation -> {
                Thread.sleep(10); // 模拟处理延迟
                successCount.incrementAndGet();
                return null;
              })
          .when(commandBus)
          .handle(any(TaskReadyCommand.class));

      // When: 并发消费消息
      for (int i = 0; i < messageCount; i++) {
        int msgIndex = i;
        executor.submit(
            () -> {
              try {
                String json =
                    "{\"taskId\":" + msgIndex + ",\"idempotentKey\":\"idem-" + msgIndex + "\"}";
                MessageExt message =
                    createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-" + msgIndex, json);

                listener.onMessage(message);
              } catch (Exception e) {
                failureCount.incrementAndGet();
              } finally {
                latch.countDown();
              }
            });
      }

      // Then: 所有消息应在 5 秒内消费完成
      boolean completed = latch.await(5, TimeUnit.SECONDS);
      executor.shutdown();

      assertThat(completed).isTrue();
      assertThat(successCount.get()).isEqualTo(messageCount);
      assertThat(failureCount.get()).isZero();
    }
  }

  @Nested
  @DisplayName("重试机制测试")
  class RetryMechanismTests {

    @Test
    @DisplayName("UseCase 执行失败应抛出 RuntimeException 触发 RocketMQ 重试")
    void shouldThrowRuntimeExceptionToTriggerRocketMqRetry() throws Exception {
      // Given
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(500L);
      payload.setIdempotentKey("retry-test");
      String payloadJson = "{\"taskId\":500,\"idempotentKey\":\"retry-test\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-retry", payloadJson);

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      RuntimeException useCaseError = new RuntimeException("任务执行失败，需要重试");
      doThrow(useCaseError).when(commandBus).handle(any(TaskReadyCommand.class));

      // When & Then: 应抛出 RuntimeException
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败")
          .hasCause(useCaseError);

      // 验证 CommandBus 被调用一次
      verify(commandBus, times(1)).handle(any(TaskReadyCommand.class));
    }

    @Test
    @DisplayName("JSON 解析异常应抛出 RuntimeException 触发重试")
    void shouldThrowRuntimeExceptionOnJsonParsingError() throws Exception {
      // Given
      String invalidJson = "{\"taskId\":\"not-a-number\"}";
      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-parse-error", invalidJson);

      when(objectMapper.readValue(eq(invalidJson), eq(TaskReadyPayload.class)))
          .thenThrow(
              tools.jackson.databind.exc.InvalidDefinitionException.from(
                  (tools.jackson.core.JsonParser) null,
                  "Cannot parse",
                  (tools.jackson.databind.JavaType) null));

      // When & Then
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败")
          .hasCauseInstanceOf(tools.jackson.databind.DatabindException.class);

      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }
  }

  @Nested
  @DisplayName("幂等性验证测试")
  class IdempotencyTests {

    @Test
    @DisplayName("相同 idempotentKey 的消息应传递给 UseCase (由 UseCase 处理幂等)")
    void shouldPassDuplicateMessagesToUseCase() throws Exception {
      // Given: 两条具有相同 idempotentKey 的消息
      String idempotentKey = "duplicate-key-123";
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(600L);
      payload.setIdempotentKey(idempotentKey);
      String payloadJson = "{\"taskId\":600,\"idempotentKey\":\"" + idempotentKey + "\"}";

      MessageExt message1 =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-dup-1", payloadJson);
      MessageExt message2 =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-dup-2", payloadJson);

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When: 消费两次
      listener.onMessage(message1);
      listener.onMessage(message2);

      // Then: CommandBus 应被调用两次 (幂等性由 Handler 层处理)
      verify(commandBus, times(2)).handle(commandCaptor.capture());

      // 验证两次调用的 idempotentKey 相同
      assertThat(commandCaptor.getAllValues())
          .extracting(TaskReadyCommand::idempotentKey)
          .containsOnly(idempotentKey);
    }

    @Test
    @DisplayName("不同 idempotentKey 的消息应独立处理")
    void shouldProcessMessagesWithDifferentIdempotentKeysIndependently() throws Exception {
      // Given: 两条不同 idempotentKey 的消息
      TaskReadyPayload payload1 = new TaskReadyPayload();
      payload1.setTaskId(700L);
      payload1.setIdempotentKey("key-1");
      String json1 = "{\"taskId\":700,\"idempotentKey\":\"key-1\"}";

      TaskReadyPayload payload2 = new TaskReadyPayload();
      payload2.setTaskId(800L);
      payload2.setIdempotentKey("key-2");
      String json2 = "{\"taskId\":800,\"idempotentKey\":\"key-2\"}";

      MessageExt message1 = createMessageExt("INGEST_TASK_READY", "TASK_READY", "dedup-1", json1);
      MessageExt message2 = createMessageExt("INGEST_TASK_READY", "TASK_READY", "dedup-2", json2);

      when(objectMapper.readValue(eq(json1), eq(TaskReadyPayload.class))).thenReturn(payload1);
      when(objectMapper.readValue(eq(json2), eq(TaskReadyPayload.class))).thenReturn(payload2);

      // When
      listener.onMessage(message1);
      listener.onMessage(message2);

      // Then: CommandBus 应被调用两次，taskId 不同
      verify(commandBus, times(2)).handle(commandCaptor.capture());

      assertThat(commandCaptor.getAllValues())
          .extracting(TaskReadyCommand::taskId)
          .containsExactly(700L, 800L);
    }
  }

  @Nested
  @DisplayName("元数据验证测试")
  class MetadataValidationTests {

    @Test
    @DisplayName("应传递所有 UserProperties 到 headers")
    void shouldPassAllUserPropertiesToHeaders() throws Exception {
      // Given: 消息包含多个 UserProperties
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(900L);
      payload.setIdempotentKey("meta-test");
      String payloadJson = "{\"taskId\":900,\"idempotentKey\":\"meta-test\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-meta", payloadJson);
      message.putUserProperty("traceId", "trace-abc-123");
      message.putUserProperty("spanId", "span-xyz-456");
      message.putUserProperty("userId", "user-999");
      message.putUserProperty("priority", "HIGH");

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then: 所有 UserProperties 应在 headers 中
      verify(commandBus).handle(commandCaptor.capture());
      TaskReadyCommand command = commandCaptor.getValue();

      assertThat(command.headers())
          .containsEntry("traceId", "trace-abc-123")
          .containsEntry("spanId", "span-xyz-456")
          .containsEntry("userId", "user-999")
          .containsEntry("priority", "HIGH");
    }

    @Test
    @DisplayName("没有 UserProperties 时 headers 应只包含 RocketMQ 元数据")
    void shouldOnlyIncludeRocketMqMetadataWhenNoUserProperties() throws Exception {
      // Given: 无 UserProperties
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(1000L);
      payload.setIdempotentKey("no-props");
      String payloadJson = "{\"taskId\":1000,\"idempotentKey\":\"no-props\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-no-props", payloadJson);
      // 不添加任何 UserProperties

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then: headers 应包含 RocketMQ 基础元数据
      verify(commandBus).handle(commandCaptor.capture());
      TaskReadyCommand command = commandCaptor.getValue();

      assertThat(command.headers())
          .containsKeys("msgId", "KEYS", "TAGS", "topic")
          .doesNotContainKey("partitionKey")
          .doesNotContainKey("channel");
    }
  }

  // ==================== 辅助方法 ====================

  private MessageExt createMessageExt(String topic, String tags, String keys, String payloadJson) {
    MessageExt message = new MessageExt();
    message.setTopic(topic);
    message.setTags(tags);
    message.setKeys(keys);
    message.setBody(payloadJson.getBytes(StandardCharsets.UTF_8));
    message.setMsgId("test-msg-" + System.nanoTime());

    // 初始化 properties map (RocketMQ 内部需要)
    if (keys != null) {
      message.getProperties().put("KEYS", keys);
    }
    if (tags != null) {
      message.getProperties().put("TAGS", tags);
    }

    return message;
  }
}
