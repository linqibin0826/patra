package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.EnumSet;
import java.util.Set;

/// 计划组装异常。
/// 
/// 触发场景:在验证通过后,切片/任务生成阶段无法产生可执行的批次。
/// 
/// 与 {@link PlanValidationException} 的区别:本异常表示输入参数有效,但组装算法或组合规则失败;而 {@link
/// PlanValidationException} 表示输入参数本身无效。
/// 
/// 处理策略:
/// 
/// - {@link Reason#EMPTY_RESULT}: 视为空窗口,记录 INFO 级别日志并停止创建(正常情况)。
///   - {@link Reason#SLICE_GENERATION_FAILED} / {@link Reason#TASK_GENERATION_FAILED}: 记录 ERROR
///       级别日志,检查策略实现或基准数据。
/// 
/// @author linqibin
/// @since 0.1.0
public class PlanAssemblyException extends IngestException implements HasErrorTraits {

  /// 组装失败的根因。
/// 
/// 帮助调用方选择日志级别和告警策略。
  public enum Reason {
    /// 算法执行成功但产生空结果(窗口内无数据)。
    EMPTY_RESULT,
    /// 生成切片时失败(窗口分区或边界计算错误)。
    SLICE_GENERATION_FAILED,
    /// 生成任务时失败(切片到任务的映射或参数组装问题)。
    TASK_GENERATION_FAILED
  }

  /// 失败原因;如果未区分则可能为 `null`。
  private final Reason reason;

  /// 构造计划组装异常(不指定具体原因)。
/// 
/// @param message 描述性消息
  public PlanAssemblyException(String message) {
    this(message, null, null);
  }

  /// 构造计划组装异常并指定原因。
/// 
/// @param message 描述性消息
/// @param reason 失败原因
  public PlanAssemblyException(String message, Reason reason) {
    this(message, reason, null);
  }

  /// 构造计划组装异常并指定原因和底层原因。
/// 
/// @param message 描述性消息
/// @param reason 失败原因
/// @param cause 底层原因
  public PlanAssemblyException(String message, Reason reason, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  /// 构造计划组装异常并附带底层原因(不指定 Reason)。
/// 
/// @param message 描述性消息
/// @param cause 底层原因
  public PlanAssemblyException(String message, Throwable cause) {
    this(message, null, cause);
  }

  /// 获取失败原因。
/// 
/// @return 失败原因,可能为 `null`
  public Reason getReason() {
    return reason;
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return EnumSet.of(ErrorTrait.RULE_VIOLATION);
  }
}
