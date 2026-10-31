package dev.patra.notification.app.kernel.usecase.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.port.gateway.UserDataCleanupGateway;
import dev.patra.notification.domain.kernel.port.identity.AccountStatePort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 注销清理的公共流程（照 interaction）：合并各子域的候选用户，一批回查一次 identity，只删 DELETED 的人，每个
/// （用户，参与方）一个事务；identity 不可用跳过这一批继续。
class CleanupDeletedUsersHandlerTest {

  private final UserDataCleanupGateway p = mock(UserDataCleanupGateway.class);
  private final UserDataCleanupGateway q = mock(UserDataCleanupGateway.class);
  private final AccountStatePort accounts = mock(AccountStatePort.class);
  private final List<String> events = new ArrayList<>();
  private CleanupDeletedUsersHandler handler;

  @BeforeEach
  void setUp() {
    when(p.subdomain()).thenReturn("p");
    when(q.subdomain()).thenReturn("q");
    handler =
        new CleanupDeletedUsersHandler(List.of(p, q), accounts, new RecordingTransactions(events));
  }

  /// 两个参与方的候选有重叠：并起来去重，一批只调一次 identity。
  @Test
  void should_merge_candidates_across_participants() {
    when(p.findUsersAfter(0L, 100)).thenReturn(List.of(1L, 3L));
    when(q.findUsersAfter(0L, 100)).thenReturn(List.of(2L, 3L));
    when(accounts.findStates(List.of(1L, 2L, 3L))).thenReturn(Map.of());

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.scannedUsers()).isEqualTo(3);
    verify(accounts, times(1)).findStates(anyList());
    verify(p).findUsersAfter(3L, 100);
    verify(q).findUsersAfter(3L, 100);
  }

  /// 并集超过 100 个时取最小的 100 个，游标停在第 100 个，下一轮从那里接着取。
  @Test
  void should_take_smallest_hundred_when_participants_overflow() {
    List<Long> first = LongStream.rangeClosed(1, 100).boxed().toList();
    when(p.findUsersAfter(0L, 100)).thenReturn(first);
    when(q.findUsersAfter(0L, 100)).thenReturn(LongStream.rangeClosed(50, 149).boxed().toList());
    when(q.findUsersAfter(100L, 100)).thenReturn(LongStream.rangeClosed(101, 149).boxed().toList());
    when(accounts.findStates(anyList())).thenReturn(Map.of());

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result.scannedUsers()).isEqualTo(149);
    verify(accounts).findStates(first);
    verify(accounts).findStates(LongStream.rangeClosed(101, 149).boxed().toList());
  }

  /// 只删 DELETED；每个参与方各开一个事务；ACTIVE 与 identity 省略的都不删。
  @Test
  void should_delete_deleted_users_in_every_participant_each_in_own_transaction() {
    when(p.findUsersAfter(0L, 100)).thenReturn(List.of(1L, 2L, 3L));
    when(accounts.findStates(List.of(1L, 2L, 3L)))
        .thenReturn(Map.of(1L, AccountState.DELETED, 2L, AccountState.ACTIVE));
    when(p.deleteAllOf(1L))
        .thenAnswer(
            inv -> {
              events.add("p:1");
              return 10;
            });
    when(q.deleteAllOf(1L))
        .thenAnswer(
            inv -> {
              events.add("q:1");
              return 2;
            });

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result).isEqualTo(CleanupResult.of(3, 1, 12, 0));
    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "p:1",
            RecordingTransactions.END,
            RecordingTransactions.BEGIN,
            "q:1",
            RecordingTransactions.END);
    verify(p, never()).deleteAllOf(2L);
    verify(p, never()).deleteAllOf(3L);
  }

  /// identity 不可用：这一批计失败，游标照常往后走。
  @Test
  void should_skip_batch_when_identity_down_and_continue() {
    List<Long> first = LongStream.rangeClosed(1, 100).boxed().toList();
    when(p.findUsersAfter(0L, 100)).thenReturn(first);
    when(p.findUsersAfter(100L, 100)).thenReturn(List.of(101L));
    when(accounts.findStates(first))
        .thenThrow(new IdentityUnavailableException(new RuntimeException("down")));
    when(accounts.findStates(List.of(101L))).thenReturn(Map.of(101L, AccountState.DELETED));
    when(p.deleteAllOf(101L)).thenReturn(1);

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result).isEqualTo(CleanupResult.of(101, 1, 1, 1));
    verify(p, never()).deleteAllOf(1L);
  }

  /// 没有候选就什么都不做。
  @Test
  void should_do_nothing_without_candidates() {
    assertThat(handler.handle(CleanupDeletedUsersCommand.of()))
        .isEqualTo(CleanupResult.of(0, 0, 0, 0));
    verify(p, never()).deleteAllOf(anyLong());
  }
}
