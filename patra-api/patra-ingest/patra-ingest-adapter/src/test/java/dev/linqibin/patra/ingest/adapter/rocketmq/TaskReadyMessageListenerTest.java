package dev.linqibin.patra.ingest.adapter.rocketmq;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.ingest.adapter.rocketmq.dto.TaskReadyPayload;
import dev.linqibin.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

/// TaskReadyMessageListener 单元测试。
///
/// 测试覆盖:
///
/// - 正常消息消费流程
/// - 消息元数据提取（KEYS, TAGS, UserProperties）
/// - TaskReadyCommand 组装
/// - 异常处理和日志记录
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskReadyMessageListener 单元测试")
class TaskReadyMessageListenerTest {

  @Mock private CommandBus commandBus;

  @Mock private ObjectMapper objectMapper;

  @Captor private ArgumentCaptor<TaskReadyCommand> commandCaptor;

  private TaskReadyMessageListener listener;

  @BeforeEach
  void setUp() {
    listener = new TaskReadyMessageListener(commandBus, objectMapper);
  }

  @Nested
  @DisplayName("正常消息消费场景")
  class NormalMessageConsumptionTests {

    @Test
    @DisplayName("应成功消费标准任务就绪消息")
    void shouldConsumeStandardTaskReadyMessage() throws Exception {
      // Given
      long taskId = 12345L;
      String idempotentKey = "task-idem-001";

      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(taskId);
      payload.setIdempotentKey(idempotentKey);

      String payloadJson =
          "{\"taskId\":" + taskId + ",\"idempotentKey\":\"" + idempotentKey + "\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "dedup-key-001", payloadJson);
      message.putUserProperty("partitionKey", "partition-001");
      message.putUserProperty("channel", "TASK_READY");

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then
      verify(commandBus).handle(commandCaptor.capture());

      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.taskId()).isEqualTo(taskId);
      assertThat(command.idempotentKey()).isEqualTo(idempotentKey);

      // 验证消息头包含 RocketMQ 元数据
      Map<String, Object> headers = command.headers();
      assertThat(headers).isNotNull();
      assertThat(headers.get("msgId")).isNotNull();
      assertThat(headers.get("KEYS")).isEqualTo("dedup-key-001");
      assertThat(headers.get("TAGS")).isEqualTo("TASK_READY");
      assertThat(headers.get("topic")).isEqualTo("INGEST_TASK_READY");
      assertThat(headers.get("partitionKey")).isEqualTo("partition-001");
      assertThat(headers.get("channel")).isEqualTo("TASK_READY");
    }

    @Test
    @DisplayName("应正确提取所有 UserProperties")
    void shouldExtractAllUserProperties() throws Exception {
      // Given
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(999L);
      payload.setIdempotentKey("idem-999");
      String payloadJson = "{\"taskId\":999,\"idempotentKey\":\"idem-999\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-999", payloadJson);
      message.putUserProperty("customHeader1", "value1");
      message.putUserProperty("customHeader2", "value2");
      message.putUserProperty("traceId", "trace-123");

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then
      verify(commandBus).handle(commandCaptor.capture());

      TaskReadyCommand command = commandCaptor.getValue();
      Map<String, Object> headers = command.headers();

