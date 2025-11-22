package com.patra.catalog.domain.model.valueobject;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 导入任务强类型 ID。
/// 
/// 包装 Long 类型的雪花 ID，提供编译期类型安全。
/// 
/// **设计原则**：
/// 
/// - 不可变性：使用 record 自动提供
///   - 类型安全：防止不同类型的 ID 混淆
///   - 值验证：确保 ID 不为 null 且为正数
///   - 纯领域对象：不依赖任何框架
/// 
/// **使用示例**：
/// 
/// ```java
/// // 创建强类型 ID
/// MeshImportId id = MeshImportId.of(1734567890123456789L);
/// 
/// // 获取原始值
/// Long rawValue = id.value();
/// 
/// // 比较 ID
/// if (id1.equals(id2)) {
///     // 相同的导入任务
/// ```
/// 
/// @param value 雪花 ID 值（必须为正数）
/// @author linqibin
/// @since 0.2.0
public record MeshImportId(Long value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 ID 的有效性。
/// 
/// @throws IllegalArgumentException 如果 ID 为 null 或非正数
  public MeshImportId {
    Assert.notNull(value, "MeSH 导入任务 ID 不能为 null");
    Assert.isTrue(value > 0, "MeSH 导入任务 ID 必须为正数：%d", value);
  }

  /// 从 Long 创建强类型 ID。
/// 
/// @param value 雪花 ID（必须为正数）
/// @return MeshImportId 实例
/// @throws IllegalArgumentException 如果 ID 为 null 或非正数
  public static MeshImportId of(Long value) {
    return new MeshImportId(value);
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
