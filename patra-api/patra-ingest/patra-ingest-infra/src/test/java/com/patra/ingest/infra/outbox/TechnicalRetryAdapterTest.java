package com.patra.ingest.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.TechnicalRetryPort.RetryContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// TechnicalRetryAdapter 单元测试
/// 
/// 测试策略：
/// 
/// - 验证 RetryContext 到 OutboxMessage 的转换逻辑
///   - 验证去重键（dedupKey）生成正确（SHA-256 哈希）
///   - 验证分区键（partitionKey）提取逻辑
///   - 验证无效输入的处理
/// 
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
class TechnicalRetryAdapterTest {

  @Mock private OutboxMessageRepository repository;

  private TechnicalRetryAdapter adapter;

  @BeforeEach
  void setup() {
    ObjectMapper objectMapper = new ObjectMapper();
    adapter = new TechnicalRetryAdapter(repository, objectMapper);
  }

  @Test
  @DisplayName("应该成功发布重试请求到 Outbox")
  void should_publish_retry_to_outbox() {
    // given
    String payload = "{\"storageKey\":\"test-key\",\"bucketName\":\"test-bucket\"}";
    Map<String, Object> metadata =
        Map.of(
            "provenanceCode", "pubmed",
            "storageKey", "test-key",
            "fileSize", 12345L,
            "batchNo", 1,
            "traceId", "trace-123");

    RetryContext context =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload(payload)
            .metadata(metadata)
            .build();

    // when
    adapter.publishRetry(context);

    // then
    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(repository).saveOrUpdate(captor.capture());

    OutboxMessage saved = captor.getValue();
    assertThat(saved).isNotNull();
    assertThat(saved.getAggregateType()).isEqualTo("TASK_RUN");
    assertThat(saved.getAggregateId()).isEqualTo(100L);
    assertThat(saved.getChannel()).isEqualTo("STORAGE_METADATA_INTERNAL");
    assertThat(saved.getOpType()).isEqualTo("STORAGE_METADATA_RETRY");
    assertThat(saved.getPayloadJson()).isEqualTo(payload);
    assertThat(saved.getPartitionKey()).isEqualTo("pubmed");
    assertThat(saved.getDedupKey()).isNotNull();
    assertThat(saved.getStatusCode()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("应该使用 SHA-256 生成去重键")
  void should_generate_dedup_key_with_sha256() {
    // given
    Map<String, Object> metadata =
        Map.of("storageKey", "test-key", "fileSize", 12345L, "provenanceCode", "pubmed");

    RetryContext context =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload("{}")
            .metadata(metadata)
            .build();

    // when
    adapter.publishRetry(context);

    // then
    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(repository).saveOrUpdate(captor.capture());

    String dedupKey = captor.getValue().getDedupKey();
    // SHA-256 输出为 64 个十六进制字符
    assertThat(dedupKey).hasSize(64);
    assertThat(dedupKey).matches("^[0-9a-f]{64}$");
  }

  @Test
  @DisplayName("相同 storageKey 和 fileSize 应该生成相同的去重键")
  void should_generate_same_dedup_key_for_same_input() {
    // given
    Map<String, Object> metadata1 =
        Map.of("storageKey", "key-1", "fileSize", 999L, "provenanceCode", "pubmed");
    Map<String, Object> metadata2 =
        Map.of("storageKey", "key-1", "fileSize", 999L, "provenanceCode", "embase");

    RetryContext context1 =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload("{}")
            .metadata(metadata1)
            .build();

    RetryContext context2 =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(200L)
            .payload("{}")
            .metadata(metadata2)
            .build();

    // when
    adapter.publishRetry(context1);
    adapter.publishRetry(context2);

    // then
    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(repository, org.mockito.Mockito.times(2)).saveOrUpdate(captor.capture());

    String dedupKey1 = captor.getAllValues().get(0).getDedupKey();
    String dedupKey2 = captor.getAllValues().get(1).getDedupKey();

    assertThat(dedupKey1).isEqualTo(dedupKey2);
  }

  @Test
  @DisplayName("应该使用 provenanceCode 作为分区键")
  void should_use_provenance_code_as_partition_key() {
    // given
    Map<String, Object> metadata =
        Map.of("storageKey", "key-1", "fileSize", 999L, "provenanceCode", "embase");

    RetryContext context =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload("{}")
            .metadata(metadata)
            .build();

    // when
    adapter.publishRetry(context);

    // then
    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(repository).saveOrUpdate(captor.capture());

    assertThat(captor.getValue().getPartitionKey()).isEqualTo("embase");
  }

  @Test
  @DisplayName("provenanceCode 缺失时应该使用 UNKNOWN 作为分区键")
  void should_use_unknown_when_provenance_code_missing() {
    // given
    Map<String, Object> metadata = Map.of("storageKey", "key-1", "fileSize", 999L);

    RetryContext context =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload("{}")
            .metadata(metadata)
            .build();

    // when
    adapter.publishRetry(context);

    // then
    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(repository).saveOrUpdate(captor.capture());

    assertThat(captor.getValue().getPartitionKey()).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("应该序列化 metadata 到 headers JSON")
  void should_serialize_metadata_to_headers_json() {
    // given
    Map<String, Object> metadata =
        Map.of(
            "provenanceCode", "pubmed",
            "batchNo", 10,
            "traceId", "trace-456");

    RetryContext context =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload("{}")
            .metadata(metadata)
            .build();

    // when
    adapter.publishRetry(context);

    // then
    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(repository).saveOrUpdate(captor.capture());

    String headersJson = captor.getValue().getHeadersJson();
    assertThat(headersJson).isNotNull();
    assertThat(headersJson).contains("\"provenanceCode\":\"pubmed\"");
    assertThat(headersJson).contains("\"batchNo\":10");
    assertThat(headersJson).contains("\"traceId\":\"trace-456\"");
  }

  @Test
  @DisplayName("无效的重试上下文应该跳过发布")
  void should_skip_publish_when_context_is_invalid() {
    // given - payload 为 null
    RetryContext invalidContext =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(100L)
            .payload(null)
            .metadata(Map.of())
            .build();

    // when
    adapter.publishRetry(invalidContext);

    // then
    verify(repository, never()).saveOrUpdate(any());
  }

  @Test
  @DisplayName("aggregateId 为 null 时应该跳过发布")
  void should_skip_publish_when_aggregate_id_is_null() {
    // given
    RetryContext invalidContext =
        RetryContext.builder()
            .operationType("METADATA_RECORD")
            .aggregateId(null)
            .payload("{}")
            .metadata(Map.of())
            .build();

    // when
    adapter.publishRetry(invalidContext);

    // then
    verify(repository, never()).saveOrUpdate(any());
  }

  @Test
  @DisplayName("整个 context 为 null 时应该跳过发布")
  void should_skip_publish_when_context_is_null() {
    // when
    adapter.publishRetry(null);

    // then
    verify(repository, never()).saveOrUpdate(any());
  }
}
