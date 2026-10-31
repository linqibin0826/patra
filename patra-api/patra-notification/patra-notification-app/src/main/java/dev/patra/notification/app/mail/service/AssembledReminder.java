package dev.patra.notification.app.mail.service;

import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import java.util.Objects;

/// 装配一封提醒邮件的结果：可以发了，或者确定不该发。
public sealed interface AssembledReminder permits AssembledReminder.Ready, AssembledReminder.Skip {

  /// 可以发了。
  ///
  /// @param to 收件地址
  /// @param content 拼好的内容
  record Ready(String to, ReminderMailContent content) implements AssembledReminder {

    /// 校验非空。
    public Ready {
      Objects.requireNonNull(to, "to 不能为 null");
      Objects.requireNonNull(content, "content 不能为 null");
    }

    /// 构造。
    ///
    /// @param to 收件地址
    /// @param content 内容
    /// @return 结果
    public static Ready of(String to, ReminderMailContent content) {
      return new Ready(to, content);
    }
  }

  /// 确定不该发。
  ///
  /// @param reason 原因
  record Skip(SkipReason reason) implements AssembledReminder {

    /// 校验非空。
    public Skip {
      Objects.requireNonNull(reason, "reason 不能为 null");
    }

    /// 构造。
    ///
    /// @param reason 原因
    /// @return 结果
    public static Skip of(SkipReason reason) {
      return new Skip(reason);
    }
  }
}
