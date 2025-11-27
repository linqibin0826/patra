package com.patra.catalog.domain.model.vo.mesh;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// MeSH 概念关系值对象。
///
/// 概念关系说明：
///
/// - 表示同一主题词内不同概念之间的语义关系
///   - 关系类型：NRW (Narrower) = 更窄、BRD (Broader) = 更宽、REL (Related) = 相关
///   - 例如：概念 "Calcimycin" (M0000001) 与 "A-23187" (M0353609) 的关系
///   - 用于构建概念的语义网络
///
/// 使用示例：
///
/// ```java
/// ConceptRelation relation = ConceptRelation.of(
///     "NRW",  // Narrower relationship
///     MeshUI.of("M0000001"),
///     MeshUI.of("M0353609")
/// );
/// ```
///
/// @param relationName 关系类型（NRW/BRD/REL等）
/// @param concept1Ui 概念1的唯一标识符
/// @param concept2Ui 概念2的唯一标识符
/// @author linqibin
/// @since 0.2.0
public record ConceptRelation(String relationName, MeshUI concept1Ui, MeshUI concept2Ui)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 常用关系类型常量。
  public static final String NRW = "NRW"; // Narrower（更窄）
  public static final String BRD = "BRD"; // Broader（更宽）
  public static final String REL = "REL"; // Related（相关）

  /// 工厂方法。
  ///
  /// @param relationName 关系类型
  /// @param concept1Ui 概念1 UI
  /// @param concept2Ui 概念2 UI
  /// @return 概念关系值对象
  public static ConceptRelation of(String relationName, MeshUI concept1Ui, MeshUI concept2Ui) {
    // 参数验证
    Assert.notBlank(relationName, "关系类型不能为空");
    Assert.notNull(concept1Ui, "概念1 UI不能为空");
    Assert.notNull(concept2Ui, "概念2 UI不能为空");
    Assert.isTrue(concept1Ui.isConcept(), "概念1 UI必须是概念类型(M开头)：%s", concept1Ui.ui());
    Assert.isTrue(concept2Ui.isConcept(), "概念2 UI必须是概念类型(M开头)：%s", concept2Ui.ui());
    Assert.isFalse(concept1Ui.equals(concept2Ui), "概念1和概念2不能相同");

    return new ConceptRelation(relationName, concept1Ui, concept2Ui);
  }

  /// 工厂方法（允许 relationName 为 null）。
  ///
  /// DTD 定义 RelationName 为 #IMPLIED（可选），部分概念关系可能没有指定关系类型。
  ///
  /// @param concept1Ui 概念1 UI（首选概念）
  /// @param concept2Ui 概念2 UI（关联概念）
  /// @param relationName 关系类型（可为 null）
  /// @return 概念关系值对象
  public static ConceptRelation ofNullable(MeshUI concept1Ui, MeshUI concept2Ui, String relationName) {
    Assert.notNull(concept1Ui, "概念1 UI不能为空");
    Assert.notNull(concept2Ui, "概念2 UI不能为空");
    Assert.isTrue(concept1Ui.isConcept(), "概念1 UI必须是概念类型(M开头)：%s", concept1Ui.ui());
    Assert.isTrue(concept2Ui.isConcept(), "概念2 UI必须是概念类型(M开头)：%s", concept2Ui.ui());
    Assert.isFalse(concept1Ui.equals(concept2Ui), "概念1和概念2不能相同");

    return new ConceptRelation(relationName, concept1Ui, concept2Ui);
  }
}
