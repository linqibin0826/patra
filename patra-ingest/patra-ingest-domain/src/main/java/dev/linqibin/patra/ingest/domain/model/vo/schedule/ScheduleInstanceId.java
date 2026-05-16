package dev.linqibin.patra.ingest.domain.model.vo.schedule;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 调度实例标识符值对象。
///
/// 封装数据库主键（雪花 ID），提供编译时类型安全。
/// 防止 ID 类型混淆（如 PlanId 误传给 ScheduleInstanceId）。
///
/// @param value 数据库主键值（雪花 ID）
/// @author Patra Lin
/// @since 0.6.0
public record ScheduleInstanceId(Long value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 ID 有效性。
  ///
  /// @throws IllegalArgumentException 如果 ID 为空或非正整数
  public ScheduleInstanceId {
    Assert.notNull(value, "ScheduleInstanceId 不能为空");
    Assert.isTrue(value > 0, "ScheduleInstanceId 必须为正整数: %d", value);
  }

  /// 静态工厂方法：从 Long 值创建 ScheduleInstanceId。
  ///
  /// @param value 数据库主键值
  /// @return ScheduleInstanceId 实例
  /// @throws IllegalArgumentException 如果 value 无效
  public static ScheduleInstanceId of(Long value) {
    return new ScheduleInstanceId(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
