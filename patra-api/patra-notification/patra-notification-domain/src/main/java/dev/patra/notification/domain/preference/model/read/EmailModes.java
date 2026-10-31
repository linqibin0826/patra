package dev.patra.notification.domain.preference.model.read;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.util.Objects;

/// 一个人两类提醒的邮件档位（只读，不带令牌）：mail 装配时先用它判断该不该发，确定要发才去取令牌。
///
/// @param reply 「回复我的」档位
/// @param mention 「@ 我的」档位
public record EmailModes(EmailMode reply, EmailMode mention) {

  /// 没设过时的默认值：两类都是离站时逐条。
  public static final EmailModes DEFAULT = new EmailModes(EmailMode.INSTANT, EmailMode.INSTANT);

  /// 校验非空。
  public EmailModes {
    Objects.requireNonNull(reply, "reply 不能为 null");
    Objects.requireNonNull(mention, "mention 不能为 null");
  }

  /// 构造。
  ///
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @return 档位
  public static EmailModes of(EmailMode reply, EmailMode mention) {
    return new EmailModes(reply, mention);
  }

  /// 某一类的档位。
  ///
  /// @param category 类别
  /// @return 档位；「有帮助」永远是关
  public EmailMode modeOf(NotificationCategory category) {
    return switch (category) {
      case REPLY -> reply;
      case MENTION -> mention;
      case HELPFUL -> EmailMode.OFF;
    };
  }

  /// 有没有一类设为每日汇总。
  ///
  /// @return 有为真
  public boolean anyDigest() {
    return reply == EmailMode.DIGEST || mention == EmailMode.DIGEST;
  }
}
