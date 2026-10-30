package dev.patra.interaction.app.discussion.usecase.query;

import dev.patra.interaction.app.discussion.service.CommentViews;
import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import dev.patra.interaction.domain.discussion.model.enums.ThreadSort;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.read.ThreadStats;
import dev.patra.interaction.domain.discussion.model.vo.ThreadCursor;
import dev.patra.interaction.domain.discussion.policy.DiscussionLimits;
import dev.patra.interaction.domain.discussion.policy.MentionSyntax;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.RankCursor;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 讨论的读（spec 6.1）：登录可选，已登录时带访客拿「这条是我发的」「我点过有帮助没有」与主页可见性。对象还没登记
/// （没人讨论过）回空页；对象已失效 404。编辑原文要登录、只给作者。列表每楼带楼统计、每页带对象评论总数（PAP-132）。
@Component
@RequiredArgsConstructor
public class DiscussionQueryService {

  private final DiscussionSubjects subjects;
  private final DiscussionReadPort reads;
  private final CommentViews views;
  private final CurrentUserPort currentUser;

  /// 讨论列表的一页：根评论，每楼带前 3 条回复。
  ///
  /// @param type 对外类型名
  /// @param key 对外 key
  /// @param sort `asc`（默认）/ `latest` / `helpful`
  /// @param cursor 游标，可空
  /// @param size 每页几楼，可空（默认 20）
  /// @return 一页
  public ThreadPageView threads(String type, String key, String sort, String cursor, Integer size) {
    SubjectType subjectType = subjects.requireDiscussable(type, key);
    ThreadSort threadSort = ThreadSort.fromParam(sort);
    boolean helpful = threadSort == ThreadSort.HELPFUL;
    ThreadCursor after = helpful ? null : ThreadCursor.parse(cursor).orElse(null);
    RankCursor rankAfter = helpful ? rankCursor(cursor) : null;
    int pageSize = pageSize(size, DiscussionLimits.THREAD_PAGE_DEFAULT);
    Optional<SubjectView> found = subjects.findForRead(subjectType, key);
    if (found.isEmpty()) {
      return ThreadPageView.of(List.of(), null, 0);
    }
    SubjectView subject = found.get();
    List<CommentRecord> fetched =
        helpful
            ? reads.findRootsByHelpful(subject.subjectId(), rankAfter, pageSize + 1)
            : reads.findRoots(subject.subjectId(), threadSort, after, pageSize + 1);
    List<CommentRecord> roots = fetched.subList(0, Math.min(pageSize, fetched.size()));
    Map<Long, List<CommentRecord>> previews =
        roots.isEmpty()
            ? Map.of()
            : reads.findReplyPreviews(
                roots.stream().map(CommentRecord::commentId).toList(),
                DiscussionLimits.PREVIEW_REPLIES + 1);
    List<CommentRecord> records = new ArrayList<>();
    for (CommentRecord root : roots) {
      records.add(root);
      records.addAll(shown(previews.getOrDefault(root.commentId(), List.of())));
    }
    Map<Long, CommentView> byId = assemble(records, subject);
    Map<Long, ThreadStats> stats =
        roots.isEmpty()
            ? Map.of()
            : reads.findThreadStats(roots.stream().map(CommentRecord::commentId).toList());
    List<ThreadView> threads =
        roots.stream()
            .map(
                root -> {
                  List<CommentRecord> preview = previews.getOrDefault(root.commentId(), List.of());
                  List<CommentRecord> shown = shown(preview);
                  boolean more = preview.size() > shown.size();
                  return thread(
                      byId.get(root.commentId()),
                      shown.stream().map(r -> byId.get(r.commentId())).toList(),
                      more ? cursorOf(shown.getLast()) : null,
                      stats.get(root.commentId()));
                })
            .toList();
    String next = null;
    if (fetched.size() > pageSize) {
      CommentRecord last = roots.getLast();
      next =
          helpful
              ? RankCursor.of(last.helpfulCount(), last.createdAt(), last.commentId()).format()
              : cursorOf(last);
    }
    return ThreadPageView.of(threads, next, reads.countComments(subject.subjectId()));
  }

