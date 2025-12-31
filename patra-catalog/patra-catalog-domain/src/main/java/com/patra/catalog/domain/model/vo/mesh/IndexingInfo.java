package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// SCR 索引信息值对象。
///
/// 表示 SCR（补充概念记录）的索引信息，包含引用的主题词、限定词或其他 SCR。
/// 索引信息用于建立 SCR 与其他 MeSH 记录之间的语义关联。
///
/// 业务规则：
///
/// - 至少需要一个有效的 UI（descriptorUi、qualifierUi 或 chemicalUi）
/// - descriptorUi 指向主题词（D开头）
/// - qualifierUi 指向限定词（Q开头）
/// - chemicalUi 指向其他 SCR（C开头）
///
/// @param descriptorUi 引用的主题词UI（可选）
/// @param qualifierUi 引用的限定词UI（可选）
/// @param chemicalUi 引用的化学物质/SCR UI（可选）
/// @author linqibin
/// @since 0.1.0
public record IndexingInfo(MeshUI descriptorUi, MeshUI qualifierUi, MeshUI chemicalUi)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数有效性。
  public IndexingInfo {
    // 至少需要一个有效的 UI
    Assert.isTrue(
        descriptorUi != null || qualifierUi != null || chemicalUi != null,
        "至少需要一个有效的 UI（descriptorUi、qualifierUi 或 chemicalUi）");

    // 类型验证
    if (descriptorUi != null) {
      Assert.isTrue(descriptorUi.isDescriptor(), "主题词UI必须以D开头：%s", descriptorUi.ui());
    }
    if (qualifierUi != null) {
      Assert.isTrue(qualifierUi.isQualifier(), "限定词UI必须以Q开头：%s", qualifierUi.ui());
    }
    if (chemicalUi != null) {
      Assert.isTrue(chemicalUi.isScr(), "化学物质UI必须以C开头：%s", chemicalUi.ui());
    }
  }

  /// 创建只有主题词的索引信息。
  ///
  /// @param descriptorUi 主题词UI
  /// @return 索引信息
  public static IndexingInfo ofDescriptor(MeshUI descriptorUi) {
    return new IndexingInfo(descriptorUi, null, null);
  }

  /// 创建带限定词的索引信息。
  ///
  /// @param descriptorUi 主题词UI
  /// @param qualifierUi 限定词UI
  /// @return 索引信息
  public static IndexingInfo ofDescriptorWithQualifier(MeshUI descriptorUi, MeshUI qualifierUi) {
    return new IndexingInfo(descriptorUi, qualifierUi, null);
  }

  /// 创建只有化学物质的索引信息。
  ///
  /// @param chemicalUi 化学物质UI
  /// @return 索引信息
  public static IndexingInfo ofChemical(MeshUI chemicalUi) {
    return new IndexingInfo(null, null, chemicalUi);
  }

  /// 创建完整的索引信息。
  ///
  /// @param descriptorUi 主题词UI（可为 null）
  /// @param qualifierUi 限定词UI（可为 null）
  /// @param chemicalUi 化学物质UI（可为 null）
  /// @return 索引信息
  public static IndexingInfo of(MeshUI descriptorUi, MeshUI qualifierUi, MeshUI chemicalUi) {
    return new IndexingInfo(descriptorUi, qualifierUi, chemicalUi);
  }

  /// 判断是否有主题词。
  public boolean hasDescriptor() {
    return descriptorUi != null;
  }

  /// 判断是否有限定词。
  public boolean hasQualifier() {
    return qualifierUi != null;
  }

  /// 判断是否有化学物质。
  public boolean hasChemical() {
    return chemicalUi != null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("IndexingInfo[");
    boolean first = true;
    if (hasDescriptor()) {
      sb.append("D=").append(descriptorUi.ui());
      first = false;
    }
    if (hasQualifier()) {
      if (!first) sb.append(", ");
      sb.append("Q=").append(qualifierUi.ui());
      first = false;
    }
    if (hasChemical()) {
      if (!first) sb.append(", ");
      sb.append("C=").append(chemicalUi.ui());
    }
    sb.append("]");
    return sb.toString();
  }
}
