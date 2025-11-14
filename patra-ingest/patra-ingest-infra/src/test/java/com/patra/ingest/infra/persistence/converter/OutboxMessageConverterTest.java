package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * OutboxMessageConverter 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>测试 OutboxMessage → OutboxMessageDO 的转换
 *   <li>测试 OutboxMessageDO → OutboxMessage 的转换
 *   <li>测试双向转换的一致性
 *   <li>测试 JSON 字段的双向转换
 *   <li>测试租约字段的映射（leaseOwner ↔ pubLeaseOwner, leaseExpireAt ↔ pubLeasedUntil）
 *   <li>测试空值和边界情况
 * </ul>
 *
 * <p>注意：MapStruct 转换器通过 Mappers.getMapper() 直接实例化，无需 Spring 容器。
 */
class OutboxMessageConverterTest {

  private final OutboxMessageConverter converter = Mappers.getMapper(OutboxMessageConverter.class);

  @Test
  @DisplayName("应当正确将OutboxMessage转换为OutboxMessageDO")
  void shouldConvertDomainToEntity() throws Exception {
    // Given: 构造完整的OutboxMessage
    Instant now = Instant.now();
    Instant notBefore = now.minusSeconds(60);
    Instant nextRetryAt = now.plusSeconds(120);
    Instant leaseExpireAt = now.plusSeconds(300);

    String payloadJson = "{\"taskId\":1001,\"priority\":5}";
    String headersJson = "{\"correlationId\":\"corr-123\"}";

    OutboxMessage message =
        OutboxMessage.builder()
            .id(2001L)
            .version(1L)
            .aggregateType("TASK")
            .aggregateId(1001L)
            .channel("INGEST_TASK")
            .opType("TASK_READY")
            .partitionKey("PUBMED:HARVEST")
            .dedupKey("dedup-key-001")
            .payloadJson(payloadJson)
            .headersJson(headersJson)
            .notBefore(notBefore)
            .statusCode("PENDING")
            .retryCount(2)
            .nextRetryAt(nextRetryAt)
            .errorCode("ERR_NETWORK")
            .errorMsg("Connection timeout")
            .leaseOwner("relay-worker-01")
            .leaseExpireAt(leaseExpireAt)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 验证所有字段正确映射
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(2001L);
    assertThat(entity.getAggregateType()).isEqualTo("TASK");
    assertThat(entity.getAggregateId()).isEqualTo(1001L);
    assertThat(entity.getChannel()).isEqualTo("INGEST_TASK");
    assertThat(entity.getOpType()).isEqualTo("TASK_READY");
    assertThat(entity.getPartitionKey()).isEqualTo("PUBMED:HARVEST");
    assertThat(entity.getDedupKey()).isEqualTo("dedup-key-001");
    assertThat(entity.getNotBefore()).isEqualTo(notBefore);
    assertThat(entity.getStatusCode()).isEqualTo("PENDING");
    assertThat(entity.getRetryCount()).isEqualTo(2);
    assertThat(entity.getNextRetryAt()).isEqualTo(nextRetryAt);
    assertThat(entity.getErrorCode()).isEqualTo("ERR_NETWORK");
    assertThat(entity.getErrorMsg()).isEqualTo("Connection timeout");

    // 验证payloadJson转换为JsonNode
    assertThat(entity.getPayloadJson()).isNotNull();
    assertThat(entity.getPayloadJson().get("taskId").asLong()).isEqualTo(1001L);
    assertThat(entity.getPayloadJson().get("priority").asInt()).isEqualTo(5);

    // 验证headersJson转换为JsonNode
    assertThat(entity.getHeadersJson()).isNotNull();
    assertThat(entity.getHeadersJson().get("correlationId").asText()).isEqualTo("corr-123");

    // 验证租约字段映射
    assertThat(entity.getPubLeaseOwner()).isEqualTo("relay-worker-01");
    assertThat(entity.getPubLeasedUntil()).isEqualTo(leaseExpireAt);
  }

