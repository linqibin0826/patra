package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 删收藏夹：锁本人，是自己的才删；不存在或不是自己的也不报错（不泄露别人的夹是否存在）。
@Component
@RequiredArgsConstructor
public class DeleteCollectionHandler implements CommandHandler<DeleteCollectionCommand, Void> {

  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;

  /// 删。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(DeleteCollectionCommand command) {
    long userId = currentUser.require().userId();
    CollectionId id = CollectionId.of(command.collectionId());
    transactions.executeWithoutResult(
        status -> {
          library.lockOwner(userId);
          if (collections.findOwned(userId, id).isPresent()) {
            collections.delete(userId, id);
          }
        });
    return null;
  }
}
