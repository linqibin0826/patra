package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 载体评级标识符值对象。
///
/// 封装数据库主键（雪花 ID），提供编译时类型安全。
/// 防止 ID 类型混淆（如 VenueId 误传给 VenueRatingId）。
///
/// **设计原则**：
///
/// - 不可变性：Record 自动提供
/// - 类型安全：编译器可检测 ID 类型错误
/// - 验证：构造时确保 ID 有效（非空且为正整数）
///
/// **使用示例**：
///
/// ```java
/// // 从数据库 ID 创建
/// VenueRatingId id = VenueRatingId.of(12345L);
///
/// // 获取原始值
/// Long rawId = id.value();
/// ```
///
/// @param value 数据库主键值（雪花 ID）
/// @author Patra Lin
/// @since 0.6.0
public record VenueRatingId(Long value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 ID 有效性。
  ///
  /// @throws IllegalArgumentException 如果 ID 为空或非正整数
  public VenueRatingId {
    Assert.notNull(value, "VenueRatingId 不能为空");
    Assert.isTrue(value > 0, "VenueRatingId 必须为正整数: %d", value);
  }

  /// 静态工厂方法：从 Long 值创建 VenueRatingId。
  ///
  /// @param value 数据库主键值
  /// @return VenueRatingId 实例
  /// @throws IllegalArgumentException 如果 value 无效
  public static VenueRatingId of(Long value) {
    return new VenueRatingId(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
