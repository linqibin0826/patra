package dev.patra.interaction.domain.subject.port.markdown;

import dev.patra.interaction.domain.subject.model.vo.PlainText;
import java.util.Set;

/// 正文 Markdown 转纯文本（PAP-128）。评论与投稿经验的正文是同一种 Markdown 子集（门户只放行一部分语法），
/// 规则见 `docs/patra/specs/2026-10-30-plaintext-conversion-design.md`。领域层没有 Markdown 解析器，由基础设施实现。
public interface PlainTextPort {

  /// 转纯文本。门户会当成提及的 `<@ID>`（不在行内代码里、没被转义），且 ID 在名单里，才保留成提及；
  /// 其余同形文字被断开（见 {@link PlainText}）。
  ///
  /// @param markdown 正文（存储形式）
  /// @param mentionedUserIds 正文 @ 到的人：评论传事件里的名单；投稿经验不解析 @，传空集合
  /// @return 全文纯文本，不截断
  PlainText toPlainText(String markdown, Set<Long> mentionedUserIds);
}
