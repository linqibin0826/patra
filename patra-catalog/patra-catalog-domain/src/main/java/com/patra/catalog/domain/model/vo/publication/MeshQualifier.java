package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 限定词值对象。
///
/// 封装 MeSH 主题词的限定词（Qualifier/Subheading）信息。
///
/// **业务含义**：
///
/// 限定词用于细化主题词的含义，例如：
/// - Diabetes Mellitus/**drug therapy** → 糖尿病的药物治疗
/// - Neoplasms/**diagnosis** → 肿瘤的诊断
/// - Heart/**physiology** → 心脏的生理学
///
/// **MajorTopic 标记**：
///
/// 当 `majorTopic=true` 时，表示该限定词修饰的主题是文献的主要讨论内容。
///
/// @param qualifierUi 限定词 UI（如 "Q000379"）
/// @param majorTopic 是否为主要主题
/// @param qualifierOrder 限定词顺序（在同一标引内的顺序）
/// @author linqibin
/// @since 0.1.0
public record MeshQualifier(String qualifierUi, boolean majorTopic, Integer qualifierOrder)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证限定词 UI 不为空。
  public MeshQualifier {
    Assert.isTrue(StrUtil.isNotBlank(qualifierUi), "限定词 UI 不能为空");
  }

  /// 创建限定词值对象。
  ///
  /// @param qualifierUi 限定词 UI
  /// @param majorTopic 是否为主要主题
  /// @param qualifierOrder 限定词顺序
  /// @return 限定词值对象
  public static MeshQualifier of(String qualifierUi, boolean majorTopic, Integer qualifierOrder) {
    return new MeshQualifier(qualifierUi, majorTopic, qualifierOrder);
  }

  /// 创建非主要主题的限定词。
  ///
  /// @param qualifierUi 限定词 UI
  /// @param order 限定词顺序
  /// @return 限定词值对象
  public static MeshQualifier ofMinor(String qualifierUi, Integer order) {
    return new MeshQualifier(qualifierUi, false, order);
  }

  /// 创建主要主题的限定词。
  ///
  /// @param qualifierUi 限定词 UI
  /// @param order 限定词顺序
  /// @return 限定词值对象
  public static MeshQualifier ofMajor(String qualifierUi, Integer order) {
    return new MeshQualifier(qualifierUi, true, order);
  }
}
