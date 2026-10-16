package dev.patra.tool.cqrs.interceptor;

import dev.patra.tool.core.PatraOrders;
import dev.patra.tool.cqrs.Command;
import dev.patra.tool.cqrs.CommandInterceptor;
import dev.patra.tool.cqrs.CommandInterceptor.CommandExecutor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.core.annotation.Order;

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
/// - 通过配置 `patra.cqrs.interceptors.tracing=false` 禁用
///
/// ## Order 说明
///
/// Order=50 确保追踪拦截器在最外层，使 Span 覆盖整个命令执行周期，
/// 包括日志和指标采集。
@Order(PatraOrders.CQRS_INTERCEPTOR_TRACING)
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
