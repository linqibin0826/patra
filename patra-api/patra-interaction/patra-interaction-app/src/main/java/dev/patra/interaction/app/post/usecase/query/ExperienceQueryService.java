package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.app.post.service.ExperienceViews;
import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import dev.patra.interaction.domain.post.exception.PostNotFoundException;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.SummaryWindow;
import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.read.ExperienceSummary;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.policy.ExperienceRules;
import dev.patra.interaction.domain.post.policy.ExperienceSummaryRules;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 投稿经验的查询（spec 6.4），登录可选：期刊下的列表与汇总、经验详情、修改历史、用户主页的经验列表。
///
/// 期刊没有登记（还没人挂过经验）时列表为空、条数为 0，不查库也不调 catalog。匿名遮蔽在 `ExperienceViews` 里做。
@Component
@RequiredArgsConstructor
public class ExperienceQueryService {

  /// 期刊列表每页最多（也是默认）条数。
  public static final int JOURNAL_PAGE_SIZE = 10;

  /// 用户列表每页最多（也是默认）条数。
  public static final int USER_PAGE_SIZE = 20;

  private final SubjectReadPort subjects;
  private final PostReadPort reads;
  private final PostRepository posts;
  private final UserDirectoryPort users;
  private final ExperienceViews views;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  /// 期刊下的经验一页（不受统计窗口影响，列全部）。
  ///
  /// @param journalKey 期刊的对外 key
  /// @param cursor 游标，可空
  /// @param limit 每页条数 1–10，默认 10
  /// @return 一页
  public CursorPage<ExperienceItemView> journalExperiences(
      String journalKey, String cursor, Integer limit) {
    int size = pageSize(limit, JOURNAL_PAGE_SIZE);
    PostId after = PostId.parseCursor(cursor);
    SubjectId journal = registeredJournal(journalKey);
    if (journal == null) {
      return CursorPage.empty();
    }
    List<PostAggregate> rows = reads.pageByJournal(journal, after, size + 1);
    List<PostAggregate> page = rows.subList(0, Math.min(size, rows.size()));
    return CursorPage.of(views.items(page, viewer()), nextCursor(rows, page));
  }

  /// 期刊汇总。
  ///
  /// @param journalKey 期刊的对外 key
  /// @param window `3y`（默认）/ `all`
  /// @return 汇总
  public ExperienceSummary summary(String journalKey, String window) {
    SummaryWindow summaryWindow = SummaryWindow.fromParam(window);
    SubjectId journal = registeredJournal(journalKey);
    ExperienceStats stats =
        journal == null
            ? ExperienceStats.builder().build()
            : reads.summarize(journal, summaryWindow.since(ExperienceRules.today(clock)));
    return ExperienceSummaryRules.summarize(stats);
  }

  /// 经验详情。
  ///
  /// @param postId 经验 ID
  /// @return 详情；已删除的只有删除标记与期刊
  /// @throws PostNotFoundException 不存在
  public ExperienceDetailView detail(long postId) {
    PostAggregate post = posts.find(PostId.of(postId)).orElseThrow(PostNotFoundException::notFound);
    return views.detail(post, viewer());
  }

  /// 修改历史：当前版本在前，旧版本按替换顺序倒排。
  ///
  /// @param postId 经验 ID
  /// @return 版本
  /// @throws PostNotFoundException 不存在或已删除
  public List<RevisionVersionView> revisions(long postId) {
    PostId id = PostId.of(postId);
    PostAggregate post = posts.find(id).orElseThrow(PostNotFoundException::notFound);
    if (post.isDeleted()) {
      throw PostNotFoundException.deleted();
    }
    List<RevisionVersionView> versions = new ArrayList<>();
    versions.add(
        RevisionVersionView.of(
            post.contentWrittenAt(),
            true,
            post.fields(),
            post.body() == null ? null : post.body().value()));
    reads.revisions(id).stream()
        .map(
            r ->
                RevisionVersionView.of(
                    r.writtenAt(), false, r.fields(), r.body() == null ? null : r.body().value()))
        .forEach(versions::add);
    return List.copyOf(versions);
  }

  /// 用户主页的经验一页：本人看全部（含匿名）；别人只在 identity 判定主页对他可见时看署名的，否则空页。
  ///
  /// @param userId 主页主人
  /// @param cursor 游标，可空
  /// @param limit 每页条数 1–20，默认 20（「关于」页用 1 判有没有）
  /// @return 一页
  public CursorPage<UserExperienceView> userExperiences(long userId, String cursor, Integer limit) {
    int size = pageSize(limit, USER_PAGE_SIZE);
    PostId after = PostId.parseCursor(cursor);
    Long viewer = viewer();
    boolean self = viewer != null && viewer == userId;
    if (!self) {
      UserCard owner = users.findCards(List.of(userId), viewer).get(userId);
      if (owner == null || !owner.profileVisible()) {
        return CursorPage.empty();
      }
    }
    List<PostAggregate> rows = reads.pageByAuthor(userId, self, after, size + 1);
    List<PostAggregate> page = rows.subList(0, Math.min(size, rows.size()));
    Map<SubjectId, SubjectView> journals =
        page.isEmpty()
            ? Map.of()
            : subjects.findByIds(page.stream().map(PostAggregate::subjectId).distinct().toList());
    List<UserExperienceView> items =
        page.stream()
            .map(
                post ->
                    UserExperienceView.of(
                        JournalRefView.of(journals.get(post.subjectId())),
                        views.withoutAuthor(post, viewer)))
            .toList();
    return CursorPage.of(items, nextCursor(rows, page));
  }

  /// 已登记的期刊；key 不合法 422，未登记为 `null`。
  ///
  /// @param journalKey 期刊的对外 key
  /// @return 本地 ID，可空
  private SubjectId registeredJournal(String journalKey) {
    SubjectType.JOURNAL.parseKey(journalKey);
    return subjects.findIdsByKeys(SubjectType.JOURNAL, List.of(journalKey)).get(journalKey);
  }

  /// 每页条数：没给用上限；1 ~ 上限，否则 422。
  ///
  /// @param limit 请求的条数，可空
  /// @param max 上限
  /// @return 条数
  private static int pageSize(Integer limit, int max) {
    if (limit == null) {
      return max;
    }
    if (limit < 1 || limit > max) {
      throw new InvalidPostRequestException("每页条数要在 1 到 " + max + " 之间：" + limit);
    }
    return limit;
  }

  /// 多取的那一条存在时，下一页游标是本页最后一条的 ID。
  ///
  /// @param rows 多取一条的结果
  /// @param page 本页
  /// @return 游标，可空
  private static String nextCursor(List<PostAggregate> rows, List<PostAggregate> page) {
    return rows.size() > page.size() ? Long.toString(page.getLast().postId().value()) : null;
  }

  /// 访客的用户 ID，匿名为 `null`。
  ///
  /// @return 用户 ID
  private Long viewer() {
    return currentUser.current().map(CurrentUser::userId).orElse(null);
  }
}
