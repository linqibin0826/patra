package dev.patra.interaction.app.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.port.repository.PublishQuotaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 底座登记的注销清理动作：删掉这个人的发表计数。
class SubjectUserDataCleanupGatewayImplTest {

  private final PublishQuotaRepository quotas = mock(PublishQuotaRepository.class);
  private final SubjectUserDataCleanupGatewayImpl cleanup =
      new SubjectUserDataCleanupGatewayImpl(quotas);

  @Test
  void should_delegate_to_publish_quotas() {
    when(quotas.findUsersAfter(0L, 100)).thenReturn(List.of(3L));
    when(quotas.deleteAllOf(3L)).thenReturn(2);

    assertThat(cleanup.subdomain()).isEqualTo("subject");
    assertThat(cleanup.findUsersAfter(0L, 100)).containsExactly(3L);
    assertThat(cleanup.deleteAllOf(3L)).isEqualTo(2);
  }
}
