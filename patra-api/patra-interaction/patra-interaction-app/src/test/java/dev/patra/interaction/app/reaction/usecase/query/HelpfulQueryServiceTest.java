package dev.patra.interaction.app.reaction.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.reaction.port.read.HelpfulReadPort;
import org.junit.jupiter.api.Test;

/// 获赞：按用户 ID 读 reaction 的统计，不要会话。
class HelpfulQueryServiceTest {

  private final HelpfulReadPort reads = mock(HelpfulReadPort.class);
  private final HelpfulQueryService service = new HelpfulQueryService(reads);

  @Test
  void should_return_helpful_received_from_read_port() {
    when(reads.helpfulReceived(7L)).thenReturn(3L);

    assertThat(service.helpfulReceived(7L)).isEqualTo(3L);
  }
}
