package dev.patra.notification.app.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent.Layout;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 交给发信端口的模板变量（spec 8.5）：`mail` 加 layout 读的三个顶层变量，不占渲染器保留的键，不放空值。
class ReminderMailModelTest {

  /// 键齐全，退订链接与通知设置是卡片外的两个链接。
  @Test
  void model_has_mail_and_layout_keys_without_reserved_or_null_values() {
    ReminderMailContent content = content("预览");

    Map<String, Object> model = ReminderMailModel.of(content);

    assertThat(model)
        .containsEntry("mail", content)
        .containsEntry("preheader", "预览")
        .containsEntry("footnote", "此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。")
        .containsEntry(
            "asideLinks",
            List.of(
                AsideLink.of("退订这类邮件", "https://p/unsubscribe#token=T&category=REPLY"),
                AsideLink.of("通知设置", "https://p/settings/notifications")))
        .doesNotContainKeys("subject", "siteName", "notice");
  }

  /// 预览文字为空时不放这个键（模板变量不能为空值）。
  @Test
  void missing_preheader_is_left_out() {
    assertThat(ReminderMailModel.of(content(null))).doesNotContainKey("preheader");
  }

  private static ReminderMailContent content(String preheader) {
    return ReminderMailContent.builder()
        .layout(Layout.SINGLE)
        .subject("王立 回复了你的评论")
        .preheader(preheader)
        .unsubscribeLabel("退订这类邮件")
        .unsubscribeUrl("https://p/unsubscribe#token=T&category=REPLY")
        .preferencesUrl("https://p/settings/notifications")
        .oneClickUrl("https://p/unsubscribe/one-click?token=T&category=REPLY")
        .build();
  }
}