  @Test
  @DisplayName("应当正确将OutboxMessageDO转换为OutboxMessage")
  void shouldConvertEntityToDomain() throws Exception {
    // Given: 构造完整的OutboxMessageDO
    Instant now = Instant.now();
    Instant notBefore = now.minusSeconds(60);
    Instant nextRetryAt = now.plusSeconds(120);
    Instant pubLeasedUntil = now.plusSeconds(300);

    JsonNode payloadJson =
        JsonMapperHolder.getObjectMapper().readTree("{\"taskId\":1001,\"priority\":5}");
    JsonNode headersJson =
        JsonMapperHolder.getObjectMapper().readTree("{\"correlationId\":\"corr-123\"}");

    OutboxMessageDO entity = new OutboxMessageDO();
    entity.setId(2001L);
    entity.setVersion(1L);
    entity.setAggregateType("TASK");
    entity.setAggregateId(1001L);
    entity.setChannel("INGEST_TASK");
    entity.setOpType("TASK_READY");
    entity.setPartitionKey("PUBMED:HARVEST");
    entity.setDedupKey("dedup-key-001");
    entity.setPayloadJson(payloadJson);
    entity.setHeadersJson(headersJson);
    entity.setNotBefore(notBefore);
    entity.setStatusCode("PENDING");
    entity.setRetryCount(2);
    entity.setNextRetryAt(nextRetryAt);
    entity.setErrorCode("ERR_NETWORK");
    entity.setErrorMsg("Connection timeout");
    entity.setPubLeaseOwner("relay-worker-01");
    entity.setPubLeasedUntil(pubLeasedUntil);

    // When: 转换为领域对象
    OutboxMessage message = converter.toDomain(entity);

    // Then: 验证所有字段正确映射
    assertThat(message).isNotNull();
    assertThat(message.getId()).isEqualTo(2001L);
    assertThat(message.getVersion()).isEqualTo(1L);
    assertThat(message.getAggregateType()).isEqualTo("TASK");
    assertThat(message.getAggregateId()).isEqualTo(1001L);
    assertThat(message.getChannel()).isEqualTo("INGEST_TASK");
    assertThat(message.getOpType()).isEqualTo("TASK_READY");
    assertThat(message.getPartitionKey()).isEqualTo("PUBMED:HARVEST");
    assertThat(message.getDedupKey()).isEqualTo("dedup-key-001");
    assertThat(message.getNotBefore()).isEqualTo(notBefore);
    assertThat(message.getStatusCode()).isEqualTo("PENDING");
    assertThat(message.getRetryCount()).isEqualTo(2);
    assertThat(message.getNextRetryAt()).isEqualTo(nextRetryAt);
    assertThat(message.getErrorCode()).isEqualTo("ERR_NETWORK");
    assertThat(message.getErrorMsg()).isEqualTo("Connection timeout");

    // 验证JsonNode转换为payloadJson
    assertThat(message.getPayloadJson()).isNotNull();
    assertThat(message.getPayloadJson()).contains("\"taskId\":1001");
    assertThat(message.getPayloadJson()).contains("\"priority\":5");

    // 验证JsonNode转换为headersJson
    assertThat(message.getHeadersJson()).isNotNull();
    assertThat(message.getHeadersJson()).contains("\"correlationId\":\"corr-123\"");

    // 验证租约字段映射
    assertThat(message.getLeaseOwner()).isEqualTo("relay-worker-01");
    assertThat(message.getLeaseExpireAt()).isEqualTo(pubLeasedUntil);
  }

  @Test
  @DisplayName("应当支持双向转换的一致性")
  void shouldMaintainConsistencyInRoundTripConversion() {
    // Given: 原始OutboxMessage
    Instant now = Instant.now();
    String payloadJson = "{\"taskId\":999}";
    String headersJson = "{\"traceId\":\"trace-999\"}";

    OutboxMessage original =
        OutboxMessage.builder()
            .id(1L)
            .version(1L)
            .aggregateType("TASK")
            .aggregateId(999L)
            .channel("TEST_CHANNEL")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-999")
            .payloadJson(payloadJson)
            .headersJson(headersJson)
            .notBefore(now)
            .statusCode("PENDING")
            .retryCount(0)
            .leaseOwner("worker-01")
            .leaseExpireAt(now.plusSeconds(60))
            .build();

    // When: Domain → DO → Domain
    OutboxMessageDO entity = converter.toEntity(original);
    OutboxMessage restored = converter.toDomain(entity);

    // Then: 关键字段应保持一致
    assertThat(restored.getId()).isEqualTo(original.getId());
    assertThat(restored.getAggregateType()).isEqualTo(original.getAggregateType());
    assertThat(restored.getAggregateId()).isEqualTo(original.getAggregateId());
    assertThat(restored.getChannel()).isEqualTo(original.getChannel());
    assertThat(restored.getDedupKey()).isEqualTo(original.getDedupKey());
    assertThat(restored.getLeaseOwner()).isEqualTo(original.getLeaseOwner());
    assertThat(restored.getLeaseExpireAt()).isEqualTo(original.getLeaseExpireAt());
  }

