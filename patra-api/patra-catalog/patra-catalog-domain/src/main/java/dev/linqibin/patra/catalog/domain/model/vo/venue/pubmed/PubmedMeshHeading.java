package dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 期刊 MeSH 主题词。
///
/// 表示从 NLM Serfile 解析出的 MeSH 主题词数据。
///
/// **XML 结构示例**：
///
/// ```xml
/// <MeshHeading>
///   <DescriptorName MajorTopicYN="Y" UI="D002309" Type="Geographic">Cardiology</DescriptorName>
///   <QualifierName MajorTopicYN="N">methods</QualifierName>
/// </MeshHeading>
/// ```
///
/// **属性说明**：
///
/// | 属性 | 说明 |
/// |------|------|
/// | UI | 描述符唯一标识符（如 D002309） |
/// | Type | 描述符类型（Geographic 表示地理描述符） |
/// | MajorTopicYN | 是否主要主题（Y/N） |
///
/// @param descriptorName 主题词描述符名称
/// @param descriptorUi 描述符唯一标识符（可选）
/// @param descriptorType 描述符类型（可选，如 Geographic）
/// @param isMajorTopic 描述符是否为主要主题
/// @param qualifierName 限定词名称（可选）
/// @param qualifierIsMajor 限定词是否为主要主题
/// @author linqibin
/// @since 0.1.0
public record PubmedMeshHeading(
    String descriptorName,
    String descriptorUi,
    String descriptorType,
    boolean isMajorTopic,
    String qualifierName,
    boolean qualifierIsMajor) {

  /// 创建不带限定词的主题词（基本版）。
  ///
  /// @param descriptorName 描述符名称
  /// @param isMajorTopic 是否主要主题
  /// @return MeSH 主题词
  public static PubmedMeshHeading of(String descriptorName, boolean isMajorTopic) {
    return new PubmedMeshHeading(descriptorName, null, null, isMajorTopic, null, false);
  }

  /// 创建带限定词的主题词（基本版）。
  ///
  /// @param descriptorName 描述符名称
  /// @param isMajorTopic 描述符是否主要主题
  /// @param qualifierName 限定词名称
  /// @param qualifierIsMajor 限定词是否主要主题
  /// @return MeSH 主题词
  public static PubmedMeshHeading of(
      String descriptorName, boolean isMajorTopic, String qualifierName, boolean qualifierIsMajor) {
    return new PubmedMeshHeading(
        descriptorName, null, null, isMajorTopic, qualifierName, qualifierIsMajor);
  }

  /// 创建带限定词的主题词（兼容旧 API）。
  ///
  /// @param descriptorName 描述符名称
  /// @param qualifierName 限定词名称
  /// @param isMajorTopic 是否主要主题
  /// @return MeSH 主题词
  public static PubmedMeshHeading withQualifier(
      String descriptorName, String qualifierName, boolean isMajorTopic) {
    return new PubmedMeshHeading(descriptorName, null, null, isMajorTopic, qualifierName, false);
  }

  /// 创建完整的 MeSH 主题词。
  ///
  /// @param descriptorName 描述符名称
  /// @param descriptorUi 描述符唯一标识符
  /// @param descriptorType 描述符类型
  /// @param isMajorTopic 描述符是否主要主题
  /// @param qualifierName 限定符名称
  /// @param qualifierIsMajor 限定符是否主要主题
  /// @return MeSH 主题词
  public static PubmedMeshHeading ofFull(
      String descriptorName,
      String descriptorUi,
      String descriptorType,
      boolean isMajorTopic,
      String qualifierName,
      boolean qualifierIsMajor) {
    return new PubmedMeshHeading(
        descriptorName,
        descriptorUi,
        descriptorType,
        isMajorTopic,
        qualifierName,
        qualifierIsMajor);
  }

  /// 是否有限定词。
  public boolean hasQualifier() {
    return qualifierName != null && !qualifierName.isBlank();
  }

  /// 判断是否有描述符 UI。
  ///
  /// @return true 如果有 UI
  public boolean hasDescriptorUi() {
    return descriptorUi != null && !descriptorUi.isBlank();
  }

  /// 判断是否为地理描述符。
  ///
  /// @return true 如果是地理描述符
  public boolean isGeographic() {
    return "Geographic".equalsIgnoreCase(descriptorType);
  }
}
