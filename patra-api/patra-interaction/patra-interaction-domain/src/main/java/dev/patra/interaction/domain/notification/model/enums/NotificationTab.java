package dev.patra.interaction.domain.notification.model.enums;

import dev.patra.interaction.domain.notification.exception.InvalidNotificationRequestException;
import java.util.Arrays;

/// 提醒列表的两个标签。
public enum NotificationTab {

  /// 回复我的：有人回复我的评论、有人评论我的经验。
  REPLIES("replies"),

  /// @ 我的：有人在评论里 @ 我。
  MENTIONS("mentions");

  private final String param;

  /// 构造。
  ///
  /// @param param 查询参数里的写法
  NotificationTab(String param) {
    this.param = param;
  }

  /// 查询参数里的写法。
  ///
  /// @return `replies` / `mentions`
  public String param() {
    return param;
  }

  /// 解析查询参数；缺省是「回复我的」。
  ///
  /// @param param 参数，可空
  /// @return 标签
  /// @throws InvalidNotificationRequestException 不认识的写法
  public static NotificationTab fromParam(String param) {
    if (param == null || param.isBlank()) {
      return REPLIES;
    }
    return Arrays.stream(values())
        .filter(t -> t.param.equals(param))
        .findFirst()
        .orElseThrow(() -> new InvalidNotificationRequestException("tab 只能是 replies 或 mentions"));
  }
}
