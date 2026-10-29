package dev.patra.interaction.app.collection.usecase.copy;

import static dev.patra.interaction.app.collection.Fixtures.card;
import static dev.patra.interaction.app.collection.Fixtures.collection;
import static dev.patra.interaction.app.collection.Fixtures.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.app.collection.TestUsers;
import dev.patra.interaction.app.collection.service.CollectionAccess;
import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.exception.CollectionLimitReachedException;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.exception.LibraryFullException;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 复制别人的公开收藏夹：失效的跳过，已在库里的不重复收，重名加序号；放不下整体失败。
class CopyCollectionHandlerTest {

  private static final long OWNER = 7L;
  private static final long ME = 9L;
  private static final CollectionId SOURCE = CollectionId.of(500L);
  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
  private static final SubjectId S1 = SubjectId.of(11L);
  private static final SubjectId S2 = SubjectId.of(12L);
  private static final SubjectId S3 = SubjectId.of(13L);

  private final LibraryReadPort reads = mock(LibraryReadPort.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CollectionRepository collections = mock(CollectionRepository.class);
  private CopyCollectionHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new CopyCollectionHandler(
            new CollectionAccess(reads, users),
            reads,
            new SubjectViews(subjects),
            library,
            collections,
            TestUsers.signedIn(ME),
            new RecordingTransactions(new ArrayList<>()),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(reads.findCollection(SOURCE))
        .thenReturn(Optional.of(collection(500L, OWNER, CollectionVisibility.PUBLIC, 3)));
    when(users.findCards(List.of(OWNER), ME))
        .thenReturn(Map.of(OWNER, card(OWNER, AccountState.ACTIVE, true)));
    when(reads.allCollection(OWNER, SOURCE))
        .thenReturn(List.of(ItemRef.of(S1, NOW), ItemRef.of(S2, NOW), ItemRef.of(S3, NOW)));
    when(subjects.findByIds(List.of(S1, S2, S3)))
        .thenReturn(
            Map.of(
                S1, view(11L, SubjectState.ACTIVE, "1", "J", null),
                S2, view(12L, SubjectState.ACTIVE, "2", "J", null),
                S3, view(13L, SubjectState.UNAVAILABLE, "3", "J", null)));
    when(library.findExistingItems(ME, List.of(S1, S2))).thenReturn(Set.of(S2));
    when(collections.findNames(ME)).thenReturn(Set.of("组会"));
  }

  /// 正常复制：新夹「组会 (2)」带源简介；S1 新收进、S2 原本就在、S3 失效跳过；夹里放 S1、S2。
  @Test
  void should_copy_with_counts_and_free_name() {
    CollectionSummary created = collection(600L, ME, CollectionVisibility.PRIVATE, 0);
    when(collections.create(ME, CollectionName.parse("组会 (2)"), "每周四", NOW)).thenReturn(created);
    when(library.insertItems(ME, List.of(S1), NOW)).thenReturn(1);

    CopyResult result = handler.handle(CopyCollectionCommand.of(500L));

    assertThat(result)
        .isEqualTo(
            CopyResult.builder()
                .collectionId(CollectionId.of(600L))
                .name("组会 (2)")
                .addedToLibrary(1)
                .alreadyInLibrary(1)
                .skippedUnavailable(1)
                .build());
    verify(library).lockOwner(ME);
    verify(collections).addMembers(ME, CollectionId.of(600L), List.of(S1, S2), NOW);
  }

  /// 库放不下新篇目：整体失败，什么都不写。
  @Test
  void should_fail_whole_copy_when_library_cannot_fit() {
    when(library.countItems(ME)).thenReturn(20_000);

    assertThatThrownBy(() -> handler.handle(CopyCollectionCommand.of(500L)))
        .isInstanceOf(LibraryFullException.class);
    verify(collections, never()).create(anyLong(), any(), any(), any());
    verify(library, never()).insertItems(anyLong(), any(), any());
    verify(collections, never()).addMembers(anyLong(), any(), any(), any());
  }

  /// 收藏夹已满 200 个。
  @Test
  void should_reject_when_collections_full() {
    when(collections.countCollections(ME)).thenReturn(200);

    assertThatThrownBy(() -> handler.handle(CopyCollectionCommand.of(500L)))
        .isInstanceOf(CollectionLimitReachedException.class);
  }

  /// 私有夹不能复制，本人的也不行。
  @Test
  void should_not_copy_private_even_for_owner() {
    when(reads.findCollection(SOURCE))
        .thenReturn(Optional.of(collection(500L, ME, CollectionVisibility.PRIVATE, 3)));
    when(users.findCards(List.of(ME), ME))
        .thenReturn(Map.of(ME, card(ME, AccountState.ACTIVE, true)));

    assertThatThrownBy(() -> handler.handle(CopyCollectionCommand.of(500L)))
        .isInstanceOf(CollectionNotFoundException.class);
  }
}
