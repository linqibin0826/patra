package dev.patra.interaction.app.discussion.service;

import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.gateway.StatsRecountGateway;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// discussion 登记的重算：每个对象的未删除评论总数（文献的讨论数、经验的回复数）。发根评论不拿楼锁，由仓储锁住对象
/// 统计行再数。
@Component
@RequiredArgsConstructor
public class CommentCountRecountGatewayImpl implements StatsRecountGateway<SubjectId> {

  private final CommentRepository comments;

  /// 计数名。
  ///
  /// @return `discussion.comment_count`
  @Override
  public String counter() {
    return "discussion.comment_count";
  }

  /// 评论总数与明细不一致的对象。
  ///
  /// @return 对象
  @Override
  public List<SubjectId> findDrifted() {
    return comments.findCommentCountDrift();
  }

  /// 锁住对象统计行后按明细重数。
  ///
  /// @param subjectId 对象
  /// @return 改了才有值
  @Override
  public Optional<StatsCorrection> recount(SubjectId subjectId) {
    return comments.recountCommentCount(subjectId);
  }
}
