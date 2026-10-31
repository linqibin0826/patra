package dev.patra.notification.domain.inbox.model.enums;

import dev.patra.notification.domain.inbox.exception.InvalidInboxRequestException;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.util.Arrays;

/// 提醒列表的三个标签与它们在查询参数里的写法。
public enum InboxTab {

  /// 回复我的。
  REPLIES("replies", NotificationCategory.REPLY),

  /// @ 我的。
  MENTIONS("mentions", NotificationCategory.MENTION),

  /// 有帮助（PAP-144 写入条目）。
  HELPFUL("helpful", NotificationCategory.HELPFUL);

  private final String param;
  private final NotificationCategory category;

  /// 构造。
  ///
  /// @param param 查询参数里的写法
  /// @param category 对应的类别
  InboxTab(String param, NotificationCategory category) {
    this.param = param;
    this.category = category;
  }

  /// 对应的类别。
  ///
  /// @return 类别
  public NotificationCategory category() {
    return category;
  }

  /// 解析查询参数；缺省是「回复我的」。
  ///
  /// @param param 参数，可空
  /// @return 标签
  /// @throws InvalidInboxRequestException 不认识的写法
  public static InboxTab fromParam(String param) {
    if (param == null || param.isBlank()) {
      return REPLIES;
    }
    return Arrays.stream(values())
        .filter(t -> t.param.equals(param))
        .findFirst()
        .orElseThrow(() -> new InvalidInboxRequestException("tab 只能是 replies、mentions 或 helpful"));
  }
}
