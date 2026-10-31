package dev.patra.notification.app.kernel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.kernel.port.repository.PresenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/// 在站信号：记下时刻取注入的时钟，读是批量的。
class PresenceGatewayImplTest {

  private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

  private final PresenceRepository presence = mock(PresenceRepository.class);
  private final PresenceGatewayImpl gateway =
      new PresenceGatewayImpl(
          presence, TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));

  /// 拉未读数、推送心跳时记一次在站：时刻是时钟的「现在」。
  @Test
  void should_mark_seen_at_clock_instant() {
    gateway.markSeen(7L);

    verify(presence).touch(7L, NOW);
  }

  /// 批量读最后出现的时刻。
  @Test
  void should_read_last_seen() {
    when(presence.find(List.of(7L, 8L))).thenReturn(Map.of(7L, NOW));

    assertThat(gateway.lastSeen(List.of(7L, 8L))).containsExactly(Map.entry(7L, NOW));
  }

  /// 没有人要查时不查库。
  @Test
  void should_return_empty_for_no_users() {
    assertThat(gateway.lastSeen(List.of())).isEmpty();
    verifyNoInteractions(presence);
  }
}
