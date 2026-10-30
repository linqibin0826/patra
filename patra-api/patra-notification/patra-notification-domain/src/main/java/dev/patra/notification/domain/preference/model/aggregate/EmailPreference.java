package dev.patra.notification.domain.preference.model.aggregate;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.exception.InvalidPreferenceRequestException;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.model.vo.UnsubscribeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/// 一个用户的提醒邮件偏好：「回复我的」「@ 我的」各一个档位，加他的退订令牌（spec 5.1）。
///
/// 没有偏好行的人按默认值处理，第一次改档位或第一次被取令牌时才落库。「有帮助」不在这里：它只在站内，没有邮件。
@Getter
public final class EmailPreference {

  private final long userId;
  private final UnsubscribeToken unsubscribeToken;
  private EmailMode reply;
  private EmailMode mention;

  /// 构造。
  ///
  /// @param userId 用户 ID
  /// @param unsubscribeToken 退订令牌
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  private EmailPreference(
      long userId, UnsubscribeToken unsubscribeToken, EmailMode reply, EmailMode mention) {
    this.userId = userId;
    this.unsubscribeToken = Objects.requireNonNull(unsubscribeToken, "unsubscribeToken 不能为 null");
    this.reply = Objects.requireNonNull(reply, "reply 不能为 null");
    this.mention = Objects.requireNonNull(mention, "mention 不能为 null");
  }

  /// 新用户的默认偏好：两类都是离站时逐条。
  ///
  /// @param userId 用户 ID
  /// @param unsubscribeToken 新生成的退订令牌
  /// @return 偏好
  public static EmailPreference createDefault(long userId, UnsubscribeToken unsubscribeToken) {
    return new EmailPreference(userId, unsubscribeToken, EmailMode.INSTANT, EmailMode.INSTANT);
  }

  /// 从库里恢复。
  ///
  /// @param userId 用户 ID
  /// @param unsubscribeToken 退订令牌
  /// @param reply 「回复我的」档位
  /// @param mention 「@ 我的」档位
  /// @return 偏好
  public static EmailPreference restore(
      long userId, UnsubscribeToken unsubscribeToken, EmailMode reply, EmailMode mention) {
    return new EmailPreference(userId, unsubscribeToken, reply, mention);
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

  /// 改一类的档位，另一类不动。
  ///
  /// @param category 类别
  /// @param mode 新档位
  /// @throws InvalidPreferenceRequestException 改「有帮助」：它没有邮件选项
  public void change(NotificationCategory category, EmailMode mode) {
    Objects.requireNonNull(mode, "mode 不能为 null");
    switch (category) {
      case REPLY -> reply = mode;
      case MENTION -> mention = mode;
      case HELPFUL -> throw new InvalidPreferenceRequestException("有帮助只在站内提醒，没有邮件选项");
    }
  }

  /// 按退订类别把对应的类别设成关（spec 5.2）。设成关而不是切换，所以重复退订结果相同。
  ///
  /// `REPLY` / `MENTION` 不看原来的档位；`DIGEST` 只关此刻在每日汇总的类别；`ALL` 两类都关。
  ///
  /// @param category 退订类别
  /// @return 这一次真的从开变成关的类别，按回复、@ 的顺序；重复退订时为空
  public List<NotificationCategory> unsubscribe(UnsubscribeCategory category) {
    List<NotificationCategory> turnedOff = new ArrayList<>();
    if (sendsEmail(covers(category, NotificationCategory.REPLY, reply), reply)) {
      reply = EmailMode.OFF;
      turnedOff.add(NotificationCategory.REPLY);
    }
    if (sendsEmail(covers(category, NotificationCategory.MENTION, mention), mention)) {
      mention = EmailMode.OFF;
      turnedOff.add(NotificationCategory.MENTION);
    }
    return List.copyOf(turnedOff);
  }

  /// 是不是已经退订过这一类（spec 5.3）：它管得到的类别都已经是关。`DIGEST` 即没有任何类别在每日汇总。
  ///
  /// @param category 退订类别
  /// @return 已经退订过为 `true`
  public boolean isUnsubscribedFrom(UnsubscribeCategory category) {
    return !sendsEmail(covers(category, NotificationCategory.REPLY, reply), reply)
        && !sendsEmail(covers(category, NotificationCategory.MENTION, mention), mention);
  }

  /// 退订这一类之后，还有没有提醒邮件开着（spec 5.3）：它管不到的类别里有没有不是关的。
  ///
  /// @param category 退订类别
  /// @return 还有开着的为 `true`；`ALL` 永远为 `false`
  public boolean anyEmailOnAfter(UnsubscribeCategory category) {
    return sendsEmail(!covers(category, NotificationCategory.REPLY, reply), reply)
        || sendsEmail(!covers(category, NotificationCategory.MENTION, mention), mention);
  }

  /// 在考虑范围内、且档位不是关。
  ///
  /// @param considered 这一类在不在考虑范围内
  /// @param mode 这一类的档位
  /// @return 在范围内且会发邮件为 `true`
  private static boolean sendsEmail(boolean considered, EmailMode mode) {
    return considered && mode != EmailMode.OFF;
  }

  /// 退订类别管不管得到这一类。
  ///
  /// @param category 退订类别
  /// @param target 「回复我的」或「@ 我的」
  /// @param current 这一类当前的档位
  /// @return 管得到为 `true`
  private static boolean covers(
      UnsubscribeCategory category, NotificationCategory target, EmailMode current) {
    return switch (category) {
      case REPLY -> target == NotificationCategory.REPLY;
      case MENTION -> target == NotificationCategory.MENTION;
      case DIGEST -> current == EmailMode.DIGEST;
      case ALL -> true;
    };
  }
}
