package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 载体实例标识符值对象。
///
/// 封装数据库主键（雪花 ID），提供编译时类型安全。
/// 防止 ID 类型混淆（如 VenueId 误传给 VenueInstanceId）。
///
/// @param value 数据库主键值（雪花 ID）
/// @author Patra Lin
/// @since 0.6.0
public record VenueInstanceId(Long value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 ID 有效性。
  ///
  /// @throws IllegalArgumentException 如果 ID 为空或非正整数
  public VenueInstanceId {
    Assert.notNull(value, "VenueInstanceId 不能为空");
    Assert.isTrue(value > 0, "VenueInstanceId 必须为正整数: %d", value);
  }

  /// 静态工厂方法：从 Long 值创建 VenueInstanceId。
  ///
  /// @param value 数据库主键值
  /// @return VenueInstanceId 实例
  /// @throws IllegalArgumentException 如果 value 无效
  public static VenueInstanceId of(Long value) {
    return new VenueInstanceId(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
