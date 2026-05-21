package dev.linqibin.starter.observability.interceptor;

import dev.linqibin.starter.core.error.model.ErrorResolution;
import dev.linqibin.starter.core.error.pipeline.ResolutionInterceptor;
import dev.linqibin.starter.core.error.pipeline.ResolutionInvocation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;

/// 错误解析可观测性拦截器。
///
/// 功能：
///
/// - 为错误解析流程创建 Observation
/// - 自动记录错误类型、错误类、解析结果等关键信息
/// - 与其他可观测性组件（日志、指标、追踪）集成
///
/// 实现模式：
///
/// - 在 beforeResolve 阶段创建并启动 Observation
/// - 将 Observation 存储在调用链上下文中
/// - 在 afterResolve 阶段停止 Observation 并记录结果
/// - 异常时记录错误事件
///
/// Observation 标签：
///
/// - error.class - 异常的完全限定类名
/// - error.type - 异常的简单类名
/// - resolution.success - 解析是否成功（true/false）
/// - resolution.strategy -
// 使用的解析策略（cache/application-exception/contributor/trait/naming/cause/fallback）
/// - resolution.depth - 原因链递归深度（仅在 CAUSE 策略时有意义）
///
/// 执行顺序：
///
/// - 使用 Ordered.HIGHEST_PRECEDENCE 确保最早执行
/// - 这样可以捕获整个错误解析流程的时间
///
/// @author Jobs
/// @since 1.0.0
public class ObservationResolutionInterceptor implements ResolutionInterceptor, Ordered {

  private static final Logger log = LoggerFactory.getLogger(ObservationResolutionInterceptor.class);

  private static final String OBSERVATION_NAME = "error.resolution";
  private static final String OBSERVATION_CONTEXT_KEY = "observation";

  private final ObservationRegistry observationRegistry;

  /// 构造函数。
  ///
  /// @param observationRegistry Observation 注册中心
  public ObservationResolutionInterceptor(ObservationRegistry observationRegistry) {
    this.observationRegistry = observationRegistry;
    log.info("初始化错误解析可观测性拦截器");
  }

  /// 拦截错误解析流程。
  ///
  /// 流程：
  ///
  /// - 创建 Observation 并添加错误信息标签
  /// - 启动 Observation
  /// - 调用下一个拦截器或解析引擎
  /// - 停止 Observation 并记录解析结果
  /// - 如果发生异常，记录错误事件
  ///
  /// @param exception  正在解析的异常
  /// @param invocation 管道调用对象
  /// @return 解析后的错误
  @Override
  public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
    // 创建 Observation
    Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry);

    // 添加低基数标签（错误类型信息）
    observation.lowCardinalityKeyValue("error.class", exception.getClass().getName());
    observation.lowCardinalityKeyValue("error.type", exception.getClass().getSimpleName());

    // 启动 Observation
    observation.start();

    try {
      // 调用下一个拦截器或解析引擎
      ErrorResolution resolution = invocation.proceed(exception);

      // 记录解析结果和策略
      observation.lowCardinalityKeyValue("resolution.success", "true");
      observation.lowCardinalityKeyValue("resolution.strategy", resolution.strategy().getValue());

      log.debug(
          "错误解析成功: {} (策略: {})",
          exception.getClass().getSimpleName(),
          resolution.strategy().getValue());

      return resolution;

    } catch (Exception e) {
      // 记录解析失败
      observation.lowCardinalityKeyValue("resolution.success", "false");
      observation.error(e);

      log.error("错误解析失败: {}", exception.getClass().getSimpleName(), e);

      throw e;

    } finally {
      // 停止 Observation（无论成功或失败）
      observation.stop();
    }
  }

  /// 获取拦截器执行顺序。
  ///
  /// 使用最高优先级确保最早执行，这样可以：
  ///
  /// - 捕获整个错误解析流程的时间
  /// - 包含其他拦截器的执行时间
  ///
  /// @return 执行顺序值
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
