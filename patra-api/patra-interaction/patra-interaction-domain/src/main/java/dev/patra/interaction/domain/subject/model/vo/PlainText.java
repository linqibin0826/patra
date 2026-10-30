package dev.patra.interaction.domain.subject.model.vo;

import java.util.Objects;
import java.util.regex.Pattern;

/// 正文转成的纯文本（PAP-128）：读者在门户上看到的那些字，去掉样式，结构只留换行。
///
/// 约定（spec 第 4 节）：
///
/// - 每个 `<@数字>` 都是真提及，消费方用 {@link #MENTION} 找出来、按 ID 现查名字，不用名单、不用反转义；
/// - 长得像提及的普通文字在 `<` 与 `@` 之间插了 {@link #WORD_JOINER}（看不见），匹配不上 {@link #MENTION}；
/// - 行之间用 `\n`，没有空行，行首尾没有空白。
///
/// 文字里可能有字面的 `<b>` 之类，放进 HTML 时要转义。
///
/// @param value 纯文本
public record PlainText(String value) {

  /// 提及：`<@` + 规范十进制正整数（最多 19 位）+ `>`。
  public static final Pattern MENTION = Pattern.compile("<@([1-9][0-9]{0,18})>");

  /// 断开同形文字用的零宽连接符（U+2060 WORD JOINER）。
  public static final char WORD_JOINER = '⁠';

  /// 校验。
  public PlainText {
    Objects.requireNonNull(value, "value");
  }

  /// 创建。
  ///
  /// @param value 纯文本
  /// @return 纯文本
  public static PlainText of(String value) {
    return new PlainText(value);
  }
}
