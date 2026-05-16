package dev.linqibin.starter.restclient.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/// 长时间运行客户端启用条件。
///
/// 当 `linqibin.starter.rest-client.clients.long-running.enabled` 为 `true` 或未配置时，
/// 条件匹配；当显式配置为 `false` 时，条件不匹配。
///
/// @author linqibin
/// @since 0.1.0
class LongRunningClientEnabledCondition extends SpringBootCondition {

  private static final String ENABLED_PROPERTY =
      "linqibin.starter.rest-client.clients.long-running.enabled";

  @Override
  public ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    var environment = context.getEnvironment();
    var enabled = environment.getProperty(ENABLED_PROPERTY, Boolean.class, true);

    if (enabled) {
      return ConditionOutcome.match("Long-running client is enabled (default or explicit)");
    } else {
      return ConditionOutcome.noMatch("Long-running client is explicitly disabled");
    }
  }
}
