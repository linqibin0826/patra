package dev.patra.interaction.domain.discussion.port.repository;

import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// 评论的写端（`discussion.comment`、`discussion.comment_revision`）与随评论同事务维护的楼统计、对象统计
/// （`discussion.thread_stats`、`discussion.subject_stats`）。写方法不开事务，由 app 层的事务包住。
public interface CommentRepository {

  /// 拿一楼的事务级 advisory lock `discussion.thread:{rootId}`：同一楼的回复与删除在锁上排队。
  ///
  /// @param rootId 根评论
  void lockThread(long rootId);

  /// 按 ID 取。
  ///
  /// @param commentId 评论
  /// @return 评论；不存在为空
  Optional<CommentAggregate> find(long commentId);

  /// 按 ID 取并锁行（`FOR UPDATE`，删除用）。
  ///
  /// @param commentId 评论
  /// @return 评论；不存在为空
  Optional<CommentAggregate> findForUpdate(long commentId);

  /// 按 ID 取并锁行（`FOR NO KEY UPDATE`，编辑用：不挡往它下面插回复时外键要的锁）。
  ///
  /// @param commentId 评论
  /// @return 评论；不存在为空
  Optional<CommentAggregate> findForEdit(long commentId);

  /// 有没有直接回复（`parent_id` 是它的评论）。
  ///
  /// @param commentId 评论
  /// @return 有时为 `true`
  boolean hasReplies(long commentId);

  /// 插入一条新评论（ACTIVE），ID 由这里生成；根评论的 `root_id` 是新 ID 本身。
  ///
  /// @param comment 新评论
  /// @return 新评论 ID
  long insert(NewComment comment);

  /// 改正文：只写 `body`、`mentioned_user_ids`、`revised_at`。
  ///
  /// @param comment 改完的评论
  void updateBody(CommentAggregate comment);

  /// 往修改历史里追加一个旧版本，版本号是该评论已有最大值 + 1。
  ///
  /// @param commentId 评论
  /// @param revision 旧版本
  void appendRevision(long commentId, RevisionRecord revision);

  /// 变占位：清正文与提及、记删除时刻，并删掉它的修改历史。
  ///
  /// @param commentId 评论
  /// @param now 删除时刻
  void markDeleted(long commentId, Instant now);

  /// 整条删除（修改历史随外键级联删除）。
  ///
  /// @param commentId 评论
  void delete(long commentId);

  /// 楼里多了一条未删除的回复：回复数 +1，最后回复时间取与它较晚的那个（后拿到楼锁的回复时间可能更早）。
  ///
  /// @param rootId 根评论
  /// @param createdAt 新回复的发表时刻
  void recordReply(long rootId, Instant createdAt);

  /// 楼里少了一条未删除的回复：先锁住楼统计行，再用新语句把回复数 −1、最后回复时间重取为剩余未删除回复中最新的
  /// （没有剩余时为空）。调用前这条回复已经变占位或被去掉。
  ///
  /// @param rootId 根评论
  void removeReply(long rootId);

  /// 根评论整条去掉时删它的楼统计行。
  ///
  /// @param rootId 根评论
  void dropThreadStats(long rootId);

  /// 对象下多了一条未删除的评论（根评论或回复）。
  ///
  /// @param subjectId 对象
  void incrementCommentCount(SubjectId subjectId);

  /// 对象下少了一条未删除的评论。
  ///
  /// @param subjectId 对象
  void decrementCommentCount(SubjectId subjectId);

  /// 按明细重算第一步：未删除回复数或最后回复时间与楼统计行不一致的楼，以及根评论已不存在的统计行（任一边缺行按
  /// 0 / 空算）。不加锁。
  ///
  /// @return 根评论 ID
  List<Long> findThreadStatsDrift();

  /// 按明细重算第二步：调用方已拿楼锁（回复与删除都拿它，锁内没有并发写），按明细重取回复数与最后回复时间，不一致
  /// 才写；根评论已不存在就删行。须在调用方的事务里调用。
  ///
  /// @param rootId 根评论
  /// @return 改了才有值，前后值是回复数
  Optional<StatsCorrection> recountThreadStats(long rootId);

  /// 按明细重算第一步：未删除的评论数与对象统计行不一致的对象（任一边缺行按 0 算）。不加锁。
  ///
  /// @return 对象
  List<SubjectId> findCommentCountDrift();

  /// 按明细重算第二步：发根评论不拿楼锁，所以先建出统计行、`FOR UPDATE` 锁住，再用新语句数，不一致才写。须在调用方
  /// 的事务里调用。
  ///
  /// @param subjectId 对象
  /// @return 改了才有值
  Optional<StatsCorrection> recountCommentCount(SubjectId subjectId);
}
