package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.domain.collection.exception.CollectionLimitReachedException;
import dev.patra.interaction.domain.collection.exception.CollectionNameTakenException;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionDescription;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 新建收藏夹：解析文本 → 锁本人 → 满 200 个拒绝 → 重名拒绝 → 建私有空夹。
@Component
@RequiredArgsConstructor
public class CreateCollectionHandler
    implements CommandHandler<CreateCollectionCommand, CollectionSummary> {

  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 新建。
  ///
  /// @param command 命令
  /// @return 新收藏夹
  @Override
  public CollectionSummary handle(CreateCollectionCommand command) {
    long userId = currentUser.require().userId();
    CollectionName name = CollectionName.parse(command.name());
    String description =
        CollectionDescription.parse(command.description())
            .map(CollectionDescription::value)
            .orElse(null);
    return transactions.execute(
        status -> {
          library.lockOwner(userId);
          if (collections.countCollections(userId) >= CollectionLimits.MAX_COLLECTIONS) {
            throw new CollectionLimitReachedException();
          }
          if (collections.nameTaken(userId, name.value(), null)) {
            throw new CollectionNameTakenException();
          }
          return collections.create(userId, name, description, clock.instant());
        });
  }
}
