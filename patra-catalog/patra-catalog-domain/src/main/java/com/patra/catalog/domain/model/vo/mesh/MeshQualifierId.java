package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 修饰词标识符值对象。
///
/// 封装数据库主键（雪花 ID），提供编译时类型安全。
/// 防止 ID 类型混淆（如 MeshDescriptorId 误传给 MeshQualifierId）。
///
/// @param value 数据库主键值（雪花 ID）
/// @author Patra Lin
/// @since 0.6.0
public record MeshQualifierId(Long value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 ID 有效性。
  ///
  /// @throws IllegalArgumentException 如果 ID 为空或非正整数
  public MeshQualifierId {
    Assert.notNull(value, "MeshQualifierId 不能为空");
    Assert.isTrue(value > 0, "MeshQualifierId 必须为正整数: %d", value);
  }

  /// 静态工厂方法：从 Long 值创建 MeshQualifierId。
  ///
  /// @param value 数据库主键值
  /// @return MeshQualifierId 实例
  /// @throws IllegalArgumentException 如果 value 无效
  public static MeshQualifierId of(Long value) {
    return new MeshQualifierId(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
