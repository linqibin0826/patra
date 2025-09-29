package com.patra.ingest.app.relay.event;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日志型事件发布器：将事件以 DEBUG 级别输出，供开发与诊断使用。
 * <p>生产环境可通过提高日志级别或替换为复合发布器，写入指标系统。</p>
 */
@Slf4j
@Component
public class LoggingOutboxRelayEventPublisher implements OutboxRelayEventPublisher {

    @Override
    public void publish(List<OutboxRelayDomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        // 仅在 DEBUG 级别打印事件快照，避免生产环境噪音
        events.forEach(event -> log.debug("[INGEST][APP] relay-event: {}", event));
    }
}
