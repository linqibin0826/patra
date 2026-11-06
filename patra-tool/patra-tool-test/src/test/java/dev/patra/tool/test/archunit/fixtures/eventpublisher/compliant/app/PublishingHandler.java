package dev.patra.tool.test.archunit.fixtures.eventpublisher.compliant.app;

import org.springframework.context.ApplicationEventPublisher;

/// 夹具：app 层用发布器。
public class PublishingHandler {

  private final ApplicationEventPublisher events;

  public PublishingHandler(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void handle() {
    events.publishEvent("event");
  }
}