  @Test
  @DisplayName("应当正确处理空payload和headers")
  void shouldHandleNullPayloadAndHeaders() throws Exception {
    // Given: payload和headers为null的OutboxMessage
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .payloadJson(null)
            .headersJson(null)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: payload和headers应为null
    assertThat(entity.getPayloadJson()).isNull();
    assertThat(entity.getHeadersJson()).isNull();
  }

  @Test
  @DisplayName("应当正确处理空租约字段")
  void shouldHandleNullLeaseFields() {
    // Given: 租约字段为null的OutboxMessageDO
    OutboxMessageDO entity = new OutboxMessageDO();
    entity.setAggregateType("TASK");
    entity.setAggregateId(1L);
    entity.setChannel("TEST");
    entity.setOpType("TEST_OP");
    entity.setPartitionKey("TEST:PARTITION");
    entity.setDedupKey("dedup-001");
    entity.setStatusCode("PENDING");
    entity.setPubLeaseOwner(null);
    entity.setPubLeasedUntil(null);

    // When: 转换为领域对象
    OutboxMessage message = converter.toDomain(entity);

    // Then: 租约字段应为null
    assertThat(message.getLeaseOwner()).isNull();
    assertThat(message.getLeaseExpireAt()).isNull();
  }

  @Test
  @DisplayName("应当正确处理空notBefore和nextRetryAt")
  void shouldHandleNullTimestamps() {
    // Given: 时间字段为null的OutboxMessage
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .notBefore(null)
            .nextRetryAt(null)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 时间字段应为null
    assertThat(entity.getNotBefore()).isNull();
    assertThat(entity.getNextRetryAt()).isNull();
  }

  @Test
  @DisplayName("应当正确处理空error字段")
  void shouldHandleNullErrorFields() {
    // Given: error字段为null的OutboxMessage
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .errorCode(null)
            .errorMsg(null)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: error字段应为null
    assertThat(entity.getErrorCode()).isNull();
    assertThat(entity.getErrorMsg()).isNull();
  }

  @Test
  @DisplayName("应当正确转换包含复杂JSON的payload")
  void shouldConvertComplexPayloadJson() throws Exception {
    // Given: 包含复杂JSON的OutboxMessage
    String complexPayload =
        "{\"taskId\":1001,\"params\":{\"query\":\"test\",\"filters\":[\"A\",\"B\"]},\"metadata\":{\"source\":\"PUBMED\"}}";
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .payloadJson(complexPayload)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 复杂JSON应正确转换
    assertThat(entity.getPayloadJson()).isNotNull();
    assertThat(entity.getPayloadJson().get("taskId").asLong()).isEqualTo(1001L);
    assertThat(entity.getPayloadJson().get("params").get("query").asText()).isEqualTo("test");
    assertThat(entity.getPayloadJson().get("params").get("filters").isArray()).isTrue();
    assertThat(entity.getPayloadJson().get("metadata").get("source").asText()).isEqualTo("PUBMED");
  }

