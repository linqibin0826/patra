package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.app.collection.service.Batches;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 移出收藏夹：锁本人 → 不是自己的 404 → 移出（不在夹里的忽略）。
@Component
@RequiredArgsConstructor
public class RemoveFromCollectionHandler
    implements CommandHandler<RemoveFromCollectionCommand, Void> {

  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 移出。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(RemoveFromCollectionCommand command) {
    long userId = currentUser.require().userId();
    CollectionId id = CollectionId.of(command.collectionId());
    List<SubjectId> ids = Batches.requireItemIds(command.itemIds());
    transactions.executeWithoutResult(
        status -> {
          library.lockOwner(userId);
          collections.findOwned(userId, id).orElseThrow(CollectionNotFoundException::new);
          collections.removeMembers(userId, id, ids, clock.instant());
        });
    return null;
  }
}
