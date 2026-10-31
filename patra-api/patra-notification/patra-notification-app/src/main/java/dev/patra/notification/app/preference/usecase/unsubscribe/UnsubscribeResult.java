package dev.patra.notification.app.preference.usecase.unsubscribe;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.model.enums.UnsubscribeCategory;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import java.util.List;
import lombok.Builder;

/// 一次退订的结果（spec 8.3）。
///
/// @param category 这次的退订类别
/// @param turnedOff 这一次真的从开变成关的类别，按回复、@ 的顺序；重复退订时为空
/// @param reply 退订之后「回复我的」的档位
/// @param mention 退订之后「@ 我的」的档位
/// @param othersEnabled 退订之后还有没有提醒邮件开着
@Builder
public record UnsubscribeResult(
    UnsubscribeCategory category,
    List<NotificationCategory> turnedOff,
    EmailMode reply,
    EmailMode mention,
    boolean othersEnabled) {

  /// 防御性拷贝。
  public UnsubscribeResult {
    turnedOff = turnedOff != null ? List.copyOf(turnedOff) : List.of();
  }
}