  /// 单楼视图：目标所在的整楼。
  ///
  /// @param type 对外类型名
  /// @param key 对外 key
  /// @param commentId 目标评论
  /// @return 单楼
  /// @throws CommentNotFoundException 目标不存在、已整条去掉或不在这个对象下
  public SingleThreadView thread(String type, String key, long commentId) {
    SubjectType subjectType = subjects.requireDiscussable(type, key);
    SubjectView subject =
        subjects.findForRead(subjectType, key).orElseThrow(CommentNotFoundException::new);
    CommentRecord target =
        reads
            .findById(commentId)
            .filter(r -> r.subjectId().equals(subject.subjectId()))
            .orElseThrow(CommentNotFoundException::new);
    List<CommentRecord> records = reads.findThread(target.rootId());
    Map<Long, CommentView> byId = assemble(records, subject);
    CommentView root = byId.get(records.getFirst().commentId());
    List<CommentView> replies =
        records.subList(1, records.size()).stream().map(r -> byId.get(r.commentId())).toList();
    ThreadStats stats = reads.findThreadStats(List.of(root.commentId())).get(root.commentId());
    return SingleThreadView.of(commentId, thread(root, replies, null, stats));
  }

  /// 楼内回复的一页（「展开更多回复」）。
  ///
  /// @param rootId 根评论
  /// @param cursor 游标，可空
  /// @param size 每页几条，可空（默认 50）
  /// @return 一页
  /// @throws CommentNotFoundException 不存在或不是根评论
  public CommentPageView replies(long rootId, String cursor, Integer size) {
    ThreadCursor after = ThreadCursor.parse(cursor).orElse(null);
    int pageSize = pageSize(size, DiscussionLimits.REPLY_PAGE_DEFAULT);
    CommentRecord root =
        reads
            .findById(rootId)
            .filter(CommentRecord::isRoot)
            .orElseThrow(CommentNotFoundException::new);
    SubjectView subject = subjects.requireActive(root.subjectId());
    List<CommentRecord> fetched = reads.findReplies(rootId, after, pageSize + 1);
    List<CommentRecord> page = fetched.subList(0, Math.min(pageSize, fetched.size()));
    Map<Long, CommentView> byId = assemble(page, subject);
    String next = fetched.size() > pageSize ? cursorOf(page.getLast()) : null;
    return CommentPageView.of(page.stream().map(r -> byId.get(r.commentId())).toList(), next);
  }

  /// 对话链。
  ///
  /// @param commentId 目标评论
  /// @return 从根到它
  /// @throws CommentNotFoundException 不存在
  public ConversationView conversation(long commentId) {
    CommentRecord target = reads.findById(commentId).orElseThrow(CommentNotFoundException::new);
    SubjectView subject = subjects.requireActive(target.subjectId());
    List<CommentRecord> chain = reads.findAncestry(commentId);
    Map<Long, CommentView> byId = assemble(chain, subject);
    return ConversationView.of(
        commentId, chain.stream().map(r -> byId.get(r.commentId())).toList());
  }

  /// 修改历史（公开）。
  ///
  /// @param commentId 评论
  /// @return 版本与提及
  /// @throws CommentNotFoundException 不存在或是占位
  public RevisionsView revisions(long commentId) {
    CommentRecord target = requireActiveComment(commentId);
    subjects.requireActive(target.subjectId());
    List<RevisionRecord> versions = new ArrayList<>();
    versions.add(
        RevisionRecord.of(
            target.body(), target.revisedAt() != null ? target.revisedAt() : target.createdAt()));
    versions.addAll(reads.findRevisions(commentId));
    Set<Long> mentioned = new LinkedHashSet<>();
    for (RevisionRecord version : versions) {
      mentioned.addAll(MentionSyntax.mentionedIds(version.body()));
    }
    List<MentionView> mentions =
        mentioned.isEmpty() ? List.of() : views.mentions(List.copyOf(mentioned), viewer());
    return RevisionsView.of(versions, mentions);
  }

