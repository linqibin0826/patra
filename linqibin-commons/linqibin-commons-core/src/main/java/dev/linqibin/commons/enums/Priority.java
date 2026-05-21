package dev.linqibin.commons.enums;

/// 任务优先级级别枚举,包含用于调度的关联队列值。
///
/// 队列值越小表示优先级越高。用于任务调度和队列系统确定执行顺序。
public enum Priority {
  /// 最高优先级,队列值 10。
  HIGHEST(10),

  /// 较高优先级,队列值 20。
  HIGHER(20),

  /// 高优先级,队列值 30。
  HIGH(30),

  /// 正常优先级,队列值 50(默认)。
  NORMAL(50),

  /// 低优先级,队列值 70。
  LOW(70),

  /// 较低优先级,队列值 80。
  LOWER(80),

  /// 最低优先级,队列值 90。
  LOWEST(90);

  private final int queueValue;

  /// 构造优先级枚举常量。
  ///
  /// @param queueValue 队列值,数字越小表示优先级越高
  Priority(int queueValue) {
    this.queueValue = queueValue;
  }

  /// 返回用于基于优先级调度的队列值。
  ///
  /// @return 队列值,数字越小表示优先级越高
  public int queueValue() {
    return queueValue;
  }
}
