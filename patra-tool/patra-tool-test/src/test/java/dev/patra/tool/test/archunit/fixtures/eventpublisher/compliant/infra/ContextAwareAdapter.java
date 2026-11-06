package dev.patra.tool.test.archunit.fixtures.eventpublisher.compliant.infra;

import org.springframework.context.ApplicationContext;

/// 夹具：只用 `ApplicationContext` 取 bean，没有发事件。
public class ContextAwareAdapter {

  private final ApplicationContext context;

  public ContextAwareAdapter(ApplicationContext context) {
    this.context = context;
  }

  public Object bean() {
    return context.getBean("bean");
  }
}
