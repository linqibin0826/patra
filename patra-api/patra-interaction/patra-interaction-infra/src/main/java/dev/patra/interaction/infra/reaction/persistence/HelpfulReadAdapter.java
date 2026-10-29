package dev.patra.interaction.infra.reaction.persistence;

import dev.patra.interaction.domain.reaction.port.read.HelpfulReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `HelpfulReadPort` 的原生 SQL 实现：读 `reaction.user_stats`。
@Component
@RequiredArgsConstructor
public class HelpfulReadAdapter implements HelpfulReadPort {

  private final JdbcClient jdbc;

  /// 按主键读获赞，没有行为 0。
  ///
  /// @param userId 用户
  /// @return 获赞
  @Override
  public long helpfulReceived(long userId) {
    return jdbc.sql("SELECT helpful_received FROM reaction.user_stats WHERE user_id = :userId")
        .param("userId", userId)
        .query(Long.class)
        .optional()
        .orElse(0L);
  }
}
