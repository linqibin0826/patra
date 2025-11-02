package com.patra.starter.core.error.observation;

import com.patra.starter.core.error.model.ErrorResolution;

/** 发布在错误解析期间捕获的指标或遥测数据的抽象。 */
public interface ErrorObservationRecorder {

  /**
   * 记录单次错误解析运行的结果和耗时。
   *
   * @param exception 正在解析的原始异常
   * @param resolution 产生的规范化错误
   * @param durationMs 解析错误花费的时间（毫秒）
   * @param slow 执行是否超过配置的慢阈值
   */
  void recordResolution(
      Throwable exception, ErrorResolution resolution, long durationMs, boolean slow);

  /** 记录断路器产生回退响应而不是执行管道的情况。 */
  void recordCircuitBreakerFallback(Throwable exception);

  /** 无操作实现，便于在禁用观测时进行依赖注入。 */
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
