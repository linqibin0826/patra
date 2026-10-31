package dev.patra.notification.domain.mail.model.vo.content;

/// 一条提醒「在哪」（spec 8.3）：单条邮件写小标加内容两行，列表里写一行。
///
/// @param label 单条邮件的小标（`文献讨论` 等），列表里为空
/// @param text 单条邮件的内容（标题或经验信息），列表里为空
/// @param line 列表里的一行，单条邮件里为空
public record ReminderPlace(String label, String text, String line) {

  /// 构造。
  ///
  /// @param label 小标
  /// @param text 内容
  /// @param line 列表里的一行
  /// @return 在哪
  public static ReminderPlace of(String label, String text, String line) {
    return new ReminderPlace(label, text, line);
  }
}
