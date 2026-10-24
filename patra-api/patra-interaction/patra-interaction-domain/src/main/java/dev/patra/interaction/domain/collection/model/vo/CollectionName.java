package dev.patra.interaction.domain.collection.model.vo;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionFieldsException;
import dev.patra.interaction.domain.collection.policy.TextRules;

/// 收藏夹名称：去首尾空白后 1 ~ 100 字素、UTF-8 ≤ 400 字节，单行。
///
/// @param value 规范化后的名称
public record CollectionName(String value) {

  /// 字段名。
  public static final String FIELD = "name";

  /// 字素上限。
  public static final int MAX_GRAPHEMES = 100;

  /// 解析用户输入。
  ///
  /// @param raw 原文
  /// @return 名称
  /// @throws InvalidCollectionFieldsException 不合法
  public static CollectionName parse(String raw) {
    String text = TextRules.normalize(raw);
    if (text.isEmpty()) {
      throw TextRules.violation(FIELD, TextRules.REQUIRED, "请输入名称");
    }
    TextRules.check(FIELD, text, MAX_GRAPHEMES, false);
    return new CollectionName(text);
  }
}
