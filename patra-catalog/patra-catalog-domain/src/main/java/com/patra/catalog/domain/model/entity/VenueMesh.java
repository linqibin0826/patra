package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/// 期刊 MeSH 主题词值对象（不可变）。
///
/// **设计说明**：
///
/// - 作为值对象存在（不是实体）
/// - 使用 Record 实现不可变性
/// - 通过 `VenueRepository` 统一管理
/// - 存储期刊的 MeSH 主题词分类
/// - 数据主要来源于 NLM Serfile 的 MeshHeadingList
///
/// **MeSH 主题词说明**：
///
/// - **Descriptor（描述符）**：MeSH 主题词的主体，如 "Medicine"、"Cardiology"
/// - **Qualifier（限定符）**：可选的限定符，如 "methods"、"diagnosis"
/// - **Major Topic**：标记为主要主题的词条会在检索时被优先考虑
///
/// **示例**：
///
/// ```java
/// // 创建主要主题
/// VenueMesh mesh = VenueMesh.create("Medicine", "D008511", true);
///
/// // 创建带限定符的主题
/// VenueMesh meshWithQualifier = VenueMesh.create(
///     "Cardiology", "D002309", false, "methods", "Q000379"
/// );
/// ```
///
/// @param descriptorName MeSH 描述符名称（必填）
/// @param descriptorUi MeSH 描述符唯一标识符（格式：D000001，可选）
/// @param isMajorTopic 是否主要主题
/// @param qualifierName MeSH 限定符名称（可选）
/// @param qualifierUi MeSH 限定符唯一标识符（格式：Q000001，可选）
/// @author linqibin
/// @since 0.1.0
@SuppressWarnings("java:S6218") // 自定义 equals/hashCode 基于业务语义（描述符 UI 或名称）
public record VenueMesh(
    String descriptorName,
    String descriptorUi,
    boolean isMajorTopic,
    String qualifierName,
    String qualifierUi)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public VenueMesh {
    Assert.notBlank(descriptorName, "描述符名称不能为空");
  }

  // ========== 工厂方法 ==========

  /// 创建主题。
  ///
  /// @param descriptorName MeSH 描述符名称
  /// @param descriptorUi MeSH 描述符唯一标识符
  /// @param isMajorTopic 是否主要主题
  /// @return 主题值对象
  public static VenueMesh create(String descriptorName, String descriptorUi, boolean isMajorTopic) {
    return new VenueMesh(descriptorName, descriptorUi, isMajorTopic, null, null);
  }

  /// 创建带限定符的主题（仅限定符名称）。
  ///
  /// @param descriptorName MeSH 描述符名称
  /// @param descriptorUi MeSH 描述符唯一标识符
  /// @param isMajorTopic 是否主要主题
  /// @param qualifierName 限定符名称
  /// @return 主题值对象
  public static VenueMesh create(
      String descriptorName, String descriptorUi, boolean isMajorTopic, String qualifierName) {
    return new VenueMesh(descriptorName, descriptorUi, isMajorTopic, qualifierName, null);
  }

  /// 创建带限定符的主题（完整信息）。
  ///
  /// @param descriptorName MeSH 描述符名称
  /// @param descriptorUi MeSH 描述符唯一标识符
  /// @param isMajorTopic 是否主要主题
  /// @param qualifierName 限定符名称
  /// @param qualifierUi 限定符唯一标识符
  /// @return 主题值对象
  public static VenueMesh create(
      String descriptorName,
      String descriptorUi,
      boolean isMajorTopic,
      String qualifierName,
      String qualifierUi) {
    return new VenueMesh(descriptorName, descriptorUi, isMajorTopic, qualifierName, qualifierUi);
  }

  /// 创建主要主题。
  ///
  /// @param descriptorName MeSH 描述符名称
  /// @param descriptorUi MeSH 描述符唯一标识符
  /// @return 主题值对象
  public static VenueMesh major(String descriptorName, String descriptorUi) {
    return create(descriptorName, descriptorUi, true);
  }

  /// 创建次要主题。
  ///
  /// @param descriptorName MeSH 描述符名称
  /// @param descriptorUi MeSH 描述符唯一标识符
  /// @return 主题值对象
  public static VenueMesh minor(String descriptorName, String descriptorUi) {
    return create(descriptorName, descriptorUi, false);
  }

  // ========== 查询方法 ==========

  /// 判断是否有 MeSH 描述符 UI。
  ///
  /// @return true 如果有描述符 UI
  public boolean hasDescriptorUi() {
    return descriptorUi != null && !descriptorUi.isBlank();
  }

  /// 判断是否有限定符。
  ///
  /// @return true 如果有限定符
  public boolean hasQualifier() {
    return qualifierName != null && !qualifierName.isBlank();
  }

  /// 判断是否有限定符 UI。
  ///
  /// @return true 如果有限定符 UI
  public boolean hasQualifierUi() {
    return qualifierUi != null && !qualifierUi.isBlank();
  }

  /// 获取完整主题描述（描述符 + 限定符）。
  ///
  /// @return 完整描述，如 "Cardiology/methods"
  public String getFullDescription() {
    if (hasQualifier()) {
      return descriptorName + "/" + qualifierName;
    }
    return descriptorName;
  }

  @Override
  public String toString() {
    return String.format(
        "VenueMesh[name=%s, ui=%s, major=%b]", descriptorName, descriptorUi, isMajorTopic);
  }

  /// 业务相等性：描述符 UI（如果有）或描述符名称。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueMesh that)) {
      return false;
    }
    if (hasDescriptorUi() && that.hasDescriptorUi()) {
      return Objects.equals(descriptorUi, that.descriptorUi);
    }
    return Objects.equals(descriptorName, that.descriptorName);
  }

  @Override
  public int hashCode() {
    return hasDescriptorUi() ? Objects.hash(descriptorUi) : Objects.hash(descriptorName);
  }
}