      assertThat(headers.get("customHeader1")).isEqualTo("value1");
      assertThat(headers.get("customHeader2")).isEqualTo("value2");
      assertThat(headers.get("traceId")).isEqualTo("trace-123");
    }
  }

  @Nested
  @DisplayName("异常处理场景")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("JSON 解析失败应抛出 RuntimeException")
    void shouldThrowRuntimeExceptionWhenJsonParsingFails() throws Exception {
      // Given
      String invalidJson = "{invalid json}";
      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-001", invalidJson);

      when(objectMapper.readValue(eq(invalidJson), eq(TaskReadyPayload.class)))
          .thenThrow(new tools.jackson.core.exc.StreamReadException(null, "Invalid JSON"));

      // When & Then
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败")
          .hasCauseInstanceOf(tools.jackson.core.exc.StreamReadException.class);

      // 验证没有调用 UseCase
      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }

    @Test
    @DisplayName("CommandBus 抛出异常应传播为 RuntimeException")
    void shouldPropagateUseCaseException() throws Exception {
      // Given
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(777L);
      payload.setIdempotentKey("idem-777");
      String payloadJson = "{\"taskId\":777,\"idempotentKey\":\"idem-777\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-777", payloadJson);

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      RuntimeException useCaseException = new RuntimeException("任务执行失败");
      doThrow(useCaseException).when(commandBus).handle(any(TaskReadyCommand.class));

      // When & Then
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败")
          .hasCause(useCaseException);
    }

    @Test
    @DisplayName("TaskReadyPayload 验证失败应抛出 RuntimeException")
    void shouldThrowRuntimeExceptionWhenPayloadValidationFails() throws Exception {
      // Given: 创建一个无效的 Payload (idempotentKey 为 null)
      String payloadJson = "{\"taskId\":888,\"idempotentKey\":null}";
      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-888", payloadJson);

      // Mock ObjectMapper 返回一个会在 validate() 时失败的 Payload
      TaskReadyPayload invalidPayload = new TaskReadyPayload();
      invalidPayload.setTaskId(888L);
      invalidPayload.setIdempotentKey(null);
      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class)))
          .thenReturn(invalidPayload);

      // When & Then
      assertThatThrownBy(() -> listener.onMessage(message))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("消息消费失败");

      // 验证没有调用 UseCase
      verify(commandBus, never()).handle(any(TaskReadyCommand.class));
    }
  }

  @Nested
  @DisplayName("消息元数据映射场景")
  class MessageMetadataMappingTests {

    @Test
    @DisplayName("KEYS 应映射到 headers 的 KEYS 字段")
    void shouldMapKeysToHeaders() throws Exception {
      // Given
      String dedupKey = "unique-dedup-key-123";
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(100L);
      payload.setIdempotentKey("idem-100");
      String payloadJson = "{\"taskId\":100,\"idempotentKey\":\"idem-100\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", dedupKey, payloadJson);

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then
      verify(commandBus).handle(commandCaptor.capture());

      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.headers().get("KEYS")).isEqualTo(dedupKey);
    }

    @Test
    @DisplayName("TAGS 应映射到 headers 的 TAGS 字段")
    void shouldMapTagsToHeaders() throws Exception {
      // Given
      String tags = "CUSTOM_TAG";
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(200L);
      payload.setIdempotentKey("idem-200");
      String payloadJson = "{\"taskId\":200,\"idempotentKey\":\"idem-200\"}";

      MessageExt message = createMessageExt("INGEST_TASK_READY", tags, "key-200", payloadJson);

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then
      verify(commandBus).handle(commandCaptor.capture());

      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.headers().get("TAGS")).isEqualTo(tags);
    }

    @Test
    @DisplayName("partitionKey 应作为 UserProperty 传递到 headers")
    void shouldPassPartitionKeyAsUserProperty() throws Exception {
      // Given
      String partitionKey = "partition-key-999";
      TaskReadyPayload payload = new TaskReadyPayload();
      payload.setTaskId(300L);
      payload.setIdempotentKey("idem-300");
      String payloadJson = "{\"taskId\":300,\"idempotentKey\":\"idem-300\"}";

      MessageExt message =
          createMessageExt("INGEST_TASK_READY", "TASK_READY", "key-300", payloadJson);
      message.putUserProperty("partitionKey", partitionKey);

      when(objectMapper.readValue(eq(payloadJson), eq(TaskReadyPayload.class))).thenReturn(payload);

      // When
      listener.onMessage(message);

      // Then
      verify(commandBus).handle(commandCaptor.capture());

      TaskReadyCommand command = commandCaptor.getValue();
      assertThat(command.headers().get("partitionKey")).isEqualTo(partitionKey);
    }
  }

  /// 创建测试用 MessageExt 对象的辅助方法。
  ///
  /// @param topic 主题名称
  /// @param tags TAGS
  /// @param keys KEYS (dedupKey)
  /// @param payloadJson JSON 消息体
  /// @return MessageExt
  private MessageExt createMessageExt(String topic, String tags, String keys, String payloadJson) {
    MessageExt message = new MessageExt();
    message.setTopic(topic);
    message.setTags(tags);
    message.setKeys(keys);
    message.setBody(payloadJson.getBytes(StandardCharsets.UTF_8));
    message.setMsgId("test-msg-" + System.nanoTime());

    // 初始化 properties map (RocketMQ 内部需要)
    message.getProperties().put("KEYS", keys);
    message.getProperties().put("TAGS", tags);

    return message;
  }
}
