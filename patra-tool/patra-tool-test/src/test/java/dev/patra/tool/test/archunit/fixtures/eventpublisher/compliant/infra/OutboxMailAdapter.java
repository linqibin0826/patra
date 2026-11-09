package dev.patra.tool.test.archunit.fixtures.eventpublisher.compliant.infra;

import org.springframework.context.ApplicationEventPublisher;

/// 夹具：写发件箱的适配器用发布器。
public class OutboxMailAdapter {

  private final ApplicationEventPublisher events;

  public OutboxMailAdapter(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void send() {
    events.publishEvent("mail");
  }
}
