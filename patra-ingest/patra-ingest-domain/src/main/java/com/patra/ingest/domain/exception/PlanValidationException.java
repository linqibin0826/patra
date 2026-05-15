package com.patra.ingest.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 计划验证异常。
///
/// 触发场景:在切片或组装之前执行的预检验证链失败,具体包括:
///
/// - 窗口有效性检查失败(窗口缺失、边界无效、跨度过大/过小)
///   - 队列背压检测到下游过载
///   - 能力/数据源/端点组合不受平台支持
///
/// 一旦抛出,表示请求不满足业务前提条件,**不应重试**。应调整调度器参数、时间窗口或系统配置后重新提交。
///
/// 处理指南:
///
/// - **应用层**:根据预期记录 INFO/WARN 级别日志,向上游返回可读消息,**不重试**。
///   - **监控**:聚合 {@link Reason} 以发现调度或配置缺陷。
///   - **诊断**:关联窗口起止时间、计划键和背压指标进行分析。
///
/// @author linqibin
/// @since 0.1.0
public class PlanValidationException extends IngestException {

  /// 验证失败的原因分类。
  ///
  /// 所有值都表示调用方可纠正的问题;不适合自动重试。
  public enum Reason {
    /// 调度器未提供窗口。
    WINDOW_MISSING,
    /// 窗口边界无效(start >= end、未对齐或超出范围)。
    WINDOW_INVALID,
    /// 窗口跨度超过允许的最大值(需要拆分或缩小)。
    WINDOW_TOO_LARGE,
    /// 窗口跨度太小,无法生成高效的切片。
    WINDOW_TOO_SMALL,
    /// 下游队列背压;暂时拒绝新计划。
    QUEUE_BACKPRESSURE,
    /// 能力/数据源/端点组合不受平台支持。
    CAPABILITY_MISMATCH
  }

  /// 具体的失败原因;如果未区分则为 `null`。
  private final Reason reason;

  /// 构造计划验证异常(不指定明确原因)。
  ///
  /// @param message 人类可读的失败描述
  public PlanValidationException(String message) {
    this(message, null, null);
  }

  /// 构造计划验证异常并指定原因。
  ///
  /// @param message 失败描述
  /// @param reason 失败原因
  public PlanValidationException(String message, Reason reason) {
    this(message, reason, null);
  }

  /// 构造计划验证异常并指定原因和底层原因。
  ///
  /// @param message 失败描述
  /// @param reason 失败原因
  /// @param cause 底层异常(可选)
  public PlanValidationException(String message, Reason reason, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
    this.reason = reason;
  }

  /// 构造计划验证异常并附带底层原因(不指定 Reason)。
  ///
  /// @param message 失败描述
  /// @param cause 底层异常
  public PlanValidationException(String message, Throwable cause) {
    this(message, null, cause);
  }

  /// 获取验证失败原因。
  ///
  /// @return 失败原因,可能为 `null`
  public Reason getReason() {
    return reason;
  }
}
