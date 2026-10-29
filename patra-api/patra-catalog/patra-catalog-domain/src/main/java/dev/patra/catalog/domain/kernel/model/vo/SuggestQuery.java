package dev.patra.catalog.domain.kernel.model.vo;

import cn.hutool.core.lang.Assert;
import java.lang.Character.UnicodeScript;
import java.text.Normalizer;
import java.util.Set;

/// 已归一化、已验证的建议查询（机构建议与 MeSH 建议共用）。
///
/// 外部输入经 [#of] 归一：NFKC（全角字母、数字、空格变半角）→ 去首尾空白 → 连续空白折成一个空格；
/// 归一后按码点计长，只收 [#MIN_LENGTH] ~ [#MAX_LENGTH] 个字符。匹配模式按文字判定：含汉字、
/// 假名、谚文任一字符走 [MatchMode#SUBSTRING]，否则走 [MatchMode#WORD_PREFIX]。
///
/// @param text 归一化后的查询文本
/// @param mode 匹配模式
/// @param limit 最多返回条数（[#MIN_LIMIT] ~ [#MAX_LIMIT]）
/// @author linqibin
/// @since 0.1.0
public record SuggestQuery(String text, MatchMode mode, int limit) {

  /// 归一化后的最短长度（码点）。
  public static final int MIN_LENGTH = 2;

  /// 归一化后的最长长度（码点）。
  public static final int MAX_LENGTH = 100;

  /// 最少返回条数。
  public static final int MIN_LIMIT = 1;

  /// 最多返回条数。
  public static final int MAX_LIMIT = 20;

  /// 调用方没给 limit 时的缺省值。
  public static final int DEFAULT_LIMIT = 10;

  /// 出现这些文字就走子串模式。
  private static final Set<UnicodeScript> SUBSTRING_SCRIPTS =
      Set.of(
          UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HANGUL);

  /// 紧凑构造器：验证文本长度与 limit 范围。
  public SuggestQuery {
    Assert.notBlank(text, "查询文本不能为空");
    Assert.notNull(mode, "匹配模式不能为空");
    int length = text.codePointCount(0, text.length());
    Assert.isTrue(
        length >= MIN_LENGTH && length <= MAX_LENGTH, "查询文本须为 {} ~ {} 个字符", MIN_LENGTH, MAX_LENGTH);
    Assert.isTrue(
        limit >= MIN_LIMIT && limit <= MAX_LIMIT, "limit 须为 {} ~ {}", MIN_LIMIT, MAX_LIMIT);
  }

  /// 从外部输入创建查询：归一化文本并判定匹配模式。
  ///
  /// @param raw 原始查询文本
  /// @param limit 最多返回条数
  /// @return 已归一化、已验证的查询
  /// @throws IllegalArgumentException 文本为空、归一后长度不在范围内、limit 不在范围内
  public static SuggestQuery of(String raw, int limit) {
    Assert.notBlank(raw, "查询文本不能为空");
    String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
    return new SuggestQuery(text, detectMode(text), limit);
  }

  /// 按文字判定匹配模式。
  ///
  /// @param text 归一化后的文本
  /// @return 含中日韩文字为 [MatchMode#SUBSTRING]，否则 [MatchMode#WORD_PREFIX]
  private static MatchMode detectMode(String text) {
    boolean cjk =
        text.codePoints().mapToObj(UnicodeScript::of).anyMatch(SUBSTRING_SCRIPTS::contains);
    return cjk ? MatchMode.SUBSTRING : MatchMode.WORD_PREFIX;
  }
}
