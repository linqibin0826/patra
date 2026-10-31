package dev.patra.notification.domain.mail.model.vo.content;

import dev.patra.notification.domain.kernel.model.read.SegmentedExcerpt.Segment;
import java.util.List;
import lombok.Builder;

/// 邮件里的一条提醒（spec 8.2～8.4）。
///
/// @param actorName 触发人的名字（显示名、`匿名作者` 或 `已注销用户`）
/// @param action 动作（`回复了你` 等）
/// @param time 时间文字
/// @param excerpt 摘录，文字段与提及段；截掉了内容时最后一段是 `…`
/// @param place 在哪
/// @param link 「查看并回复」去的评论永久链接
@Builder
public record ReminderItem(
    String actorName,
    String action,
    String time,
    List<Segment> excerpt,
    ReminderPlace place,
    String link) {

  /// 防御性拷贝。
  public ReminderItem {
    excerpt = List.copyOf(excerpt);
  }
}
