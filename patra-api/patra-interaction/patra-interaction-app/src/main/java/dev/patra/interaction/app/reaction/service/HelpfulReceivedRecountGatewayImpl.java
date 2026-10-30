package dev.patra.interaction.app.reaction.service;

import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// reaction 登记的重算：每个用户的获赞 = 以他为受益人的明细条数（匿名的不计）。写方不拿用户级锁，由仓储锁住用户的
/// 统计行再数。
@Component
@RequiredArgsConstructor
public class HelpfulReceivedRecountGatewayImpl implements StatsRecountGateway<Long> {

  private final HelpfulRepository helpful;

  /// 计数名。
  ///
  /// @return `reaction.helpful_received`
  @Override
  public String counter() {
    return "reaction.helpful_received";
  }

  /// 获赞与明细不一致的用户。
  ///
  /// @return 用户 ID
  @Override
  public List<Long> findDrifted() {
    return helpful.findReceivedDrift();
  }

  /// 锁住用户统计行后按明细重数。
  ///
  /// @param userId 用户
  /// @return 改了才有值
  @Override
  public Optional<StatsCorrection> recount(Long userId) {
    return helpful.recountReceived(userId);
  }
}
