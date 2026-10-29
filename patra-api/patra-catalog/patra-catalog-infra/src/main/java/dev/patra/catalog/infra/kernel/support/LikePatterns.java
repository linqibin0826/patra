package dev.patra.catalog.infra.kernel.support;

/// 建议查询的 LIKE 模式：转义符 `!`，与 SQL 里的 `ESCAPE '!'` 配套。
///
/// @author linqibin
/// @since 0.1.0
public final class LikePatterns {

  private LikePatterns() {}

  /// 整串前缀：`q%`。
  ///
  /// @param text 原始查询文本
  /// @return 转义后的前缀模式
  public static String prefix(String text) {
    return escape(text) + "%";
  }

  /// 某个词的开头：`% q%`。
  ///
  /// @param text 原始查询文本
  /// @return 转义后的词首模式
  public static String wordStart(String text) {
    return "% " + escape(text) + "%";
  }

  /// 任意位置子串：`%q%`。
  ///
  /// @param text 原始查询文本
  /// @return 转义后的子串模式
  public static String contains(String text) {
    return "%" + escape(text) + "%";
  }

  /// 把 `!`、`%`、`_` 转义成字面量。
  ///
  /// @param text 原始查询文本
  /// @return 转义后的文本
  public static String escape(String text) {
    return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
