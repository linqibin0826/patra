package com.patra.starter.core.error.observation;

import com.patra.starter.core.error.model.ErrorResolution;

/// 错误观测记录器接口。
///
/// 定义在错误解析期间捕获指标和遥测数据的抽象契约,支持性能监控和观测能力。
///
/// 实现类:
///
/// - {@link MicrometerErrorObservationRecorder} - 基于 Micrometer 的实现
///   - {@link #NO_OP} - 禁用观测时的空实现
///
/// @author linqibin
/// @since 0.1.0
public interface ErrorObservationRecorder {

  /// 记录单次错误解析的结果和耗时。
  ///
  /// @param exception 待解析的原始异常
  /// @param resolution 解析产生的规范化错误
  /// @param durationMs 解析耗时(毫秒)
  /// @param slow 是否为慢解析(超过配置阈值)
  void recordResolution(
      Throwable exception, ErrorResolution resolution, long durationMs, boolean slow);

  /// 记录熔断器降级事件。
  ///
  /// 当熔断器打开时,记录降级响应而非执行完整的解析管道。
  ///
  /// @param exception 触发降级的原始异常
  void recordCircuitBreakerFallback(Throwable exception);

  /// 无操作实现,便于在禁用观测时进行依赖注入。
  ///
  /// 所有方法均为空实现,不执行任何观测记录逻辑。
  ErrorObservationRecorder NO_OP =
      new ErrorObservationRecorder() {
        @Override
        public void recordResolution(
            Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) {
          // 无操作
        }

        @Override
        public void recordCircuitBreakerFallback(Throwable exception) {
          // 无操作
        }
      };
}
