package dev.patra.notification.app.inbox.usecase.poll;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.tool.cqrs.Command;

/// 门户拉一次当前用户的未读数：顺带记下他此刻在站上（决策 H），所以是命令而不是查询。
public record PollUnreadCountsCommand() implements Command<UnreadCounts> {

  /// 构造。
  ///
  /// @return 命令
  public static PollUnreadCountsCommand of() {
    return new PollUnreadCountsCommand();
  }
}
