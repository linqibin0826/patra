package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.StandardErrorTrait;

/// 批次调度异常。
///
/// 触发场景:当批次调度由于以下原因无法继续时抛出:
///
/// - 输入参数无效(如窗口参数错误、数据源配置缺失)
///   - 上游元数据查询失败(patra-registry返回空结果或错误)
///   - 批次划分逻辑无法执行(如切片策略不适用于当前窗口)
///
/// 处理建议:
///
/// - 检查调度器传入的参数是否完整且格式正确
///   - 验证 patra-registry 中的 Provenance 配置是否存在
///   - 确认批次窗口是否符合业务规则(如最小/最大时间跨度)
///   - 查看日志中的具体错误消息定位问题根因
///
/// 重试策略:根据具体原因决定是否重试。配置缺失类错误不应重试;网络抖动导致的元数据查询失败可限次重试。
///
/// @author linqibin
/// @since 0.1.0
public class BatchSchedulingException extends IngestException {

  /// 构造批次调度异常。
  ///
  /// @param message 详细错误消息,应包含失败原因和涉及的调度参数
  public BatchSchedulingException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 构造批次调度异常并附带根本原因。
  ///
  /// @param message 详细错误消息
  /// @param cause 底层异常(如元数据查询的网络错误)
  public BatchSchedulingException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
  }
}
