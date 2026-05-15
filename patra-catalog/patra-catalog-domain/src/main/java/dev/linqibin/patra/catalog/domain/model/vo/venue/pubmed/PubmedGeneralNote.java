package dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 通用备注。
///
/// 表示期刊的通用备注信息。
///
/// @param noteType 备注类型（可为 null）
/// @param content 备注内容
/// @author linqibin
/// @since 0.1.0
public record PubmedGeneralNote(String noteType, String content) {

  /// 创建通用备注（无类型）。
  ///
  /// @param content 备注内容
  /// @return 通用备注值对象
  public static PubmedGeneralNote of(String content) {
    return new PubmedGeneralNote(null, content);
  }

  /// 创建通用备注（带类型）。
  ///
  /// @param noteType 备注类型
  /// @param content 备注内容
  /// @return 通用备注值对象
  public static PubmedGeneralNote of(String noteType, String content) {
    return new PubmedGeneralNote(noteType, content);
  }

  /// 判断是否有备注类型。
  public boolean hasNoteType() {
    return noteType != null && !noteType.isBlank();
  }

  /// 判断是否为链接复杂备注。
  public boolean isLinkComplexNote() {
    return "LinkComplexNote".equalsIgnoreCase(noteType);
  }
}
