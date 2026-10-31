package dev.patra.notification.domain.mail.model.vo.content;

import java.util.List;
import lombok.Builder;

/// 拼好的一封提醒邮件：文案、条目、链接都已定好，模板只管排版（spec 8.4、8.5）。
///
/// @param layout 版式
/// @param subject 主题
/// @param preheader 收件箱列表里显示的预览文字
/// @param eyebrow 栏目小标
/// @param heading 标题
/// @param lead 引导句；单条邮件为空
/// @param single 单条邮件的那一条；其余为空
/// @param items 多条合一的条目；其余为空列表
/// @param sections 每日汇总的分节；其余为空列表
/// @param overflow `还有 N 条没有列出…`；没有时为空
/// @param buttonLabel 主按钮文字（不含 `→`）
/// @param buttonUrl 主按钮链接
/// @param why 卡片外那一句（不含后面的两个链接）
/// @param unsubscribeLabel 退订链接的文字
/// @param unsubscribeUrl 退订确认页
/// @param settingsUrl 通知设置页
/// @param oneClickUrl RFC 8058 一键退订地址
@Builder
public record ReminderMailContent(
    Layout layout,
    String subject,
    String preheader,
    String eyebrow,
    String heading,
    String lead,
    ReminderItem single,
    List<ReminderItem> items,
    List<DigestSection> sections,
    String overflow,
    String buttonLabel,
    String buttonUrl,
    String why,
    String unsubscribeLabel,
    String unsubscribeUrl,
    String settingsUrl,
    String oneClickUrl) {

  /// 防御性拷贝，列表为空时给空列表。
  public ReminderMailContent {
    items = items == null ? List.of() : List.copyOf(items);
    sections = sections == null ? List.of() : List.copyOf(sections);
  }

  /// 版式，对应三个模板。
  public enum Layout {

    /// 离站逐条，只有一条。
    SINGLE,

    /// 离站逐条，同一类到点的几条合成一封。
    BATCH,

    /// 每日汇总。
    DIGEST
  }
}
