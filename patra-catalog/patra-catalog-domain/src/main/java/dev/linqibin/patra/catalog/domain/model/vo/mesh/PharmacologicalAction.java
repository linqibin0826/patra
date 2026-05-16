package dev.linqibin.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 药理作用值对象。
///
/// 药理作用说明：
///
/// - 描述物质的药理学特性和作用机制
///   - 例如："Calcimycin" 有药理作用 "Anti-Bacterial Agents"（抗菌药物）
///   - 通过引用其他主题词来表达药理作用
///   - 平均每个主题词有 0.2 个药理作用（仅适用于化学物质和药物）
///
/// 使用示例：
///
/// ```java
/// PharmacologicalAction action = PharmacologicalAction.of(
///     MeshUI.of("D000900"),
///     "Anti-Bacterial Agents"
/// );
/// ```
///
/// @param descriptorUi 相关主题词的唯一标识符
/// @param descriptorName 主题词名称
/// @author linqibin
/// @since 0.2.0
public record PharmacologicalAction(MeshUI descriptorUi, String descriptorName)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 工厂方法。
  ///
  /// @param descriptorUi 主题词 UI
  /// @param descriptorName 主题词名称
  /// @return 药理作用值对象
  public static PharmacologicalAction of(MeshUI descriptorUi, String descriptorName) {
    // 参数验证
    Assert.notNull(descriptorUi, "主题词UI不能为空");
    Assert.isTrue(descriptorUi.isDescriptor(), "UI必须是主题词类型(D开头)：%s", descriptorUi.ui());
    Assert.notBlank(descriptorName, "主题词名称不能为空");

    return new PharmacologicalAction(descriptorUi, descriptorName);
  }
}
