package com.patra.ingest.infra.messaging;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.value.RelayPlan;
import com.patra.ingest.domain.model.value.TaskReadyMessage;
import com.patra.ingest.infra.messaging.support.TaskReadyMessageMapper;
import com.patra.starter.rocketmq.model.PatraMessage;
import com.patra.starter.rocketmq.publisher.PatraMessagePublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link RocketMqOutboxPublisher} 的单元测试（纯 Mockito，无 Spring）。
 */
class RocketMqOutboxPublisherTest {

    private OutboxMessage.Builder base() {
        return OutboxMessage.builder()
                .aggregateType("PLAN")
                .aggregateId(1L)
                .channel("ingest.task.ready")
                .opType("HARVEST")
                .partitionKey("trace-x")
                .dedupKey("event-1")
                .payloadJson("{\"taskId\":1}");
    }

    @Test
    @DisplayName("正常发布：构造 PatraMessage 并委托 publisher 发送，返回 NONE")
    void publishSuccess() throws Exception {
        TaskReadyMessageMapper mapper = mock(TaskReadyMessageMapper.class);
        PatraMessagePublisher publisher = mock(PatraMessagePublisher.class);
        RocketMqOutboxPublisher out = new RocketMqOutboxPublisher(mapper, publisher);
        OutboxMessage msg = base().headersJson(null).build();
        TaskReadyMessage body = new TaskReadyMessage(new TaskReadyMessage.Payload(1L,2L,3L, "P","O","k",1, null, "{}", "pk", null, null, null, null),
                new TaskReadyMessage.Header(100L, "xxl", 1L, 2L, "MANUAL", null, null, "pk", "HARVEST", "SEARCH"));
        when(mapper.map(any(OutboxMessage.class))).thenReturn(body);

        RelayPlan plan = new RelayPlan(
                com.patra.ingest.domain.messaging.IngestChannels.TASK_READY,
                Instant.parse("2024-01-01T00:00:00Z"),
                10,
                java.time.Duration.ofSeconds(30),
                3,
                java.time.Duration.ofSeconds(1),
                2.0,
                java.time.Duration.ofSeconds(10),
                "node-1");
        RocketMqOutboxPublisher.PublishResult result = out.publish(msg, plan);
        assertSame(RocketMqOutboxPublisher.PublishResult.NONE, result);

        ArgumentCaptor<PatraMessage<TaskReadyMessage>> captor = ArgumentCaptor.forClass((Class) PatraMessage.class);
        verify(publisher).sendByChannel(eq("ingest.task.ready"), captor.capture());
        PatraMessage<TaskReadyMessage> sent = captor.getValue();
        assertEquals("event-1", sent.getEventId());
        // header.scheduleInstanceId 存在 → traceId 应来自 header
        assertEquals("100", sent.getTraceId());
        // header.occurredAt 为空 → 应使用 plan.triggeredAt 作为 occurredAt
        assertEquals(plan.triggeredAt(), sent.getOccurredAt());
        assertSame(body, sent.getPayload());
    }

    @Test
    @DisplayName("下游抛异常时应向上抛出")
    void publishFailure() throws Exception {
        TaskReadyMessageMapper mapper = mock(TaskReadyMessageMapper.class);
        PatraMessagePublisher publisher = mock(PatraMessagePublisher.class);
        RocketMqOutboxPublisher out = new RocketMqOutboxPublisher(mapper, publisher);
        when(mapper.map(any())).thenReturn(new TaskReadyMessage(null, null));
        doThrow(new RuntimeException("mq down")).when(publisher).sendByChannel(anyString(), any());

        OutboxMessage msg = base().headersJson(null).build();
        RelayPlan plan = new RelayPlan(
                com.patra.ingest.domain.messaging.IngestChannels.TASK_READY,
                Instant.now(),
                10,
                java.time.Duration.ofSeconds(30),
                3,
                java.time.Duration.ofSeconds(1),
                2.0,
                java.time.Duration.ofSeconds(10),
                "node-1");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> out.publish(msg, plan));
        assertTrue(ex.getMessage().contains("mq down"));
    }
}
