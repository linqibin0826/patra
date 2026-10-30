package dev.patra.interaction.app.discussion.service;

import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// discussion 登记的重算：每楼的未删除回复数与最后回复时间。先拿回复与删除共用的楼锁，锁内没有并发写；根评论已不在
/// 的统计行删掉。
@Component
@RequiredArgsConstructor
public class ThreadStatsRecountGatewayImpl implements StatsRecountGateway<Long> {

  private final CommentRepository comments;

  /// 计数名。
  ///
  /// @return `discussion.thread_stats`
  @Override
  public String counter() {
    return "discussion.thread_stats";
  }

  /// 楼统计与明细不一致的楼。
  ///
  /// @return 根评论 ID
  @Override
  public List<Long> findDrifted() {
    return comments.findThreadStatsDrift();
  }

  /// 拿楼锁后按明细重取。
  ///
  /// @param rootId 根评论
  /// @return 改了才有值
  @Override
  public Optional<StatsCorrection> recount(Long rootId) {
    comments.lockThread(rootId);
    return comments.recountThreadStats(rootId);
  }
}
