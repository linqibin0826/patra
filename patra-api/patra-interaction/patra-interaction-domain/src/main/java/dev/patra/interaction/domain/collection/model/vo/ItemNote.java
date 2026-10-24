package dev.patra.interaction.domain.collection.model.vo;

import dev.patra.interaction.domain.collection.policy.TextRules;
import java.util.Optional;

/// 文献库条目的备注：纯文本，去首尾空白后 ≤ 500 字素、UTF-8 ≤ 2,000 字节，可多行；去完为空等于删除备注。
///
/// @param value 规范化后的备注
public record ItemNote(String value) {

  /// 字段名。
  public static final String FIELD = "note";

  /// 字素上限。
  public static final int MAX_GRAPHEMES = 500;

  /// 解析用户输入。
  ///
  /// @param raw 原文，可空
  /// @return 备注；空白时为空
  public static Optional<ItemNote> parse(String raw) {
    String text = TextRules.normalize(raw);
    if (text.isEmpty()) {
      return Optional.empty();
    }
    TextRules.check(FIELD, text, MAX_GRAPHEMES, true);
    return Optional.of(new ItemNote(text));
  }
}
