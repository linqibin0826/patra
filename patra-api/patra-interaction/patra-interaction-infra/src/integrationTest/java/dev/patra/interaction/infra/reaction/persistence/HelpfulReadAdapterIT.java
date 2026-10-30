package dev.patra.interaction.infra.reaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.reaction.port.read.HelpfulReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 获赞读端口：有行读行，没有行为 0。
class HelpfulReadAdapterIT extends ReactionPersistenceITSupport {

  @Autowired private HelpfulReadPort reads;

  @Test
  void should_read_received_or_zero() {
    jdbcTemplate.update(
        "INSERT INTO reaction.user_stats (user_id, helpful_received) VALUES (9, 3)");

    assertThat(reads.helpfulReceived(9L)).isEqualTo(3L);
    assertThat(reads.helpfulReceived(10L)).isZero();
  }
}
