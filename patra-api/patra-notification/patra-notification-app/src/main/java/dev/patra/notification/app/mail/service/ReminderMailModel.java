package dev.patra.notification.app.mail.service;

import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 提醒邮件交给发信端口的模板变量（spec 8.5）：`mail` 是拼好的内容，另有共用骨架 `layout.html` 读的三个顶层变量
/// `preheader`、`footnote`、`asideLinks`（安全邮件不放它们，骨架照旧）。`subject`、`siteName` 由渲染器每封覆盖，
/// `notice` 留给安全邮件，这三个键都不用；变量值不能为空，空的不放。
public final class ReminderMailModel {

  /// 版权页里替换「请勿回复」的那一句。
  static final String FOOTNOTE = "此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。";

  /// 只有静态方法，不实例化。
  private ReminderMailModel() {}

  /// 模板变量。
  ///
  /// @param content 拼好的内容
  /// @return 变量
  public static Map<String, Object> of(ReminderMailContent content) {
    Map<String, Object> model = new HashMap<>();
    model.put("mail", content);
    if (content.preheader() != null) {
      model.put("preheader", content.preheader());
    }
    model.put("footnote", FOOTNOTE);
    model.put(
        "asideLinks",
        List.of(
            AsideLink.of(content.unsubscribeLabel(), content.unsubscribeUrl()),
            AsideLink.of("通知设置", content.settingsUrl())));
    return Map.copyOf(model);
  }
}
