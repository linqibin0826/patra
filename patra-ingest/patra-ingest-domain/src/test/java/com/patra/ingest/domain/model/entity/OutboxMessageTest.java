package com.patra.ingest.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link OutboxMessage} 构建器测试，覆盖必填校验与默认值。 */
class OutboxMessageTest {

  private OutboxMessage.Builder base() {
    return OutboxMessage.builder()
        .aggregateType("PLAN")
        .aggregateId(1L)
        .channel("INGEST_TASK_READY")
        .opType("HARVEST")
        .partitionKey("p1")
        .dedupKey("d1")
        .payloadJson("{}")
        .headersJson(null)
        .notBefore(Instant.parse("2024-01-01T00:00:00Z"));
  }

  @Test
  @DisplayName("必填字段缺失抛出 NPE")
  void requiredFields() {
    assertThrows(NullPointerException.class, () -> OutboxMessage.builder().build());
    assertThrows(NullPointerException.class, () -> base().aggregateType(null).build());
    assertThrows(NullPointerException.class, () -> base().aggregateId(null).build());
    assertThrows(NullPointerException.class, () -> base().channel(null).build());
    assertThrows(NullPointerException.class, () -> base().opType(null).build());
    assertThrows(NullPointerException.class, () -> base().partitionKey(null).build());
    assertThrows(NullPointerException.class, () -> base().dedupKey(null).build());
  }

  @Test
  @DisplayName("默认值：status=PENDING，retryCount=0；toBuilder 可回放")
  void defaultsAndToBuilder() {
    OutboxMessage m = base().build();
    assertEquals("PENDING", m.getStatusCode());
    assertEquals(0, m.getRetryCount());

    OutboxMessage m2 = m.toBuilder().statusCode("PUBLISHED").retryCount(3).build();
    assertEquals("PUBLISHED", m2.getStatusCode());
    assertEquals(3, m2.getRetryCount());
  }
}
