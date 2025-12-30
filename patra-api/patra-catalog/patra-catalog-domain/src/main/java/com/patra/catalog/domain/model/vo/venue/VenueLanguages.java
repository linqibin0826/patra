package com.patra.catalog.domain.model.vo.venue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// 期刊语言信息值对象。封装期刊的主语言和摘要语言列表。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 来源：主要来自 NLM Serfile 的 Language 元素（经过标准化转换）
/// - JSON 存储：在数据库中以 JSON 格式存储
///
/// **语言代码标准**：使用 BCP 47 格式（优先采用 ISO 639-1 两字母代码）
///
/// **标准化流程**：
///
/// - 原始数据：PubMed 使用 ISO 639-3 三字母代码（如 eng, chi, jpn）
/// - 存储格式：转换为 BCP 47 标准（如 en, zh, ja）
/// - 转换方式：通过 Registry 服务的语言字典解析
///
/// | BCP 47 | 语言 | ISO 639-3 原始代码 |
/// |--------|------|-------------------|
/// | en | 英语 | eng |
/// | zh | 中文 | chi, zho |
/// | fr | 法语 | fre, fra |
/// | de | 德语 | ger, deu |
/// | ja | 日语 | jpn |
/// | es | 西班牙语 | spa |
/// | pt | 葡萄牙语 | por |
/// | ru | 俄语 | rus |
///
/// JSON 结构示例：
///
/// ```json
/// {
///   "primary": ["en"],
///   "summary": ["fr", "de"]
/// }
/// ```
///
/// 使用示例：
///
/// ```java
/// // 创建英语期刊，带法语摘要
/// VenueLanguages languages = VenueLanguages.of(
///     List.of("en"),
///     List.of("fr")
/// );
///
/// // 检查主语言
/// if (languages.hasPrimaryLanguage("en")) {
///     // 英语期刊
/// }
///
/// // 获取主要语言
/// String mainLang = languages.getMainLanguage(); // "en"
/// ```
///
/// @param primary 主语言列表（期刊内容的语言，BCP 47 格式）
/// @param summary 摘要语言列表（摘要可用的语言，BCP 47 格式）
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueLanguages(List<String> primary, List<String> summary) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 规范化构造函数，确保列表不可变。
  public VenueLanguages {
    primary = primary != null ? List.copyOf(primary) : List.of();
    summary = summary != null ? List.copyOf(summary) : List.of();
  }

  /// 创建语言信息。
  ///
  /// @param primary 主语言列表
  /// @param summary 摘要语言列表
  /// @return 语言信息值对象
  public static VenueLanguages of(List<String> primary, List<String> summary) {
    return new VenueLanguages(primary, summary);
  }

  /// 创建仅包含主语言的语言信息。
  ///
  /// @param primary 主语言列表
  /// @return 语言信息值对象
  public static VenueLanguages ofPrimary(List<String> primary) {
    return new VenueLanguages(primary, List.of());
  }

  /// 创建单一主语言的语言信息。
  ///
  /// @param languageCode 主语言代码
  /// @return 语言信息值对象
  public static VenueLanguages ofSingleLanguage(String languageCode) {
    return new VenueLanguages(List.of(languageCode), List.of());
  }

  /// 创建空的语言信息。
  ///
  /// @return 空的语言信息值对象
  public static VenueLanguages empty() {
    return new VenueLanguages(List.of(), List.of());
  }

  /// 获取主要语言（第一个主语言）。
  ///
  /// @return 主要语言代码，如果没有则返回 null
  @JsonIgnore
  public String getMainLanguage() {
    return primary.isEmpty() ? null : primary.get(0);
  }

  /// 判断是否包含指定的主语言。
  ///
  /// @param languageCode 语言代码
  /// @return true 如果主语言列表包含该语言
  public boolean hasPrimaryLanguage(String languageCode) {
    return primary.contains(languageCode);
  }

  /// 判断是否包含指定的摘要语言。
  ///
  /// @param languageCode 语言代码
  /// @return true 如果摘要语言列表包含该语言
  public boolean hasSummaryLanguage(String languageCode) {
    return summary.contains(languageCode);
  }

  /// 判断是否有主语言。
  ///
  /// @return true 如果有主语言
  public boolean hasPrimaryLanguages() {
    return !primary.isEmpty();
  }

  /// 判断是否有摘要语言。
  ///
  /// @return true 如果有摘要语言
  public boolean hasSummaryLanguages() {
    return !summary.isEmpty();
  }

  /// 判断是否为空（无任何语言信息）。
  ///
  /// @return true 如果主语言和摘要语言都为空
  @JsonIgnore
  public boolean isEmpty() {
    return primary.isEmpty() && summary.isEmpty();
  }

  /// 判断是否为英语期刊（主语言为英语）。
  ///
  /// @return true 如果主语言包含英语
  @JsonIgnore
  public boolean isEnglish() {
    return hasPrimaryLanguage("en");
  }

  /// 判断是否为中文期刊（主语言为中文）。
  ///
  /// @return true 如果主语言包含中文
  @JsonIgnore
  public boolean isChinese() {
    return hasPrimaryLanguage("zh");
  }

  /// 获取所有语言代码（去重）。
  ///
  /// @return 所有语言代码列表
  @JsonIgnore
  public List<String> getAllLanguages() {
    List<String> all = new ArrayList<>(primary);
    for (String lang : summary) {
      if (!all.contains(lang)) {
        all.add(lang);
      }
    }
    return Collections.unmodifiableList(all);
  }

  /// 添加主语言（返回新对象）。
  ///
  /// @param languageCode 语言代码
  /// @return 新的语言信息对象
  public VenueLanguages withPrimaryLanguage(String languageCode) {
    if (primary.contains(languageCode)) {
      return this;
    }
    List<String> newPrimary = new ArrayList<>(primary);
    newPrimary.add(languageCode);
    return new VenueLanguages(newPrimary, summary);
  }

  /// 添加摘要语言（返回新对象）。
  ///
  /// @param languageCode 语言代码
  /// @return 新的语言信息对象
  public VenueLanguages withSummaryLanguage(String languageCode) {
    if (summary.contains(languageCode)) {
      return this;
    }
    List<String> newSummary = new ArrayList<>(summary);
    newSummary.add(languageCode);
    return new VenueLanguages(primary, newSummary);
  }
}
