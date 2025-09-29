package com.patra.ingest.infra.messaging.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.value.TaskReadyMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TaskReadyMessageMapper} 的单元测试。
 */
class TaskReadyMessageMapperTest {

    private OutboxMessage.Builder base() {
        return OutboxMessage.builder()
                .aggregateType("PLAN")
                .aggregateId(1L)
                .channel("ingest.task.ready")
                .opType("HARVEST")
                .partitionKey("p1")
                .dedupKey("d1");
    }

    @Test
    @DisplayName("payload 必须是合法 JSON；headers 可空")
    void mapPayloadAndOptionalHeader() {
        TaskReadyMessageMapper mapper = new TaskReadyMessageMapper(new ObjectMapper());
        String payloadJson = "{\"taskId\":1,\"planId\":2,\"sliceId\":3,\"provenance\":\"P\",\"operation\":\"O\",\"idempotentKey\":\"k\",\"priority\":1,\"scheduledAt\":\"2024-01-01T00:00:00Z\",\"params\":\"{}\",\"planKey\":\"pk\",\"planWindowFrom\":\"2024-01-01T00:00:00Z\",\"planWindowTo\":\"2024-01-02T00:00:00Z\",\"planSliceStrategy\":\"TIME\",\"planSliceParams\":\"{}\"}";
        String headerJson = "{\"scheduleInstanceId\":10,\"scheduler\":\"xxl\",\"schedulerJobId\":11,\"schedulerLogId\":12,\"triggerType\":\"MANUAL\",\"triggeredAt\":\"2024-01-01T00:00:00Z\",\"occurredAt\":\"2024-01-01T00:00:00Z\",\"planKey\":\"pk\",\"planOperation\":\"HARVEST\",\"planEndpoint\":\"SEARCH\"}";

        OutboxMessage msg = base().payloadJson(payloadJson).headersJson(null).build();
        TaskReadyMessage mapped = mapper.map(msg);
        assertNotNull(mapped.payload());
        assertNull(mapped.header());

        OutboxMessage msg2 = base().payloadJson(payloadJson).headersJson(headerJson).build();
        TaskReadyMessage mapped2 = mapper.map(msg2);
        assertNotNull(mapped2.payload());
        assertNotNull(mapped2.header());
        assertEquals(10L, mapped2.header().scheduleInstanceId());
    }

    @Test
    @DisplayName("非法 JSON 应抛出 OutboxRelayExecutionException")
    void invalidJsonShouldThrow() {
        TaskReadyMessageMapper mapper = new TaskReadyMessageMapper(new ObjectMapper());
        OutboxMessage msg = base().payloadJson("not-json").headersJson("{bad}").build();
        assertThrows(OutboxRelayExecutionException.class, () -> mapper.map(msg));
    }
}
