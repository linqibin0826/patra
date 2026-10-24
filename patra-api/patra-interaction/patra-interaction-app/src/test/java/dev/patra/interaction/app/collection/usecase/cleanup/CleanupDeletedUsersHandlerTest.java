package dev.patra.interaction.app.collection.usecase.cleanup;

import static dev.patra.interaction.app.collection.Fixtures.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.domain.collection.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.user.AccountState;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.collection.port.user.UserDirectoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 注销清理：按用户游标分批回查 identity，只删 DELETED 的；identity 不可用跳过这一批继续。
class CleanupDeletedUsersHandlerTest {

  private final LibraryReadPort reads = mock(LibraryReadPort.class);
  private final LibraryRepository library = mock(LibraryRepository.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private CleanupDeletedUsersHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new CleanupDeletedUsersHandler(
            reads, library, users, new RecordingTransactions(new ArrayList<>()));
  }

  /// 只删 DELETED；ACTIVE 与 identity 省略的都不删。
  @Test
  void should_delete_only_deleted_users() {
    when(reads.findOwnersAfter(0L, 100)).thenReturn(List.of(1L, 2L, 3L));
    when(users.findCards(List.of(1L, 2L, 3L), null))
        .thenReturn(
            Map.of(
                1L, card(1L, AccountState.DELETED, false),
                2L, card(2L, AccountState.ACTIVE, true)));
    when(library.deleteAllOf(1L)).thenReturn(DeletedUserData.of(5, 2, 3));

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result)
        .isEqualTo(
            CleanupResult.builder()
                .scannedUsers(3)
                .deletedUsers(1)
                .libraryItems(5)
                .collections(2)
                .collectionItems(3)
                .failedBatches(0)
                .build());
    InOrder order = inOrder(library);
    order.verify(library).lockOwner(1L);
    order.verify(library).deleteAllOf(1L);
    verify(library, never()).deleteAllOf(2L);
    verify(library, never()).deleteAllOf(3L);
  }

  /// identity 不可用：这一批计失败，游标照常往后走。
  @Test
  void should_skip_batch_when_identity_down_and_continue() {
    List<Long> first = LongStream.rangeClosed(1, 100).boxed().toList();
    when(reads.findOwnersAfter(0L, 100)).thenReturn(first);
    when(reads.findOwnersAfter(100L, 100)).thenReturn(List.of(101L));
    when(users.findCards(first, null))
        .thenThrow(new UserDirectoryUnavailableException(new RuntimeException()));
    when(users.findCards(List.of(101L), null))
        .thenReturn(Map.of(101L, card(101L, AccountState.DELETED, false)));
    when(library.deleteAllOf(101L)).thenReturn(DeletedUserData.of(1, 0, 0));

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.failedBatches()).isEqualTo(1);
    assertThat(result.scannedUsers()).isEqualTo(101);
    assertThat(result.deletedUsers()).isEqualTo(1);
    verify(library).deleteAllOf(101L);
  }

  /// 没有数据就什么都不做。
  @Test
  void should_do_nothing_when_no_owners() {
    when(reads.findOwnersAfter(0L, 100)).thenReturn(List.of());

    assertThat(handler.handle(CleanupDeletedUsersCommand.of()).scannedUsers()).isZero();
    verify(library, never()).deleteAllOf(anyLong());
  }
}
