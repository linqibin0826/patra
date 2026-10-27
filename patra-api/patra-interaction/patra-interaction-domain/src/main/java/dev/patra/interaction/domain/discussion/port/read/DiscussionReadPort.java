package dev.patra.interaction.domain.discussion.port.read;

import dev.patra.interaction.domain.discussion.model.enums.ThreadSort;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.ThreadCursor;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 讨论的读端：只查 `discussion` schema。每条 {@link CommentRecord} 都带父评论的作者与状态、有没有直接回复。
///
/// 楼内回复与整楼都按 `(created_at, comment_id)` 正序。
public interface DiscussionReadPort {

  /// 根评论的一页。
  ///
  /// @param subjectId 对象
  /// @param sort 排序：`ASC` 按 `(created_at, comment_id)` 升序，`LATEST` 降序
  /// @param after 从这一条之后开始，第一页为 `null`
  /// @param limit 最多几条
  /// @return 根评论（含占位）
  List<CommentRecord> findRoots(
      SubjectId subjectId, ThreadSort sort, ThreadCursor after, int limit);

  /// 每楼最早的若干条回复。
  ///
  /// @param rootIds 根评论
  /// @param perThread 每楼最多几条
  /// @return 根评论 → 回复（正序）；没有回复的根不在结果里
  Map<Long, List<CommentRecord>> findReplyPreviews(Collection<Long> rootIds, int perThread);

  /// 楼内回复的一页。
  ///
  /// @param rootId 根评论
  /// @param after 从这一条之后开始，第一页为 `null`
  /// @param limit 最多几条
  /// @return 回复（正序）
  List<CommentRecord> findReplies(long rootId, ThreadCursor after, int limit);

  /// 整楼。
  ///
  /// @param rootId 根评论
  /// @return 根评论在前，回复正序
  List<CommentRecord> findThread(long rootId);

  /// 按 ID 取一条。
  ///
  /// @param commentId 评论
  /// @return 评论；不存在为空
  Optional<CommentRecord> findById(long commentId);

  /// 从根评论到它的祖先链（含占位）。
  ///
  /// @param commentId 评论
  /// @return 根在前、它在最后；不存在为空列表
  List<CommentRecord> findAncestry(long commentId);

  /// 修改历史里的旧版本。
  ///
  /// @param commentId 评论
  /// @return 最新的在前
  List<RevisionRecord> findRevisions(long commentId);

  /// 本讨论最近发言的人：正常评论的作者，按各自最近一条评论倒序。
  ///
  /// @param subjectId 对象
  /// @param excludedUserIds 要排除的用户
  /// @param limit 最多几位
  /// @return 用户 ID
  List<Long> findRecentParticipants(
      SubjectId subjectId, Collection<Long> excludedUserIds, int limit);
}
