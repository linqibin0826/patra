package dev.patra.interaction.app.reaction.usecase.helpful;

import dev.patra.interaction.app.reaction.service.ReactionTargets;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.model.vo.TargetProfile;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 点有帮助（spec §4.1）：
///
/// 1. 事务外：解析目标、判能不能点（不合法的请求不打 identity、不耗额度）→ 已经点过直接返回（重复点幂等，不查资格）
///    → 发表资格（identity）；
/// 2. 事务：发表守卫计数（作者锁）→ 目标锁 → 锁内重读目标、判是否仍能点 → `ON CONFLICT DO NOTHING` 插入明细，
///    真插入才给目标计数、受益人获赞各 +1 → 读当前计数。
///
/// 锁顺序：配额锁 → 目标锁 → 目标行 → 用户行。额度在插入之前记：只有锁内才确定会不会插入，而配额锁要最先拿；两个
/// 请求同时点同一目标时后到的那个白耗一次额度，接受。目标锁让「删评论的同时有人点」不会留下孤儿明细（spec §4.3）。
@Component
@RequiredArgsConstructor
public class MarkHelpfulHandler implements CommandHandler<MarkHelpfulCommand, HelpfulResult> {

  private final ReactionTargets targets;
  private final PublishGuardGateway guard;
  private final HelpfulRepository helpful;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 点。
  ///
  /// @param command 命令
  /// @return 点之后的状态
  @Override
  public HelpfulResult handle(MarkHelpfulCommand command) {
    long userId = currentUser.require().userId();
    HelpfulTarget target = HelpfulTarget.of(command.type(), command.targetId());
    TargetProfile profile = targets.require(target);
    profile.requireMarkableBy(userId);
    if (helpful.exists(userId, target)) {
      return HelpfulResult.of(true, helpful.countOf(target));
    }
    guard.requireEligible(userId);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    return transactions.execute(
        status -> {
          guard.admit(userId, PublishKind.HELPFUL);
          helpful.lockTarget(target);
          targets.require(target).requireAvailable();
          Long credited = profile.creditedUserId();
          if (helpful.insert(userId, target, credited, now)) {
            helpful.adjustTargetCount(target, 1);
            if (credited != null) {
              helpful.adjustReceived(credited, 1);
            }
          }
          return HelpfulResult.of(true, helpful.countOf(target));
        });
  }
}
