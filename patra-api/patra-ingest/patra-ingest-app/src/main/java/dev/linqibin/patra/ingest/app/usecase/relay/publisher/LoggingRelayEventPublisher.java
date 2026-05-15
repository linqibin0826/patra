package dev.linqibin.patra.ingest.app.usecase.relay.publisher;

import dev.linqibin.patra.ingest.domain.event.OutboxRelayDomainEvent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 基于日志的事件发布器: 在 DEBUG 级别发出事件用于开发和诊断
///
/// 生产环境部署可以提高日志级别或用转发指标的发布器替换此发布器
@Slf4j
@Component
public class LoggingRelayEventPublisher implements RelayEventPublisher {

  @Override
  public void publish(List<OutboxRelayDomainEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    // 仅在 DEBUG 级别发出事件快照,避免生产环境噪音
    events.forEach(event -> log.debug("中继事件: {}", event));
  }
}
