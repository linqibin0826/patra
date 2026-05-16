package dev.linqibin.starter.core.cqrs.interceptor;

import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.commons.cqrs.CommandInterceptor;
import dev.linqibin.commons.cqrs.CommandInterceptor.CommandExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// 指标采集拦截器。
///
/// 使用 Micrometer 记录命令执行的指标：
/// - `command.execution` Timer：命令执行耗时
///   - `command` tag：命令类名
///   - `result` tag：success / failure
///
/// ## Prometheus 指标示例
///
/// ```
/// patra_command_execution_seconds_count{command="CreateUserCommand",result="success"} 42
/// patra_command_execution_seconds_sum{command="CreateUserCommand",result="success"} 1.234
/// ```
///
/// ## 条件装配
///
/// - 仅当容器中存在 `MeterRegistry` Bean 时启用
/// - 通过配置 `patra.command-bus.interceptors.metrics=false` 禁用
@Component
@Order(200)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "patra.command-bus.interceptors",
    name = "metrics",
    havingValue = "true",
    matchIfMissing = true)
public class MetricsCommandInterceptor implements CommandInterceptor {

  private static final String METRIC_NAME = "command.execution";
  private static final String TAG_COMMAND = "command";
  private static final String TAG_RESULT = "result";
  private static final String RESULT_SUCCESS = "success";
  private static final String RESULT_FAILURE = "failure";

  private final MeterRegistry meterRegistry;

  public MetricsCommandInterceptor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public <R> R intercept(Command<R> command, CommandExecutor<R> next) {
    String cmdName = command.getClass().getSimpleName();
    Timer.Sample sample = Timer.start(meterRegistry);

    try {
      R result = next.execute(command);
      sample.stop(
          Timer.builder(METRIC_NAME)
              .tag(TAG_COMMAND, cmdName)
              .tag(TAG_RESULT, RESULT_SUCCESS)
              .description("命令执行耗时")
              .register(meterRegistry));
      return result;
    } catch (Exception e) {
      sample.stop(
          Timer.builder(METRIC_NAME)
              .tag(TAG_COMMAND, cmdName)
              .tag(TAG_RESULT, RESULT_FAILURE)
              .description("命令执行耗时")
              .register(meterRegistry));
      throw e;
    }
  }
}
