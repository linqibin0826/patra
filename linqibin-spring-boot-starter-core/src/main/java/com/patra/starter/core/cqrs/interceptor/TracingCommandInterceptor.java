package com.patra.starter.core.cqrs.interceptor;

import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.cqrs.CommandInterceptor;
import dev.linqibin.commons.cqrs.CommandInterceptor.CommandExecutor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// 追踪拦截器。
///
/// 使用 Micrometer Observation API 创建 Span，支持分布式追踪。
/// 当配合 OpenTelemetry 或 Brave 使用时，会自动创建追踪 Span。
///
/// ## 追踪输出示例
///
/// ```
/// Span: command:CreateUserCommand
/// Tags:
///   - command.type: com.example.CreateUserCommand
///   - command.name: CreateUserCommand
/// ```
///
/// ## 条件装配
///
/// - 仅当类路径中存在 `ObservationRegistry` 时启用
/// - 仅当容器中存在 `ObservationRegistry` Bean 时启用
/// - 通过配置 `patra.command-bus.interceptors.tracing=false` 禁用
///
/// ## Order 说明
///
/// Order=50 确保追踪拦截器在最外层，使 Span 覆盖整个命令执行周期，
/// 包括日志和指标采集。
@Component
@Order(50)
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
@ConditionalOnProperty(
    prefix = "patra.command-bus.interceptors",
    name = "tracing",
    havingValue = "true",
    matchIfMissing = true)
public class TracingCommandInterceptor implements CommandInterceptor {

  private static final String OBSERVATION_NAME = "command";
  private static final String TAG_COMMAND_TYPE = "command.type";
  private static final String TAG_COMMAND_NAME = "command.name";

  private final ObservationRegistry observationRegistry;

  public TracingCommandInterceptor(ObservationRegistry observationRegistry) {
    this.observationRegistry = observationRegistry;
  }

  @Override
  public <R> R intercept(Command<R> command, CommandExecutor<R> next) {
    String cmdName = command.getClass().getSimpleName();
    String cmdType = command.getClass().getName();

    Observation observation =
        Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
            .contextualName(cmdName)
            .lowCardinalityKeyValue(TAG_COMMAND_NAME, cmdName)
            .highCardinalityKeyValue(TAG_COMMAND_TYPE, cmdType);

    return observation.observe(() -> next.execute(command));
  }
}
