package dev.patra.interaction.app.collection.service;

import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// collection 登记的重算：每个对象的收藏人数 = 它的文献库条目数。锁统计行即可（写方在用户锁之后才碰统计行，重算
/// 不拿用户锁，不会成环）。
@Component
@RequiredArgsConstructor
public class CollectorCountRecountGatewayImpl implements StatsRecountGateway<SubjectId> {

  private final LibraryRepository library;

  /// 计数名。
  ///
  /// @return `collection.collector_count`
  @Override
  public String counter() {
    return "collection.collector_count";
  }

  /// 收藏人数与文献库条目数不一致的对象。
  ///
  /// @return 对象
  @Override
  public List<SubjectId> findDrifted() {
    return library.findCollectorCountDrift();
  }

  /// 锁住统计行后按明细重数。
  ///
  /// @param subjectId 对象
  /// @return 改了才有值
  @Override
  public Optional<StatsCorrection> recount(SubjectId subjectId) {
    return library.recountCollectorCount(subjectId);
  }
}
