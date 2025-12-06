package com.patra.catalog.domain.model.dto.serfile;

/// Serfile 期刊 MeSH 主题词解析结果。
///
/// 从 Serfile XML 的 `MeshHeading` 元素解析出的数据传输对象。
/// 用于在解析层和领域层之间传递数据，不是领域实体。
///
/// **XML 结构示例**：
///
/// ```xml
/// <MeshHeading>
///   <DescriptorName MajorTopicYN="Y">Cardiology</DescriptorName>
///   <QualifierName MajorTopicYN="N">methods</QualifierName>
/// </MeshHeading>
/// ```
///
/// @param descriptorName 描述符名称（必填）
/// @param isMajorTopic 是否主要主题
/// @param qualifierName 限定符名称（可选）
/// @param qualifierIsMajor 限定符是否主要主题
/// @author linqibin
/// @since 0.1.0
public record SerialMeshHeading(
    String descriptorName, boolean isMajorTopic, String qualifierName, boolean qualifierIsMajor) {

  /// 创建无限定符的 MeSH 主题词。
  ///
  /// @param descriptorName 描述符名称
  /// @param isMajorTopic 是否主要主题
  /// @return MeSH 主题词解析结果
  public static SerialMeshHeading of(String descriptorName, boolean isMajorTopic) {
    return new SerialMeshHeading(descriptorName, isMajorTopic, null, false);
  }

  /// 创建带限定符的 MeSH 主题词。
  ///
  /// @param descriptorName 描述符名称
  /// @param isMajorTopic 描述符是否主要主题
  /// @param qualifierName 限定符名称
  /// @param qualifierIsMajor 限定符是否主要主题
  /// @return MeSH 主题词解析结果
  public static SerialMeshHeading of(
      String descriptorName, boolean isMajorTopic, String qualifierName, boolean qualifierIsMajor) {
    return new SerialMeshHeading(descriptorName, isMajorTopic, qualifierName, qualifierIsMajor);
  }

  /// 判断是否有限定符。
  ///
  /// @return true 如果有限定符
  public boolean hasQualifier() {
    return qualifierName != null && !qualifierName.isBlank();
  }
}
