package dev.patra.tool.test.archunit.fixtures.eventpublisher.infrapublisher.infra;

import org.springframework.context.ApplicationEventPublisher;

/// 夹具：普通 infra 适配器用发布器（违规）。
public class MailAdapter {

  private final ApplicationEventPublisher events;

  public MailAdapter(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void send() {
    events.publishEvent("mail");
  }
}
