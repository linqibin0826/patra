package dev.patra.interaction.app.collection.usecase.collection;

import dev.patra.interaction.app.collection.service.Batches;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 放进收藏夹：锁本人 → 不是自己的 404 → 按请求顺序分类（不在库里 / 已在夹里 / 放得下 / 夹满后剩下的）→ 写。
/// 夹满不报错，篇数回在结果里。
@Component
@RequiredArgsConstructor
public class AddToCollectionHandler
    implements CommandHandler<AddToCollectionCommand, AddToCollectionResult> {

  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 放进。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public AddToCollectionResult handle(AddToCollectionCommand command) {
    long userId = currentUser.require().userId();
    CollectionId id = CollectionId.of(command.collectionId());
    List<SubjectId> requested = Batches.requireItemIds(command.itemIds());
    return transactions.execute(
        status -> {
          library.lockOwner(userId);
          CollectionSummary collection =
              collections.findOwned(userId, id).orElseThrow(CollectionNotFoundException::new);
          Set<SubjectId> inLibrary = library.findExistingItems(userId, requested);
          Set<SubjectId> inCollection = collections.findMembers(userId, id, requested);
          int room = CollectionLimits.COLLECTION_MAX_ITEMS - collection.itemCount();
          List<SubjectId> toAdd = new ArrayList<>();
          int alreadyIn = 0;
          int skippedFull = 0;
          int skippedNotInLibrary = 0;
          for (SubjectId subjectId : requested) {
            if (!inLibrary.contains(subjectId)) {
              skippedNotInLibrary++;
            } else if (inCollection.contains(subjectId)) {
              alreadyIn++;
            } else if (toAdd.size() < room) {
              toAdd.add(subjectId);
            } else {
              skippedFull++;
            }
          }
          collections.addMembers(userId, id, toAdd, clock.instant());
          return AddToCollectionResult.builder()
              .added(toAdd.size())
              .alreadyIn(alreadyIn)
              .skippedFull(skippedFull)
              .skippedNotInLibrary(skippedNotInLibrary)
              .build();
        });
  }
}
