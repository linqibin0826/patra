package dev.patra.notification.app.kernel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.kernel.port.repository.PresenceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 底座自己的注销清理：删在站记录。
class KernelUserDataCleanupGatewayImplTest {

  private final PresenceRepository presence = mock(PresenceRepository.class);
  private final KernelUserDataCleanupGatewayImpl gateway =
      new KernelUserDataCleanupGatewayImpl(presence);

  /// 日志里按子域名列出删掉的行数。
  @Test
  void subdomain_is_kernel() {
    assertThat(gateway.subdomain()).isEqualTo("kernel");
  }

  /// 游标与条数原样交给仓储。
  @Test
  void find_users_after_delegates_to_repository() {
    when(presence.findUsersAfter(3L, 100)).thenReturn(List.of(5L, 9L));

    assertThat(gateway.findUsersAfter(3L, 100)).containsExactly(5L, 9L);
  }

  /// 删这个人的在站记录。
  @Test
  void delete_all_of_removes_presence_row() {
    when(presence.deleteAllOf(7L)).thenReturn(1);

    assertThat(gateway.deleteAllOf(7L)).isEqualTo(1);
  }
}
