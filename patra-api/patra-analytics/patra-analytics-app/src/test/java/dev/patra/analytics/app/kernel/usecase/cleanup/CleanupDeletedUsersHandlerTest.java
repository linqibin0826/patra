package dev.patra.analytics.app.kernel.usecase.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.analytics.domain.kernel.model.enums.AccountState;
import dev.patra.analytics.domain.kernel.port.gateway.UserDataCleanupGateway;
import dev.patra.analytics.domain.kernel.port.identity.AccountStatePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 注销清理的公共流程（照 notification，但一批用户一次删）：游标分批、一批回查一次 identity、只删 DELETED。
class CleanupDeletedUsersHandlerTest {

  private final UserDataCleanupGateway ingest = mock(UserDataCleanupGateway.class);
  private final AccountStatePort accounts = mock(AccountStatePort.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private CleanupDeletedUsersHandler handler;

  @BeforeEach
  void setUp() {
    when(ingest.subdomain()).thenReturn("ingest");
    when(ingest.deleteAllOf(anyList())).thenAnswer(inv -> inv.<List<Long>>getArgument(0).size());
    handler = new CleanupDeletedUsersHandler(List.of(ingest), accounts, meters);
  }

  @Test
  void deletes_only_deleted_users() {
    when(ingest.findUsersAfter(0L, 100)).thenReturn(List.of(1L, 2L, 3L));
    when(accounts.findStates(List.of(1L, 2L, 3L)))
        .thenReturn(
            Map.of(1L, AccountState.DELETED, 2L, AccountState.ACTIVE, 3L, AccountState.DELETED));

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    verify(ingest).deleteAllOf(List.of(1L, 3L));
    assertThat(result).isEqualTo(CleanupResult.of(3, 2, 0));
    assertThat(meters.get("analytics.cleanup.users").tag("outcome", "deleted").counter().count())
        .isEqualTo(2);
  }

  @Test
  void omitted_users_are_not_deleted() {
    when(ingest.findUsersAfter(0L, 100)).thenReturn(List.of(1L, 2L));
    when(accounts.findStates(List.of(1L, 2L))).thenReturn(Map.of(1L, AccountState.DELETED));

    handler.handle(CleanupDeletedUsersCommand.of());

    verify(ingest).deleteAllOf(List.of(1L));
  }

  @Test
  void identity_unavailable_skips_batch_and_counts() {
    List<Long> first = LongStream.rangeClosed(1, 100).boxed().toList();
    when(ingest.findUsersAfter(0L, 100)).thenReturn(first);
    when(ingest.findUsersAfter(100L, 100)).thenReturn(List.of(101L));
    when(accounts.findStates(first))
        .thenThrow(new IdentityUnavailableException(new RuntimeException("down")));
    when(accounts.findStates(List.of(101L))).thenReturn(Map.of(101L, AccountState.DELETED));

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result).isEqualTo(CleanupResult.of(101, 1, 1));
    verify(ingest).deleteAllOf(List.of(101L));
    assertThat(meters.get("analytics.cleanup.users").tag("outcome", "skipped").counter().count())
        .isEqualTo(100);
  }

  @Test
  void cursor_advances_across_batches() {
    List<Long> all = LongStream.rangeClosed(1, 250).boxed().toList();
    when(ingest.findUsersAfter(anyLong(), anyInt()))
        .thenAnswer(
            inv -> {
              long after = inv.getArgument(0);
              int limit = inv.getArgument(1);
              return all.stream().filter(id -> id > after).limit(limit).toList();
            });
    when(accounts.findStates(anyList()))
        .thenAnswer(
            inv ->
                inv.<List<Long>>getArgument(0).stream()
                    .collect(Collectors.toMap(Function.identity(), id -> AccountState.DELETED)));

    CleanupResult result = handler.handle(CleanupDeletedUsersCommand.of());

    assertThat(result).isEqualTo(CleanupResult.of(250, 250, 0));
    verify(accounts, times(3)).findStates(anyList());
  }

  @Test
  void gateway_deletes_in_one_call_per_batch() {
    when(ingest.findUsersAfter(0L, 100)).thenReturn(List.of(1L, 2L));
    when(accounts.findStates(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, AccountState.DELETED, 2L, AccountState.DELETED));

    handler.handle(CleanupDeletedUsersCommand.of());

    verify(ingest, times(1)).deleteAllOf(anyList());
  }

  @Test
  void nothing_deleted_when_no_deleted_users() {
    when(ingest.findUsersAfter(0L, 100)).thenReturn(List.of(1L));
    when(accounts.findStates(List.of(1L))).thenReturn(Map.of(1L, AccountState.ACTIVE));

    assertThat(handler.handle(CleanupDeletedUsersCommand.of()))
        .isEqualTo(CleanupResult.of(1, 0, 0));
    verify(ingest, never()).deleteAllOf(anyList());
  }
}
