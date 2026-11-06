package dev.patra.tool.test.archunit.fixtures.eventpublisher.adapterpublisher.adapter;

import org.springframework.context.ApplicationEventPublisher;

/// 夹具：adapter 层用发布器（违规）。
public class CommentController {

  private final ApplicationEventPublisher events;

  public CommentController(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void post() {
    events.publishEvent("comment");
  }
}
