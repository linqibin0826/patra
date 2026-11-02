package dev.patra.analytics.app.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.history.port.repository.ReadingHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class HistoryUserDataCleanupGatewayImplTest {

  private final ReadingHistoryRepository history = mock(ReadingHistoryRepository.class);
  private final HistoryUserDataCleanupGatewayImpl gateway =
      new HistoryUserDataCleanupGatewayImpl(history, TransactionOperations.withoutTransaction());

  @Test
  void finds_users_from_repository() {
    when(history.findUsersAfter(3L, 100)).thenReturn(List.of(5L, 9L));

    assertThat(gateway.subdomain()).isEqualTo("history");
    assertThat(gateway.findUsersAfter(3L, 100)).containsExactly(5L, 9L);
  }

  @Test
  void deletes_batch_in_one_call() {
    assertThat(gateway.deleteAllOf(List.of(5L, 9L))).isEqualTo(2);
    verify(history).deleteUsers(List.of(5L, 9L));
  }

  @Test
  void empty_batch_deletes_nothing() {
    assertThat(gateway.deleteAllOf(List.of())).isZero();
    verify(history, never()).deleteUsers(anyList());
  }
}
