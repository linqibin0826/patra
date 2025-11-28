package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 组合条目值对象。
///
/// 组合条目说明：
///
/// - 用于指导索引员如何标引某些复杂概念
/// - ECIN (Entry Combination In): 输入组合 - 当用户搜索此主题词+限定词时
/// - ECOUT (Entry Combination Out): 输出组合 - 应该用这个主题词+限定词替代
/// - 例如：搜索 "Eye Diseases/drug therapy" 应该重定向到 "Eye Diseases/therapy"
///
/// DTD 定义：
///
/// ```xml
/// <!ELEMENT EntryCombination (ECIN, ECOUT)>
/// <!ELEMENT ECIN (DescriptorReferredTo, QualifierReferredTo)>
/// <!ELEMENT ECOUT (DescriptorReferredTo, QualifierReferredTo?)>
/// ```
///
/// 使用示例：
///
/// ```java
/// EntryCombination combo = EntryCombination.of(
///     MeshUI.of("D005128"),  // ECIN Descriptor
///     MeshUI.of("Q000188"),  // ECIN Qualifier
///     MeshUI.of("D005128"),  // ECOUT Descriptor
///     MeshUI.of("Q000628")   // ECOUT Qualifier (可选)
/// );
/// ```
///
/// @param ecinDescriptorUi ECIN 主题词 UI（必填）
/// @param ecinQualifierUi ECIN 限定词 UI（必填）
/// @param ecoutDescriptorUi ECOUT 主题词 UI（必填）
/// @param ecoutQualifierUi ECOUT 限定词 UI（可选）
/// @author linqibin
/// @since 0.2.1
public record EntryCombination(
    MeshUI ecinDescriptorUi,
    MeshUI ecinQualifierUi,
    MeshUI ecoutDescriptorUi,
    MeshUI ecoutQualifierUi)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 工厂方法（完整参数）。
  ///
  /// @param ecinDescriptorUi ECIN 主题词 UI
  /// @param ecinQualifierUi ECIN 限定词 UI
  /// @param ecoutDescriptorUi ECOUT 主题词 UI
  /// @param ecoutQualifierUi ECOUT 限定词 UI（可选）
  /// @return 组合条目值对象
  public static EntryCombination of(
      MeshUI ecinDescriptorUi,
      MeshUI ecinQualifierUi,
      MeshUI ecoutDescriptorUi,
      MeshUI ecoutQualifierUi) {

    // ECIN 参数验证
    Assert.notNull(ecinDescriptorUi, "ECIN 主题词 UI 不能为空");
    Assert.isTrue(
        ecinDescriptorUi.isDescriptor(),
        "ECIN 主题词 UI 必须是 Descriptor 类型(D开头)：%s",
        ecinDescriptorUi.ui());

    Assert.notNull(ecinQualifierUi, "ECIN 限定词 UI 不能为空");
    Assert.isTrue(
        ecinQualifierUi.isQualifier(),
        "ECIN 限定词 UI 必须是 Qualifier 类型(Q开头)：%s",
        ecinQualifierUi.ui());

    // ECOUT Descriptor 参数验证
    Assert.notNull(ecoutDescriptorUi, "ECOUT 主题词 UI 不能为空");
    Assert.isTrue(
        ecoutDescriptorUi.isDescriptor(),
        "ECOUT 主题词 UI 必须是 Descriptor 类型(D开头)：%s",
        ecoutDescriptorUi.ui());

    // ECOUT Qualifier 可选，但如果提供了必须是正确类型
    if (ecoutQualifierUi != null) {
      Assert.isTrue(
          ecoutQualifierUi.isQualifier(),
          "ECOUT 限定词 UI 必须是 Qualifier 类型(Q开头)：%s",
          ecoutQualifierUi.ui());
    }

    return new EntryCombination(
        ecinDescriptorUi, ecinQualifierUi, ecoutDescriptorUi, ecoutQualifierUi);
  }

  /// 工厂方法（无 ECOUT Qualifier）。
  ///
  /// @param ecinDescriptorUi ECIN 主题词 UI
  /// @param ecinQualifierUi ECIN 限定词 UI
  /// @param ecoutDescriptorUi ECOUT 主题词 UI
  /// @return 组合条目值对象
  public static EntryCombination of(
      MeshUI ecinDescriptorUi, MeshUI ecinQualifierUi, MeshUI ecoutDescriptorUi) {
    return of(ecinDescriptorUi, ecinQualifierUi, ecoutDescriptorUi, null);
  }

  /// 判断是否有 ECOUT 限定词。
  ///
  /// @return true 如果有 ECOUT 限定词
  public boolean hasEcoutQualifier() {
    return ecoutQualifierUi != null;
  }
}
