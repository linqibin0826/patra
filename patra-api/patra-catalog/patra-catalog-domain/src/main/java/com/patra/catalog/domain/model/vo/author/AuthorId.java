package com.patra.catalog.domain.model.vo.author;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 作者标识符值对象。
///
/// 封装数据库主键（雪花 ID），提供编译时类型安全。
/// 防止 ID 类型混淆（如 AffiliationId 误传给 AuthorId）。
///
/// @param value 数据库主键值（雪花 ID）
/// @author Patra Lin
/// @since 0.6.0
public record AuthorId(Long value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 ID 有效性。
  ///
  /// @throws IllegalArgumentException 如果 ID 为空或非正整数
  public AuthorId {
    Assert.notNull(value, "AuthorId 不能为空");
    Assert.isTrue(value > 0, "AuthorId 必须为正整数: %d", value);
  }

  /// 静态工厂方法：从 Long 值创建 AuthorId。
  ///
  /// @param value 数据库主键值
  /// @return AuthorId 实例
  /// @throws IllegalArgumentException 如果 value 无效
  public static AuthorId of(Long value) {
    return new AuthorId(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
