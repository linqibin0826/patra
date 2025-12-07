package com.patra.catalog.domain.model.dto.serfile;

/// Serfile 记录 ID 解析结果。
///
/// 从 Serfile XML 的 `RecordID` 元素解析出的数据传输对象。
/// 表示关联记录的标识符，用于 `TitleRelated` 元素内。
///
/// **XML 结构示例**：
///
/// ```xml
/// <TitleRelated TitleType="Preceding">
///   <Title>Previous Journal Name</Title>
///   <RecordID Source="NLM">12345678</RecordID>
///   <RecordID Source="OCLC">98765432</RecordID>
///   <ISSN IssnType="Print">1234-5678</ISSN>
/// </TitleRelated>
/// ```
///
/// **来源说明**：
///
/// | Source | 含义 |
/// |--------|------|
/// | NLM | NLM Catalog 记录 ID |
/// | LC | Library of Congress 记录 ID |
/// | OCLC | OCLC WorldCat 记录 ID |
///
/// @param id 记录 ID 值
/// @param source 记录来源（NLM/LC/OCLC）
/// @author linqibin
/// @since 0.1.0
public record SerialRecordId(String id, String source) {

  /// 创建记录 ID。
  ///
  /// @param id 记录 ID 值
  /// @param source 记录来源
  /// @return 记录 ID 解析结果
  public static SerialRecordId of(String id, String source) {
    return new SerialRecordId(id, source);
  }

  /// 判断是否来自 NLM。
  ///
  /// @return true 如果来自 NLM
  public boolean isNlm() {
    return "NLM".equalsIgnoreCase(source);
  }

  /// 判断是否来自 Library of Congress。
  ///
  /// @return true 如果来自 LC
  public boolean isLc() {
    return "LC".equalsIgnoreCase(source);
  }

  /// 判断是否来自 OCLC。
  ///
  /// @return true 如果来自 OCLC
  public boolean isOclc() {
    return "OCLC".equalsIgnoreCase(source);
  }
}
