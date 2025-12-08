package com.patra.catalog.infra.adapter.parser.dto.serfile;

/// Serfile 期刊语言解析结果。
///
/// 从 Serfile XML 的 `Language` 元素解析出的数据传输对象。
/// 包含语言代码和语言类型（主语言或摘要语言）。
///
/// **XML 结构示例**：
///
/// ```xml
/// <Language LangType="Primary">eng</Language>
/// <Language LangType="Summary">fre</Language>
/// ```
///
/// **语言类型说明**：
///
/// | LangType | 含义 |
/// |----------|------|
/// | Primary | 期刊主要语言 |
/// | Summary | 摘要语言 |
///
/// @param code 语言代码（ISO 639-3，如 eng, fre, ger）
/// @param langType 语言类型（Primary/Summary）
/// @author linqibin
/// @since 0.1.0
public record SerialLanguage(String code, String langType) {

  /// 创建语言记录（无类型）。
  ///
  /// @param code 语言代码
  /// @return 语言解析结果
  public static SerialLanguage of(String code) {
    return new SerialLanguage(code, null);
  }

  /// 创建语言记录（带类型）。
  ///
  /// @param code 语言代码
  /// @param langType 语言类型
  /// @return 语言解析结果
  public static SerialLanguage of(String code, String langType) {
    return new SerialLanguage(code, langType);
  }

  /// 判断是否为主语言。
  ///
  /// @return true 如果是主语言
  public boolean isPrimary() {
    return "Primary".equalsIgnoreCase(langType);
  }

  /// 判断是否为摘要语言。
  ///
  /// @return true 如果是摘要语言
  public boolean isSummary() {
    return "Summary".equalsIgnoreCase(langType);
  }
}
