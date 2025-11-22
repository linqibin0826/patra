package com.patra.starter.feign.error.observation;

/// Feign 错误响应解码观察记录器抽象接口
/// 
/// 在解码 Feign 错误响应时记录可观测性指标，用于监控和诊断。
/// 
/// ### 主要用途
/// 
/// - 记录 ProblemDetail 解析性能和结果
///   - 跟踪错误解码成功率和容错模式使用情况
///   - 监控响应体读取性能和截断情况
///   - 记录跟踪标识符提取情况
/// 
/// ### 实现说明
/// 
/// - 提供 NO_OP 实现用于禁用观察时的零开销
///   - 生产环境使用 {@link MicrometerFeignErrorObservationRecorder} 实现
///   - 所有方法均为非阻塞,不影响主流程性能
/// 
public interface FeignErrorObservationRecorder {

  /// 空操作实现,用于禁用观察时
  FeignErrorObservationRecorder NO_OP =
      new FeignErrorObservationRecorder() {
        @Override
        public void recordProblemDetailParsing(
            String methodKey, int status, long durationMs, boolean success) {
          // 空操作
        }

        @Override
        public void recordDecodingOutcome(
            String methodKey, int status, boolean success, boolean tolerantMode) {
          // 空操作
        }

        @Override
        public void recordResponseBodyRead(
            String methodKey, int bodySize, long durationMs, boolean truncated) {
          // 空操作
        }

        @Override
        public void recordTraceIdExtraction(String methodKey, boolean found, String headerName) {
          // 空操作
        }
      };

  /// 记录下游服务 ProblemDetail 解析的结果和延迟
/// 
/// @param methodKey Feign 方法标识
/// @param status HTTP 状态码
/// @param durationMs 解析耗时(毫秒)
/// @param success 是否解析成功
  void recordProblemDetailParsing(String methodKey, int status, long durationMs, boolean success);

  /// 记录错误解码结果和容错模式使用情况
/// 
/// @param methodKey Feign 方法标识
/// @param status HTTP 状态码
/// @param success 解码是否成功
/// @param tolerantMode 是否使用了容错模式
  void recordDecodingOutcome(String methodKey, int status, boolean success, boolean tolerantMode);

  /// 记录响应体读取性能和截断情况
/// 
/// @param methodKey Feign 方法标识
/// @param bodySize 响应体大小(字节)
/// @param durationMs 读取耗时(毫秒)
/// @param truncated 是否被截断
  void recordResponseBodyRead(String methodKey, int bodySize, long durationMs, boolean truncated);

  /// 记录下游响应头中是否包含跟踪标识符
/// 
/// @param methodKey Feign 方法标识
/// @param found 是否找到跟踪标识符
/// @param headerName 跟踪标识符所在的响应头名称,未找到时为 null
  void recordTraceIdExtraction(String methodKey, boolean found, String headerName);
}
