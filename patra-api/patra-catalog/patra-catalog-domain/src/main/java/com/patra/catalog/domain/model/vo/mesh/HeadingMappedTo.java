package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// SCR 到 Descriptor 的映射关系值对象。
///
/// 表示 SCR（补充概念记录）映射到主题词（Descriptor）的关系。
/// 这是 SCR 最核心的关系，用于建立补充概念与主题词之间的语义关联。
///
/// 业务规则：
///
/// - 每个 SCR 可以映射到多个 Descriptor
/// - descriptorUi 是必需的，表示目标主题词
/// - qualifierUi 是可选的，用于进一步限定映射关系
/// - majorTopic 表示该描述符是否为主要主题词（NLM 用星号标记）
///
/// 使用示例：
///
/// ```java
/// // 创建简单映射
/// HeadingMappedTo mapping = HeadingMappedTo.of(MeshUI.of("D000001"));
///
/// // 创建带限定词的映射
/// HeadingMappedTo mappingWithQualifier = HeadingMappedTo.of(
///     MeshUI.of("D000001"),
///     MeshUI.of("Q000002")
/// );
///
/// // 创建主要主题词映射
/// HeadingMappedTo majorMapping = HeadingMappedTo.of(
///     MeshUI.of("D000001"),
///     null,
///     true
/// );
/// ```
///
/// @param descriptorUi 目标主题词UI（必需）
/// @param qualifierUi 限定词UI（可选）
/// @param majorTopic 是否为主要主题词（Major Topic）
/// @author linqibin
/// @since 0.1.0
public record HeadingMappedTo(MeshUI descriptorUi, MeshUI qualifierUi, boolean majorTopic)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数有效性。
  public HeadingMappedTo {
    Assert.notNull(descriptorUi, "主题词UI不能为空");
    Assert.isTrue(descriptorUi.isDescriptor(), "主题词UI必须以D开头：%s", descriptorUi.ui());

    if (qualifierUi != null) {
      Assert.isTrue(qualifierUi.isQualifier(), "限定词UI必须以Q开头：%s", qualifierUi.ui());
    }
  }

  /// 创建只有主题词的映射（非主要主题词）。
  ///
  /// @param descriptorUi 主题词UI
  /// @return 映射关系
  public static HeadingMappedTo of(MeshUI descriptorUi) {
    return new HeadingMappedTo(descriptorUi, null, false);
  }

  /// 创建带限定词的映射（非主要主题词）。
  ///
  /// @param descriptorUi 主题词UI
  /// @param qualifierUi 限定词UI（可为 null）
  /// @return 映射关系
  public static HeadingMappedTo of(MeshUI descriptorUi, MeshUI qualifierUi) {
    return new HeadingMappedTo(descriptorUi, qualifierUi, false);
  }

  /// 创建完整的映射关系。
  ///
  /// @param descriptorUi 主题词UI
  /// @param qualifierUi 限定词UI（可为 null）
  /// @param majorTopic 是否为主要主题词
  /// @return 映射关系
  public static HeadingMappedTo of(MeshUI descriptorUi, MeshUI qualifierUi, boolean majorTopic) {
    return new HeadingMappedTo(descriptorUi, qualifierUi, majorTopic);
  }

  /// 判断是否有限定词。
  ///
  /// @return true 如果有限定词
  public boolean hasQualifier() {
    return qualifierUi != null;
  }

  @Override
  public String toString() {
    String majorPrefix = majorTopic ? "*" : "";
    if (hasQualifier()) {
      return String.format(
          "HeadingMappedTo[%s%s/%s]", majorPrefix, descriptorUi.ui(), qualifierUi.ui());
    }
    return String.format("HeadingMappedTo[%s%s]", majorPrefix, descriptorUi.ui());
  }
}
