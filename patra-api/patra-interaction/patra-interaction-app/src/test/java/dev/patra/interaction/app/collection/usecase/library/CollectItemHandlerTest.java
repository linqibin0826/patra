package dev.patra.interaction.app.collection.usecase.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.RecordingTransactions;
import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import dev.patra.interaction.domain.collection.exception.LibraryFullException;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 收藏：已在库里直接返回（对象失效也不报错），否则先经 subject 解析（事务外），再锁住本人、查上限、插入。
class CollectItemHandlerTest {

  private static final long U = 1001L;
  private static final SubjectType PUB = SubjectType.PUBLICATION;
  private static final SubjectId S1 = SubjectId.of(11L);
  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

  private final LibraryRepository library = mock(LibraryRepository.class);
  private final SubjectGateway gateway = mock(SubjectGateway.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final List<String> events = new ArrayList<>();
  private CollectItemHandler handler;

  @BeforeEach
  void setUp() {
    when(currentUser.require()).thenReturn(CurrentUser.of(U, 1L, AccountType.USER, ClientType.WEB));
    when(subjects.findIdsByKeys(PUB, List.of("100"))).thenReturn(Map.of());
    handler =
        new CollectItemHandler(
            library,
            gateway,
            subjects,
            currentUser,
            new RecordingTransactions(events),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 新收藏：解析拿到对象、库里没有、未满 → 插入。
  @Test
  void should_collect_new_item() {
    when(gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT)).thenReturn(S1);
    when(library.findItem(U, S1)).thenReturn(Optional.empty());
    when(library.countItems(U)).thenReturn(0);
    when(library.insertItem(U, S1, NOW)).thenReturn(true);

    assertThat(handler.handle(CollectItemCommand.of("publication", "100")))
        .isEqualTo(CollectResult.of(true, S1));
    verify(library).lockOwner(U);
  }

  /// 对象已失效但本人库里已有这一条：重复收藏不报错、不经 subject 解析。
  @Test
  void should_return_existing_item_when_subject_became_unavailable() {
    when(subjects.findIdsByKeys(PUB, List.of("100"))).thenReturn(Map.of("100", S1));
    when(library.findItem(U, S1)).thenReturn(Optional.of(LibraryEntry.of(S1, null, NOW)));

    assertThat(handler.handle(CollectItemCommand.of("publication", "100")))
        .isEqualTo(CollectResult.of(false, S1));
    verifyNoInteractions(gateway);
    verify(library, never()).insertItem(anyLong(), any(), any());
  }

  /// 库满 20,000 条时新收藏被拒。
  @Test
  void should_reject_when_library_full() {
    when(gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT)).thenReturn(S1);
    when(library.findItem(U, S1)).thenReturn(Optional.empty());
    when(library.countItems(U)).thenReturn(20_000);

    assertThatThrownBy(() -> handler.handle(CollectItemCommand.of("publication", "100")))
        .isInstanceOf(LibraryFullException.class);
    verify(library, never()).insertItem(anyLong(), any(), any());
  }

  /// 锁内发现已经有了（并发的另一个请求先插了）：不查上限，按已有返回。
  @Test
  void should_not_check_limit_for_existing_item() {
    when(subjects.findIdsByKeys(PUB, List.of("100"))).thenReturn(Map.of("100", S1));
    when(gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT)).thenReturn(S1);
    when(library.findItem(U, S1))
        .thenReturn(Optional.empty(), Optional.of(LibraryEntry.of(S1, null, NOW)));
    when(library.countItems(U)).thenReturn(20_000);

    assertThat(handler.handle(CollectItemCommand.of("publication", "100")))
        .isEqualTo(CollectResult.of(false, S1));
  }

  /// 未知类型。
  @Test
  void should_reject_unknown_type() {
    assertThatThrownBy(() -> handler.handle(CollectItemCommand.of("unknown", "100")))
        .isInstanceOf(InvalidCollectionRequestException.class);
    verifyNoInteractions(gateway);
  }

  /// 解析在事务外。
  @Test
  void should_resolve_outside_transaction() {
    doAnswer(
            inv -> {
              events.add("resolve");
              return S1;
            })
        .when(gateway)
        .resolveForInteraction(PUB, "100", SubjectCapability.COLLECT);
    when(library.findItem(U, S1)).thenReturn(Optional.empty());
    when(library.insertItem(U, S1, NOW)).thenReturn(true);

    handler.handle(CollectItemCommand.of("publication", "100"));

    assertThat(events)
        .containsExactly("resolve", RecordingTransactions.BEGIN, RecordingTransactions.END);
  }
}