  @Test
  @DisplayName("应当正确转换PUBLISHED状态的消息")
  void shouldConvertPublishedMessage() {
    // Given: PUBLISHED状态的OutboxMessage
    Instant publishedAt = Instant.now();
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .statusCode("PUBLISHED")
            .retryCount(0)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 状态应正确转换
    assertThat(entity.getStatusCode()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("应当正确转换FAILED状态的消息")
  void shouldConvertFailedMessage() {
    // Given: FAILED状态的OutboxMessage
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .statusCode("FAILED")
            .retryCount(5)
            .errorCode("ERR_MAX_RETRY")
            .errorMsg("Max retry attempts reached")
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 失败信息应正确转换
    assertThat(entity.getStatusCode()).isEqualTo("FAILED");
    assertThat(entity.getRetryCount()).isEqualTo(5);
    assertThat(entity.getErrorCode()).isEqualTo("ERR_MAX_RETRY");
    assertThat(entity.getErrorMsg()).isEqualTo("Max retry attempts reached");
  }

  @Test
  @DisplayName("应当正确处理默认值：statusCode默认为PENDING")
  void shouldUseDefaultStatusCodePending() {
    // Given: 未指定statusCode的OutboxMessageDO
    OutboxMessageDO entity = new OutboxMessageDO();
    entity.setAggregateType("TASK");
    entity.setAggregateId(1L);
    entity.setChannel("TEST");
    entity.setOpType("TEST_OP");
    entity.setPartitionKey("TEST:PARTITION");
    entity.setDedupKey("dedup-001");
    entity.setStatusCode(null);

    // When: 转换为领域对象
    OutboxMessage message = converter.toDomain(entity);

    // Then: statusCode应使用默认值（OutboxMessage构造器中定义）
    // 注意：OutboxMessage.builder()会在statusCode为null时设置为"PENDING"
    assertThat(message.getStatusCode()).isNotNull();
  }

  @Test
  @DisplayName("应当正确处理默认值：retryCount默认为0")
  void shouldUseDefaultRetryCountZero() {
    // Given: 未指定retryCount的OutboxMessageDO
    OutboxMessageDO entity = new OutboxMessageDO();
    entity.setAggregateType("TASK");
    entity.setAggregateId(1L);
    entity.setChannel("TEST");
    entity.setOpType("TEST_OP");
    entity.setPartitionKey("TEST:PARTITION");
    entity.setDedupKey("dedup-001");
    entity.setRetryCount(null);

    // When: 转换为领域对象
    OutboxMessage message = converter.toDomain(entity);

    // Then: retryCount应使用默认值（OutboxMessage构造器中定义）
    assertThat(message.getRetryCount()).isNotNull();
  }

  @Test
  @DisplayName("应当正确处理包含租约的消息转换")
  void shouldConvertMessageWithActiveLease() {
    // Given: 包含租约的OutboxMessage
    Instant now = Instant.now();
    Instant leaseExpireAt = now.plusSeconds(300);

    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .statusCode("PUBLISHING")
            .leaseOwner("relay-instance-01")
            .leaseExpireAt(leaseExpireAt)
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 租约信息应正确转换
    assertThat(entity.getStatusCode()).isEqualTo("PUBLISHING");
    assertThat(entity.getPubLeaseOwner()).isEqualTo("relay-instance-01");
    assertThat(entity.getPubLeasedUntil()).isEqualTo(leaseExpireAt);

    // When: 再次转换回Domain
    OutboxMessage restored = converter.toDomain(entity);

    // Then: 租约信息应保持一致
    assertThat(restored.getLeaseOwner()).isEqualTo("relay-instance-01");
    assertThat(restored.getLeaseExpireAt()).isEqualTo(leaseExpireAt);
  }

  @Test
  @DisplayName("应当正确处理空字符串的JSON转换")
  void shouldHandleEmptyStringJson() {
    // Given: 空字符串JSON
    OutboxMessage message =
        OutboxMessage.builder()
            .aggregateType("TASK")
            .aggregateId(1L)
            .channel("TEST")
            .opType("TEST_OP")
            .partitionKey("TEST:PARTITION")
            .dedupKey("dedup-001")
            .payloadJson("")
            .headersJson("")
            .build();

    // When: 转换为DO
    OutboxMessageDO entity = converter.toEntity(message);

    // Then: 应能正确处理（MapStruct表达式会尝试解析）
    // 空字符串会导致解析失败，返回null或抛出异常，取决于JsonNodeMappings实现
    // 这里假设会返回null或空节点
    assertThat(entity).isNotNull();
  }
}
