package dev.patra.interaction.app.collection.usecase.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.app.collection.TestUsers;
import dev.patra.interaction.domain.collection.exception.CollectionLimitReachedException;
import dev.patra.interaction.domain.collection.exception.CollectionNameTakenException;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 收藏夹的新建、改名、删除、放进 / 移出。
class CollectionHandlersTest {

  private static final long U = 1001L;
  private static final CollectionId C = CollectionId.of(500L);
  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
  private static final SubjectId S1 = SubjectId.of(11L);
  private static final SubjectId S2 = SubjectId.of(12L);
  private static final SubjectId S3 = SubjectId.of(13L);
  private static final SubjectId S4 = SubjectId.of(14L);
  private static final SubjectId S5 = SubjectId.of(15L);

  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CollectionRepository collections = mock(CollectionRepository.class);
  private final CurrentUserPort currentUser = TestUsers.signedIn(U);
  private final RecordingTransactions transactions = new RecordingTransactions(new ArrayList<>());
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  /// 新建：规范化文本后锁、查个数、查重名、写。
  @Test
  void should_create_collection() {
    CollectionSummary created = summary(0);
    when(collections.create(U, CollectionName.parse("组会"), "每周四", NOW)).thenReturn(created);

    CollectionSummary result =
        new CreateCollectionHandler(library, collections, currentUser, transactions, clock)
            .handle(CreateCollectionCommand.of(" 组会 ", "每周四 "));

    assertThat(result).isEqualTo(created);
    InOrder order = inOrder(library, collections);
    order.verify(library).lockOwner(U);
    order.verify(collections).create(U, CollectionName.parse("组会"), "每周四", NOW);
  }

  /// 第 201 个被拒。
  @Test
  void should_reject_201st_collection() {
    when(collections.countCollections(U)).thenReturn(200);

    assertThatThrownBy(
            () ->
                new CreateCollectionHandler(library, collections, currentUser, transactions, clock)
                    .handle(CreateCollectionCommand.of("组会", null)))
        .isInstanceOf(CollectionLimitReachedException.class);
    verify(collections, never()).create(anyLong(), any(), any(), any());
  }

  /// 重名被拒。
  @Test
  void should_reject_taken_name() {
    when(collections.nameTaken(U, "组会", null)).thenReturn(true);

    assertThatThrownBy(
            () ->
                new CreateCollectionHandler(library, collections, currentUser, transactions, clock)
                    .handle(CreateCollectionCommand.of("组会", null)))
        .isInstanceOf(CollectionNameTakenException.class);
  }

  /// 改名排除自己查重名；不是自己的 404。
  @Test
  void should_update_text_excluding_self() {
    when(collections.findOwned(U, C)).thenReturn(Optional.of(summary(3)));
    when(collections.updateText(U, C, CollectionName.parse("周会"), null, NOW))
        .thenReturn(summary(3));
    UpdateCollectionHandler handler =
        new UpdateCollectionHandler(library, collections, currentUser, transactions, clock);

    handler.handle(UpdateCollectionCommand.of(500L, "周会", "  "));

    verify(collections).nameTaken(U, "周会", C);
    verify(collections).updateText(U, C, CollectionName.parse("周会"), null, NOW);
    when(collections.findOwned(U, C)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(UpdateCollectionCommand.of(500L, "周会", null)))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  /// 删别人的或不存在的：不报错、不删。
  @Test
  void should_ignore_delete_of_missing_collection() {
    when(collections.findOwned(U, C)).thenReturn(Optional.empty());

    new DeleteCollectionHandler(library, collections, currentUser, transactions)
        .handle(DeleteCollectionCommand.of(500L));

    verify(collections, never()).delete(anyLong(), any());
  }

  /// 删自己的。
  @Test
  void should_delete_own_collection() {
    when(collections.findOwned(U, C)).thenReturn(Optional.of(summary(3)));

    new DeleteCollectionHandler(library, collections, currentUser, transactions)
        .handle(DeleteCollectionCommand.of(500L));

    verify(collections).delete(U, C);
  }

  /// 按请求顺序分类：不在库里、已在夹里、放满后剩下的。
  @Test
  void should_add_until_full_and_count_the_rest() {
    when(collections.findOwned(U, C)).thenReturn(Optional.of(summary(4998)));
    when(library.findExistingItems(U, List.of(S1, S2, S3, S4, S5)))
        .thenReturn(Set.of(S2, S3, S4, S5));
    when(collections.findMembers(U, C, List.of(S1, S2, S3, S4, S5))).thenReturn(Set.of(S2));

    AddToCollectionResult result =
        new AddToCollectionHandler(library, collections, currentUser, transactions, clock)
            .handle(AddToCollectionCommand.of(500L, List.of(11L, 12L, 13L, 14L, 15L)));

    assertThat(result).isEqualTo(AddToCollectionResult.of(2, 1, 1, 1));
    verify(collections).addMembers(U, C, List.of(S3, S4), NOW);
  }

  /// 放进别人的收藏夹 404。
  @Test
  void should_404_when_adding_to_someone_elses_collection() {
    when(collections.findOwned(U, C)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new AddToCollectionHandler(library, collections, currentUser, transactions, clock)
                    .handle(AddToCollectionCommand.of(500L, List.of(11L))))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  /// 移出：锁、确认是自己的、移出。
  @Test
  void should_remove_from_own_collection() {
    when(collections.findOwned(U, C)).thenReturn(Optional.of(summary(2)));

    new RemoveFromCollectionHandler(library, collections, currentUser, transactions, clock)
        .handle(RemoveFromCollectionCommand.of(500L, List.of(11L, 12L)));

    InOrder order = inOrder(library, collections);
    order.verify(library).lockOwner(U);
    order.verify(collections).removeMembers(U, C, List.of(S1, S2), NOW);
  }

  /// 一个收藏夹。
  ///
  /// @param itemCount 篇数
  /// @return 收藏夹
  private static CollectionSummary summary(int itemCount) {
    return CollectionSummary.builder()
        .collectionId(C)
        .ownerId(U)
        .name("组会")
        .visibility(CollectionVisibility.PRIVATE)
        .itemCount(itemCount)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
