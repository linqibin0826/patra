package dev.patra.notification.app.inbox.usecase.poll;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 拉未读数：先记在站（门户每 60 秒拉一次，提醒邮件据此判断「离站」），再读三个未读数。命中 304 的请求同样记。
@Component
@RequiredArgsConstructor
public class PollUnreadCountsHandler
    implements CommandHandler<PollUnreadCountsCommand, UnreadCounts> {

  private final PresenceGateway presence;
  private final InboxReadPort reads;
  private final CurrentUserPort currentUser;

  /// 记在站并读未读数。
  ///
  /// @param command 命令
  /// @return 未读数
  @Override
  public UnreadCounts handle(PollUnreadCountsCommand command) {
    long userId = currentUser.require().userId();
    presence.markSeen(userId);
    return reads.unreadCounts(userId);
  }
}
