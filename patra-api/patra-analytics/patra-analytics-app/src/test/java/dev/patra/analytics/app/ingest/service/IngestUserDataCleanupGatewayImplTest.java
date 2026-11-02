package dev.patra.analytics.app.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.analytics.domain.ingest.port.store.EventStorePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestUserDataCleanupGatewayImplTest {

  private final EventStorePort store = mock(EventStorePort.class);
  private final IngestUserDataCleanupGatewayImpl gateway =
      new IngestUserDataCleanupGatewayImpl(store);

  @Test
  void finds_users_from_store() {
    when(store.findUsersAfter(4L, 100)).thenReturn(List.of(5L, 9L));

    assertThat(gateway.subdomain()).isEqualTo("ingest");
    assertThat(gateway.findUsersAfter(4L, 100)).containsExactly(5L, 9L);
  }

  @Test
  void deletes_whole_batch_in_one_call() {
    assertThat(gateway.deleteAllOf(List.of(5L, 9L))).isEqualTo(2);
    verify(store).deleteUsers(List.of(5L, 9L));
  }

  @Test
  void empty_batch_deletes_nothing() {
    assertThat(gateway.deleteAllOf(List.of())).isZero();
    verify(store, never()).deleteUsers(anyList());
  }
}
