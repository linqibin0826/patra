package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.exception.PublishNotAllowedException;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 设可见性（spec §5.5）。设为公开且当前不是公开时，事务外回查 identity：账号不是 `ACTIVE`（含 identity 省略）→
/// `ACCOUNT_RESTRICTED`；邮箱未验证 → `EMAIL_UNVERIFIED`。设为私有、已公开再设公开都不回查。
@Component
@RequiredArgsConstructor
public class SetVisibilityHandler
    implements CommandHandler<SetVisibilityCommand, CollectionSummary> {

  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final UserDirectoryPort users;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 设可见性。
  ///
  /// @param command 命令
  /// @return 改后的收藏夹
  @Override
  public CollectionSummary handle(SetVisibilityCommand command) {
    long userId = currentUser.require().userId();
    CollectionId id = CollectionId.of(command.collectionId());
    CollectionVisibility target = CollectionVisibility.fromParam(command.visibility());
    CollectionSummary current =
        collections.findOwned(userId, id).orElseThrow(CollectionNotFoundException::new);
    if (current.visibility() == target) {
      return current;
    }
    if (target == CollectionVisibility.PUBLIC) {
      requireEligible(users.findCards(List.of(userId), userId).get(userId));
    }
    return transactions.execute(
        status -> {
          library.lockOwner(userId);
          collections.findOwned(userId, id).orElseThrow(CollectionNotFoundException::new);
          return collections.updateVisibility(userId, id, target, clock.instant());
        });
  }

  /// 公开资格：先看账号状态再看邮箱。
  ///
  /// @param self 自己的摘要，identity 省略时为 `null`
  private static void requireEligible(UserCard self) {
    if (self == null || self.state() != AccountState.ACTIVE) {
      throw PublishNotAllowedException.accountRestricted();
    }
    if (!self.emailVerified()) {
      throw PublishNotAllowedException.emailUnverified();
    }
  }
}
