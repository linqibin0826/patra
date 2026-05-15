package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/// MeSH 标引值对象。
///
/// 封装文献的 MeSH 主题词标引信息，包含描述符和可选的限定词。
///
/// **业务含义**：
///
/// MeSH（Medical Subject Headings）是 NLM 维护的医学主题词表，
/// 用于对 PubMed/MEDLINE 文献进行主题标引，便于检索和分类。
///
/// 每个标引包含：
/// - **描述符（Descriptor）**：主题词本身（如 "Diabetes Mellitus"）
/// - **限定词（Qualifier）**：可选的细化修饰（如 "drug therapy"）
///
/// **MajorTopic 标记**：
///
/// 当 `majorTopic=true` 时，表示该主题词是文献的主要讨论内容，
/// 在 PubMed 检索中用星号标记（如 "Diabetes Mellitus/*drug therapy"）。
///
/// **与聚合根的关系**：
///
/// MeSH 标引是文献的补充数据（聚合边界外），通过 Repository 批量管理：
/// - 写入：`PublicationRepository.insertAllWithAssociations()`
/// - 不在 PublicationAggregate 内直接维护
///
/// @param descriptorUi 描述符 UI（如 "D003920"）
/// @param majorTopic 是否为主要主题
/// @param headingOrder 标引顺序（保留 XML 中的顺序）
/// @param qualifiers 限定词列表（不可变）
/// @author linqibin
/// @since 0.1.0
public record PublicationMeshHeading(
    String descriptorUi, boolean majorTopic, Integer headingOrder, List<MeshQualifier> qualifiers)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证描述符 UI 并进行防御性拷贝。
  public PublicationMeshHeading {
    Assert.isTrue(StrUtil.isNotBlank(descriptorUi), "描述符 UI 不能为空");
    qualifiers = qualifiers != null ? List.copyOf(qualifiers) : List.of();
  }

  /// 创建 MeSH 标引值对象。
  ///
  /// @param descriptorUi 描述符 UI
  /// @param majorTopic 是否为主要主题
  /// @param headingOrder 标引顺序
  /// @param qualifiers 限定词列表
  /// @return MeSH 标引值对象
  public static PublicationMeshHeading of(
      String descriptorUi,
      boolean majorTopic,
      Integer headingOrder,
      List<MeshQualifier> qualifiers) {
    return new PublicationMeshHeading(descriptorUi, majorTopic, headingOrder, qualifiers);
  }

  /// 创建无限定词的 MeSH 标引。
  ///
  /// @param descriptorUi 描述符 UI
  /// @param majorTopic 是否为主要主题
  /// @param headingOrder 标引顺序
  /// @return MeSH 标引值对象
  public static PublicationMeshHeading ofDescriptorOnly(
      String descriptorUi, boolean majorTopic, Integer headingOrder) {
    return new PublicationMeshHeading(descriptorUi, majorTopic, headingOrder, List.of());
  }

  /// 是否有限定词。
  ///
  /// @return true 如果有限定词
  public boolean hasQualifiers() {
    return !qualifiers.isEmpty();
  }

  /// 获取限定词数量。
  ///
  /// @return 限定词数量
  public int getQualifierCount() {
    return qualifiers.size();
  }
}
