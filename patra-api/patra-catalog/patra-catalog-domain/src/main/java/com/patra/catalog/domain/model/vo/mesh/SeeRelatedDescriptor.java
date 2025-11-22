package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 相关主题词值对象。
///
/// 相关主题词说明：
///
/// - 表示主题词之间的"另请参考"关系
///   - 用于引导用户查看相关但不完全等同的主题词
///   - 例如："Diabetes Mellitus" 的相关主题词可能是 "Hyperglycemia"
///   - 平均每个主题词有 0.2 个相关主题词（可选）
///
/// 使用示例：
///
/// ```java
/// SeeRelatedDescriptor related = SeeRelatedDescriptor.of(
///     MeshUI.of("D000001"),
///     "Calcimycin"
/// );
/// ```
///
/// @param descriptorUi 相关主题词的唯一标识符
/// @param descriptorName 主题词名称
/// @author linqibin
/// @since 0.2.0
public record SeeRelatedDescriptor(MeshUI descriptorUi, String descriptorName)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 工厂方法。
  ///
  /// @param descriptorUi 主题词 UI
  /// @param descriptorName 主题词名称
  /// @return 相关主题词值对象
  public static SeeRelatedDescriptor of(MeshUI descriptorUi, String descriptorName) {
    // 参数验证
    Assert.notNull(descriptorUi, "主题词UI不能为空");
    Assert.isTrue(descriptorUi.isDescriptor(), "UI必须是主题词类型(D开头)：%s", descriptorUi.ui());
    Assert.notBlank(descriptorName, "主题词名称不能为空");

    return new SeeRelatedDescriptor(descriptorUi, descriptorName);
  }
}
