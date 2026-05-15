package com.patra.catalog.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// 调度参数异常。
///
/// 触发场景：XXL-Job 调度器调用处理器时，传入的 JSON 参数存在以下问题：
///
/// - 缺少必填字段（如 `url`、`meshVersion` 等）
/// - 违反格式约束（如 URL 格式错误、版本号为空）
/// - JSON 解析失败
///
/// 此错误归因于调用方输入问题，应立即返回并记录警告日志，**不应重试**。
///
/// 修复建议：
///
/// - **校验配置一致性**：确保调度器任务配置与处理器期望的参数模板保持同步
/// - **前置 Schema 验证**：在适配器层对 JSON 参数应用验证，实现快速失败
/// - **监控参数缺失**：埋点统计缺失字段的频率，及时发现模板漂移问题
///
/// @author linqibin
/// @since 0.1.0
public class CatalogScheduleParameterException extends CatalogException {

  /// 构造调度参数异常。
  ///
  /// @param message 人类可读的错误消息，应说明具体缺失或错误的参数
  public CatalogScheduleParameterException(String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
  }

  /// 构造调度参数异常并附带底层原因。
  ///
  /// 适用场景：包装 JSON 解析失败等底层错误。
  ///
  /// @param message 描述性消息
  /// @param cause 底层异常
  public CatalogScheduleParameterException(String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.RULE_VIOLATION);
  }
}
