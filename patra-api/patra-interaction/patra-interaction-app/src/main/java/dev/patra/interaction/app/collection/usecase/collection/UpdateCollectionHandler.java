package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.exception.CollectionNameTakenException;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionDescription;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改名称与简介：锁本人 → 不是自己的 404 → 与别的夹重名拒绝 → 写。
@Component
@RequiredArgsConstructor
public class UpdateCollectionHandler
    implements CommandHandler<UpdateCollectionCommand, CollectionSummary> {

  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 改。
  ///
  /// @param command 命令
  /// @return 改后的收藏夹
  @Override
  public CollectionSummary handle(UpdateCollectionCommand command) {
    long userId = currentUser.require().userId();
    CollectionId id = CollectionId.of(command.collectionId());
    CollectionName name = CollectionName.parse(command.name());
    String description =
        CollectionDescription.parse(command.description())
            .map(CollectionDescription::value)
            .orElse(null);
    return transactions.execute(
        status -> {
          library.lockOwner(userId);
          collections.findOwned(userId, id).orElseThrow(CollectionNotFoundException::new);
          if (collections.nameTaken(userId, name.value(), id)) {
            throw new CollectionNameTakenException();
          }
          return collections.updateText(userId, id, name, description, clock.instant());
        });
  }
}
