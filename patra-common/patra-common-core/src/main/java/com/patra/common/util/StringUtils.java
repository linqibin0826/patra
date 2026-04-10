package com.patra.common.util;

/// 字符串工具类，提供通用的字符串处理方法。
///
/// @author linqibin
/// @since 0.1.0
public final class StringUtils {

  /// SQL `LIKE` 表达式的转义字符。
  ///
  /// 使用 `!` 而非 `\`：`\` 在 MySQL 中同时是字符串默认转义和 LIKE 转义，
  /// 会产生"到底要转义几次"的歧义。使用非反斜杠字符则语义清晰，
  /// 只需要在 DAO 查询里配套写 `ESCAPE '!'` 子句。
  private static final char LIKE_ESCAPE_CHAR = '!';

  private StringUtils() {}

  /// 将空白字符串归一化为 null。
  ///
  /// 去除首尾空白后，若结果为空则返回 null，否则返回 trim 后的字符串。
  ///
  /// @param value 原始字符串
  /// @return 去除首尾空白后的字符串，空白时返回 null
  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /// 转义 SQL `LIKE` 表达式中的通配符，防止用户输入被当作通配符解释。
  ///
  /// 转义规则：
  ///
  /// - `%`（匹配任意字符序列）→ `!%`
  /// - `_`（匹配任一字符）→ `!_`
  /// - `!`（转义字符本身）→ `!!`（必须优先于上述两条执行，避免二次转义）
  ///
  /// **配套要求**：DAO 查询必须在 LIKE 子句后附加 `ESCAPE '!'`，否则 MySQL 仍会
  /// 把 `!` 当字面字符而非转义符。例如：
  ///
  /// ```sql
  /// WHERE title LIKE CONCAT(:keyword, '%') ESCAPE '!'
  /// ```
  ///
  /// @param value 用户原始输入
  /// @return 转义后可安全拼接到 LIKE 表达式的字符串；null 输入返回 null
  public static String escapeLike(String value) {
    if (value == null) {
      return null;
    }
    if (value.isEmpty()) {
      return value;
    }
    StringBuilder sb = new StringBuilder(value.length() + 4);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == LIKE_ESCAPE_CHAR || c == '%' || c == '_') {
        sb.append(LIKE_ESCAPE_CHAR);
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
