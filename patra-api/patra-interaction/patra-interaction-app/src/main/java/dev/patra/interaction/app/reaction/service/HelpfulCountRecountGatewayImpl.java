package dev.patra.interaction.app.reaction.service;

import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// reaction 登记的重算：每个目标的有帮助数 = 它的明细条数。先拿点、取消、删除联动共用的目标锁，锁内没有并发写。
/// 不判断目标是否还在：删除联动已在删除事务里删掉统计行，万一残留置 0 对读方没有影响。
@Component
@RequiredArgsConstructor
public class HelpfulCountRecountGatewayImpl implements StatsRecountGateway<HelpfulTarget> {

  private final HelpfulRepository helpful;

  /// 计数名。
  ///
  /// @return `reaction.helpful_count`
  @Override
  public String counter() {
    return "reaction.helpful_count";
  }

  /// 有帮助数与明细不一致的目标。
  ///
  /// @return 目标
  @Override
  public List<HelpfulTarget> findDrifted() {
    return helpful.findHelpfulCountDrift();
  }

  /// 拿目标锁后按明细重数。
  ///
  /// @param target 目标
  /// @return 改了才有值
  @Override
  public Optional<StatsCorrection> recount(HelpfulTarget target) {
    helpful.lockTarget(target);
    return helpful.recountHelpfulCount(target);
  }
}
