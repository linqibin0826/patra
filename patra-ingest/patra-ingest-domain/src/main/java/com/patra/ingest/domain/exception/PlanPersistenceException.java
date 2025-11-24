package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.StandardErrorTrait;

/// 计划持久化异常。
///
/// 触发场景:在编排采集计划时,对调度实例、计划、切片、任务和任务尝试的写入/更新/查询失败。
///
/// 常见原因:
///
/// - 数据库宕机或连接池耗尽
///   - 网络抖动导致超时
///   - 乐观锁冲突(版本号不匹配)
///   - 序列号生成问题
///
/// 处理策略:
///
/// - **临时性连接/超时**:限次重试。
///   - **乐观锁冲突**:使用 {@link #getStage()} 判断哪个实体需要重建或重新读取。
///   - **约束违反**:记录日志并告警,不应盲目重试。
///
/// @author linqibin
/// @since 0.1.0
public class PlanPersistenceException extends IngestException {

  /// 持久化操作的阶段分类。
  ///
  /// 为差异化的重试/补偿策略提供上下文。
  public enum Stage {
    /// 持久化调度实例时失败。
    SCHEDULE_INSTANCE,
    /// 写入/更新计划聚合时失败。
    PLAN,
    /// 持久化计划切片时失败。
    PLAN_SLICE,
    /// 写入/更新任务时失败。
    TASK,
    /// 记录任务重试/尝试时失败。
    TASK_RETRY
  }

  /// 失败发生的阶段。
  private final Stage stage;

  /// 构造计划持久化异常。
  ///
  /// @param stage 失败阶段
  /// @param message 描述性消息
  public PlanPersistenceException(Stage stage, String message) {
    this(stage, message, null);
  }

  /// 构造计划持久化异常并附带底层原因。
  ///
  /// @param stage 失败阶段
  /// @param message 描述性消息
  /// @param cause 底层原因
  public PlanPersistenceException(Stage stage, String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.DEP_UNAVAILABLE);
    this.stage = stage;
  }

  /// 获取失败发生的阶段。
  ///
  /// @return 阶段枚举
  public Stage getStage() {
    return stage;
  }
}
