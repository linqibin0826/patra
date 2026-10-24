package dev.patra.interaction.app.collection.usecase.library;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/// 从文献库移除：锁住本人后移除（连带移出收藏夹由仓储一并做），批量 1 ~ 100 个、去重。
class RemoveItemsHandlerTest {

  private static final long U = 1001L;
  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

  private final LibraryRepository library = mock(LibraryRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private RemoveItemsHandler handler;

  @BeforeEach
  void setUp() {
    when(currentUser.require()).thenReturn(CurrentUser.of(U, 1L, AccountType.USER, ClientType.WEB));
    handler =
        new RemoveItemsHandler(
            library,
            currentUser,
            new RecordingTransactions(new ArrayList<>()),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 先锁再删，重复 ID 去掉。
  @Test
  void should_lock_then_delete_items() {
    handler.handle(RemoveItemsCommand.of(List.of(11L, 12L, 11L)));

    InOrder order = inOrder(library);
    order.verify(library).lockOwner(U);
    order.verify(library).deleteItems(U, List.of(SubjectId.of(11L), SubjectId.of(12L)), NOW);
  }

  /// 超过 100 个、空、非正数都是请求错误。
  @Test
  void should_reject_bad_batches() {
    List<Long> tooMany = LongStream.rangeClosed(1, 101).boxed().toList();
    assertThatThrownBy(() -> handler.handle(RemoveItemsCommand.of(tooMany)))
        .isInstanceOf(InvalidCollectionRequestException.class);
    assertThatThrownBy(() -> handler.handle(RemoveItemsCommand.of(List.of())))
        .isInstanceOf(InvalidCollectionRequestException.class);
    assertThatThrownBy(() -> handler.handle(RemoveItemsCommand.of(List.of(0L))))
        .isInstanceOf(InvalidCollectionRequestException.class);
    verifyNoInteractions(library);
  }
}