  /// 编辑原文（作者本人）：标记换回 `@当前用户名`，已注销的换成 `@已注销用户`（spec 5.4.5）。
  ///
  /// @param commentId 评论
  /// @return 编辑原文
  /// @throws CommentNotFoundException 不存在或是占位
  /// @throws CommentNotOwnedException 不是作者
  public String source(long commentId) {
    long editor = currentUser.require().userId();
    CommentRecord target = requireActiveComment(commentId);
    if (target.authorId() != editor) {
      throw new CommentNotOwnedException();
    }
    subjects.requireActive(target.subjectId());
    List<Long> ids = MentionSyntax.mentionedIds(target.body());
    Map<Long, UserCard> cards = ids.isEmpty() ? Map.of() : views.cards(ids, editor);
    return MentionSyntax.toSource(
        target.body(),
        id -> {
          UserCard card = cards.get(id);
          return card == null || card.state() == AccountState.DELETED ? null : card.username();
        });
  }

  /// 正常（不是占位）的评论。
  ///
  /// @param commentId 评论
  /// @return 评论
  /// @throws CommentNotFoundException 不存在或是占位
  private CommentRecord requireActiveComment(long commentId) {
    return reads
        .findById(commentId)
        .filter(r -> !r.isDeleted())
        .orElseThrow(CommentNotFoundException::new);
  }

  /// 一次拼好一批评论的视图。
  ///
  /// @param records 评论
  /// @param subject 对象
  /// @return 评论 ID → 视图
  private Map<Long, CommentView> assemble(List<CommentRecord> records, SubjectView subject) {
    Long viewer = viewer();
    Set<Long> marked =
        viewer == null || records.isEmpty()
            ? Set.of()
            : reads.findHelpfulMarked(
                viewer, records.stream().map(CommentRecord::commentId).toList());
    return views.assemble(records, subject, viewer, Map.of(), marked).stream()
        .collect(Collectors.toMap(CommentView::commentId, Function.identity()));
  }

  /// 一楼的视图。
  ///
  /// @param root 根评论
  /// @param replies 给出的回复
  /// @param repliesCursor 取剩下回复的游标；没有更多时为 `null`
  /// @param stats 楼统计；没有统计行时为 `null`（视为没有回复）
  /// @return 一楼
  private static ThreadView thread(
      CommentView root, List<CommentView> replies, String repliesCursor, ThreadStats stats) {
    return ThreadView.builder()
        .root(root)
        .replies(replies)
        .hasMoreReplies(repliesCursor != null)
        .repliesCursor(repliesCursor)
        .replyCount(stats == null ? 0 : stats.replyCount())
        .lastReplyAt(stats == null ? null : stats.lastReplyAt())
        .build();
  }

  /// 「最有帮助」的游标：没传为 `null`，格式不对 422。
  ///
  /// @param raw 原文，可空
  /// @return 游标
  /// @throws InvalidDiscussionRequestException 格式不对
  private static RankCursor rankCursor(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return RankCursor.parse(raw).orElseThrow(() -> new InvalidDiscussionRequestException("游标不合法"));
  }

  /// 预览里要展示的回复：前 3 条。
  ///
  /// @param preview 预览（最多 4 条）
  /// @return 前 3 条
  private static List<CommentRecord> shown(List<CommentRecord> preview) {
    return preview.subList(0, Math.min(DiscussionLimits.PREVIEW_REPLIES, preview.size()));
  }

  /// 一条评论之后的游标。
  ///
  /// @param record 评论
  /// @return 游标
  private static String cursorOf(CommentRecord record) {
    return ThreadCursor.of(record.createdAt(), record.commentId()).format();
  }

  /// 每页条数：不传用默认值，否则 1 ~ 50。
  ///
  /// @param size 请求里的条数，可空
  /// @param defaultSize 默认值
  /// @return 条数
  /// @throws InvalidDiscussionRequestException 越界
  private static int pageSize(Integer size, int defaultSize) {
    if (size == null) {
      return defaultSize;
    }
    if (size < 1 || size > DiscussionLimits.PAGE_MAX) {
      throw new InvalidDiscussionRequestException("每页 1 ~ " + DiscussionLimits.PAGE_MAX + " 条");
    }
    return size;
  }

  /// 访客的用户 ID，匿名为 `null`。
  ///
  /// @return 用户 ID
  private Long viewer() {
    return currentUser.current().map(CurrentUser::userId).orElse(null);
  }
}
