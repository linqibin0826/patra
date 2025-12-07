package com.patra.catalog.domain.model.dto.serfile;

/// Serfile 通用备注解析结果。
///
/// 从 Serfile XML 的 `GeneralNote` 元素解析出的数据传输对象。
/// 表示期刊的通用备注信息。
///
/// **XML 结构示例**：
///
/// ```xml
/// <GeneralNote NoteType="LinkComplexNote">Sample note content</GeneralNote>
/// <GeneralNote>Another note without type</GeneralNote>
/// ```
///
/// @param noteType 备注类型（如 LinkComplexNote，可为 null）
/// @param content 备注内容
/// @author linqibin
/// @since 0.1.0
public record SerialGeneralNote(String noteType, String content) {

  /// 创建通用备注记录（无类型）。
  ///
  /// @param content 备注内容
  /// @return 通用备注解析结果
  public static SerialGeneralNote of(String content) {
    return new SerialGeneralNote(null, content);
  }

  /// 创建通用备注记录（带类型）。
  ///
  /// @param noteType 备注类型
  /// @param content 备注内容
  /// @return 通用备注解析结果
  public static SerialGeneralNote of(String noteType, String content) {
    return new SerialGeneralNote(noteType, content);
  }

  /// 判断是否有备注类型。
  ///
  /// @return true 如果有备注类型
  public boolean hasNoteType() {
    return noteType != null && !noteType.isBlank();
  }

  /// 判断是否为链接复杂备注。
  ///
  /// @return true 如果是链接复杂备注
  public boolean isLinkComplexNote() {
    return "LinkComplexNote".equalsIgnoreCase(noteType);
  }
}
