package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 期刊语言信息。
///
/// 表示从 NLM Serfile 解析出的语言数据。
///
/// @param code 语言代码（如 "eng", "chi"）
/// @param isPrimary 是否为主语言（Primary 类型）
/// @author linqibin
/// @since 0.1.0
public record PubmedLanguage(String code, boolean isPrimary) {

  /// 创建语言对象。
  ///
  /// @param code 语言代码
  /// @param isPrimary 是否为主语言
  /// @return 语言对象
  public static PubmedLanguage of(String code, boolean isPrimary) {
    return new PubmedLanguage(code, isPrimary);
  }

  /// 创建主语言。
  public static PubmedLanguage primary(String code) {
    return new PubmedLanguage(code, true);
  }

  /// 创建非主语言（如摘要语言）。
  public static PubmedLanguage secondary(String code) {
    return new PubmedLanguage(code, false);
  }
}
