package dev.patra.notification.app.preference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/// preference 登记的注销清理：列有偏好行的人，删整行（令牌随之作废）。
class PreferenceUserDataCleanupGatewayImplTest {

  private final EmailPreferenceRepository preferences = mock(EmailPreferenceRepository.class);
  private final PreferenceUserDataCleanupGatewayImpl gateway =
      new PreferenceUserDataCleanupGatewayImpl(preferences);

  /// 日志里按子域名列出删掉的行数。
  @Test
  void subdomain_is_preference() {
    assertThat(gateway.subdomain()).isEqualTo("preference");
  }

  /// 游标与条数原样交给仓储。
  @Test
  void find_users_after_delegates_to_repository() {
    when(preferences.findUsersAfter(3L, 100)).thenReturn(List.of(5L, 9L));

    assertThat(gateway.findUsersAfter(3L, 100)).containsExactly(5L, 9L);
  }

  /// 删这个人的偏好行，返回行数。
  @Test
  void delete_all_of_removes_preference_row() {
    when(preferences.deleteByUserId(7L)).thenReturn(1);

    assertThat(gateway.deleteAllOf(7L)).isEqualTo(1);
  }
}
