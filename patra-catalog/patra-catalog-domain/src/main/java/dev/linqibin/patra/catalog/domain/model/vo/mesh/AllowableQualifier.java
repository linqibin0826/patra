package dev.linqibin.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 允许的限定词值对象。
///
/// 限定词说明：
///
/// - 限定词用于组合主题词，形成更精确的检索表达式
///   - 例如："Diabetes Mellitus/diagnosis"（糖尿病/诊断）
///   - 每个主题词平均有 23 个允许的限定词
///   - 缩写用于简化表达（如 AD = administration & dosage）
///
/// 使用示例：
///
/// ```java
/// AllowableQualifier qualifier = AllowableQualifier.of(
///     MeshUI.of("Q000008"),
///     "administration & dosage",
///     "AD"
/// );
/// ```
///
/// @param qualifierUi 限定词唯一标识符（格式：Q000001-Q999999）
/// @param qualifierName 限定词名称
/// @param abbreviation 缩写（通常 2 个字母）
/// @author linqibin
/// @since 0.2.0
public record AllowableQualifier(MeshUI qualifierUi, String qualifierName, String abbreviation)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 工厂方法。
  ///
  /// @param qualifierUi 限定词 UI
  /// @param qualifierName 限定词名称
  /// @param abbreviation 缩写
  /// @return 允许的限定词值对象
  public static AllowableQualifier of(
      MeshUI qualifierUi, String qualifierName, String abbreviation) {
    // 参数验证
    Assert.notNull(qualifierUi, "限定词UI不能为空");
    Assert.isTrue(qualifierUi.isQualifier(), "UI必须是限定词类型(Q开头)：%s", qualifierUi.ui());
    Assert.notBlank(qualifierName, "限定词名称不能为空");
    Assert.notBlank(abbreviation, "缩写不能为空");
    Assert.isTrue(abbreviation.length() <= 5, "缩写长度不能超过5个字符：%s", abbreviation);

    return new AllowableQualifier(qualifierUi, qualifierName, abbreviation);
  }
}
