package dev.patra.interaction.domain.notification.port.excerpt;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import java.util.Set;

/// 评论 Markdown 转提醒摘录的纯文本（spec 6.2 第 1 步）。领域层没有 Markdown 解析器，由基础设施实现。
public interface PlainTextPort {

  /// 转纯文本：去掉 Markdown 标记，链接取文字，块与换行之间用一个空格，连续空白合并；普通文字里的 `<@ID>` 且 ID
  /// 在名单里的才算提及，其余（代码里的、名单外的）是字面文字。
  ///
  /// @param markdown 评论正文（存储形式）
  /// @param mentionedUserIds 这条评论 @ 到的人（事件给的名单）
  /// @return 未截断的摘录
  ExcerptText toExcerpt(String markdown, Set<Long> mentionedUserIds);
}
