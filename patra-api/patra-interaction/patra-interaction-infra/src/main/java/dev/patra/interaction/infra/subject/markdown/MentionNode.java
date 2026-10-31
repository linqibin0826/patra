package dev.patra.interaction.infra.subject.markdown;

import org.commonmark.node.CustomNode;

/// 语法树里的 `<@数字>`：门户会当成提及的写法（不在行内代码里、没被转义）。是不是真提及还要看名单。
final class MentionNode extends CustomNode {

  /// 数字原文（首位不是 0，长度不限）。
  private final String digits;

  /// 创建。
  ///
  /// @param digits 数字原文
  MentionNode(String digits) {
    this.digits = digits;
  }

  /// 数字原文。
  ///
  /// @return 数字
  String digits() {
    return digits;
  }
}
