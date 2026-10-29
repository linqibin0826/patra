package dev.patra.interaction.app.reaction.usecase.helpful;

import dev.patra.interaction.domain.reaction.model.vo.HelpfulMark;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 取消有帮助（spec §4.2）：只要登录，不查发表资格（与删除自己的评论一致），也不查目标（目标删了，明细已随之删掉，
/// 取消就是什么都不做）。事务里拿目标锁、删明细，真删掉才给目标计数、受益人获赞各 −1。不退额度。
@Component
@RequiredArgsConstructor
public class UnmarkHelpfulHandler implements CommandHandler<UnmarkHelpfulCommand, HelpfulResult> {

  private final HelpfulRepository helpful;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;

  /// 取消。
  ///
  /// @param command 命令
  /// @return 取消之后的状态
  @Override
  public HelpfulResult handle(UnmarkHelpfulCommand command) {
    long userId = currentUser.require().userId();
    HelpfulTarget target = HelpfulTarget.of(command.type(), command.targetId());
    return transactions.execute(
        status -> {
          helpful.lockTarget(target);
          Optional<HelpfulMark> removed = helpful.delete(userId, target);
          removed.ifPresent(
              mark -> {
                helpful.adjustTargetCount(target, -1);
                if (mark.creditedUserId() != null) {
                  helpful.adjustReceived(mark.creditedUserId(), -1);
                }
              });
          return HelpfulResult.of(false, helpful.countOf(target));
        });
  }
}
