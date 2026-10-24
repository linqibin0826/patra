package dev.patra.interaction.app.collection.usecase.copy;

import dev.patra.interaction.app.collection.service.CollectionAccess;
import dev.patra.interaction.app.collection.service.ReadableCollection;
import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.exception.CollectionLimitReachedException;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.exception.LibraryFullException;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.collection.policy.CopyNaming;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 复制别人的公开收藏夹（spec §5.8）：
///
/// 1. 判可见（同公开收藏夹页）；源夹必须是公开的，私有夹本人也不能复制。
/// 2. 取源夹全部条目与状态，正常的进复制清单（按源夹的加入时间倒序），失效的计数跳过。
/// 3. 事务（只锁复制者）：满 200 个夹拒绝 → 新篇目放不进 20,000 整体失败 → 定名 → 建私有夹（简介照抄）→ 新篇目进
///    文献库 → 清单全部进新夹。备注不复制，复制品不记来源。
@Component
@RequiredArgsConstructor
public class CopyCollectionHandler implements CommandHandler<CopyCollectionCommand, CopyResult> {

  private final CollectionAccess access;
  private final LibraryReadPort reads;
  private final SubjectViews subjectViews;
  private final LibraryRepository library;
  private final CollectionRepository collections;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 复制。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public CopyResult handle(CopyCollectionCommand command) {
    long userId = currentUser.require().userId();
    ReadableCollection readable =
        access.open(CollectionId.of(command.collectionId()), Optional.of(userId));
    CollectionSummary source = readable.collection();
    if (!source.isPublic()) {
      throw new CollectionNotFoundException();
    }
    List<SubjectId> all =
        reads.allCollection(source.ownerId(), source.collectionId()).stream()
            .map(ItemRef::subjectId)
            .toList();
    Map<SubjectId, SubjectView> views = subjectViews.fetch(all);
    List<SubjectId> copyable =
        all.stream()
            .filter(id -> views.containsKey(id) && views.get(id).state() == SubjectState.ACTIVE)
            .toList();
    int skippedUnavailable = all.size() - copyable.size();
    return transactions.execute(
        status -> {
          library.lockOwner(userId);
          if (collections.countCollections(userId) >= CollectionLimits.MAX_COLLECTIONS) {
            throw new CollectionLimitReachedException();
          }
          Set<SubjectId> existing = library.findExistingItems(userId, copyable);
          List<SubjectId> fresh = copyable.stream().filter(id -> !existing.contains(id)).toList();
          if (library.countItems(userId) + fresh.size() > CollectionLimits.LIBRARY_MAX_ITEMS) {
            throw new LibraryFullException();
          }
          CollectionName name =
              CopyNaming.nameFor(
                  CollectionName.parse(source.name()), collections.findNames(userId));
          Instant now = clock.instant();
          CollectionSummary created = collections.create(userId, name, source.description(), now);
          int added = library.insertItems(userId, fresh, now);
          collections.addMembers(userId, created.collectionId(), copyable, now);
          return CopyResult.builder()
              .collectionId(created.collectionId())
              .name(name.value())
              .addedToLibrary(added)
              .alreadyInLibrary(copyable.size() - fresh.size())
              .skippedUnavailable(skippedUnavailable)
              .build();
        });
  }
}
