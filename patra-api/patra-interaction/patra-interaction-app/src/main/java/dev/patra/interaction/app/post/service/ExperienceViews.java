package dev.patra.interaction.app.post.service;

import dev.patra.interaction.app.post.usecase.query.AuthorView;
import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.app.post.usecase.query.ExperienceItemView;
import dev.patra.interaction.app.post.usecase.query.JournalRefView;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.read.PostStats;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 把经验拼成给访客的视图，并做匿名遮蔽（spec 6.5）：匿名经验只有作者本人拿得到作者名片；一页一次向 identity 取名片，
/// 没有要取的就不调。identity 不可用时异常照常传播（503）。每条带有帮助数、回复数与「我点过」，一页一次批量取。
@Component
@RequiredArgsConstructor
public class ExperienceViews {

  private final UserDirectoryPort users;
  private final SubjectReadPort subjects;
  private final PostReadPort reads;

  /// 一页经验。
  ///
  /// @param posts 经验
  /// @param viewer 访客，匿名为 `null`
  /// @return 视图，顺序同 `posts`
  public List<ExperienceItemView> items(List<PostAggregate> posts, Long viewer) {
    Set<Long> authors = new LinkedHashSet<>();
    posts.stream()
        .filter(post -> authorVisible(post, viewer))
        .forEach(post -> authors.add(post.authorId()));
    Map<Long, UserCard> cards = authors.isEmpty() ? Map.of() : users.findCards(authors, viewer);
    Map<PostId, PostStats> stats = stats(posts, viewer);
    return posts.stream()
        .map(
            post ->
                item(
                    post,
                    authorVisible(post, viewer) ? cards.get(post.authorId()) : null,
                    viewer,
                    stats))
        .toList();
  }

  /// 不带作者的条目（用户主页列表：作者就是主页主人，不再调 identity）。
  ///
  /// @param posts 经验
  /// @param viewer 访客，匿名为 `null`
  /// @return 条目，顺序同 `posts`
  public List<ExperienceItemView> withoutAuthor(List<PostAggregate> posts, Long viewer) {
    Map<PostId, PostStats> stats = stats(posts, viewer);
    return posts.stream().map(post -> item(post, null, viewer, stats)).toList();
  }

  /// 详情：已删除的只给删除标记与期刊。
  ///
  /// @param post 经验
  /// @param viewer 访客，匿名为 `null`
  /// @return 详情
  public ExperienceDetailView detail(PostAggregate post, Long viewer) {
    if (post.isDeleted()) {
      return ExperienceDetailView.builder()
          .postId(post.postId())
          .deleted(true)
          .journal(journal(post.subjectId()))
          .build();
    }
    return detailOf(post, items(List.of(post), viewer).getFirst());
  }

  /// 作者刚写完或改完时的详情：作者名片用资格回查拿到的那张，不再调 identity。
  ///
  /// @param post 经验
  /// @param self 作者自己的名片
  /// @return 详情
  public ExperienceDetailView authored(PostAggregate post, UserCard self) {
    return detailOf(post, item(post, self, post.authorId(), stats(List.of(post), post.authorId())));
  }

  /// 期刊的引用。
  ///
  /// @param journal 期刊的本地 ID
  /// @return 期刊
  public JournalRefView journal(SubjectId journal) {
    return JournalRefView.of(subjects.findByIds(List.of(journal)).get(journal));
  }

  /// 未删除经验的详情。
  ///
  /// @param post 经验
  /// @param item 条目
  /// @return 详情
  private ExperienceDetailView detailOf(PostAggregate post, ExperienceItemView item) {
    return ExperienceDetailView.builder()
        .postId(post.postId())
        .deleted(false)
        .journal(journal(post.subjectId()))
        .experience(item)
        .build();
  }

  /// 一页经验的统计。
  ///
  /// @param posts 经验
  /// @param viewer 访客，匿名为 `null`
  /// @return 经验 → 统计
  private Map<PostId, PostStats> stats(List<PostAggregate> posts, Long viewer) {
    return posts.isEmpty()
        ? Map.of()
        : reads.findStats(posts.stream().map(PostAggregate::postId).toList(), viewer);
  }

  /// 一条经验的视图。
  ///
  /// @param post 经验
  /// @param card 作者名片；不该给或 identity 不认识时为 `null`
  /// @param viewer 访客，匿名为 `null`
  /// @param stats 这一页的统计；缺这条时按 0 与「没点过」
  /// @return 条目
  private static ExperienceItemView item(
      PostAggregate post, UserCard card, Long viewer, Map<PostId, PostStats> stats) {
    PostStats stat =
        stats.getOrDefault(
            post.postId(), PostStats.of(0, 0, viewer == null ? null : Boolean.FALSE));
    return ExperienceItemView.builder()
        .postId(post.postId())
        .fields(post.fields())
        .body(post.body() == null ? null : post.body().value())
        .createdAt(post.createdAt())
        .edited(post.edited())
        .author(card == null ? null : AuthorView.of(card))
        .anonymous(post.anonymous())
        .mine(isAuthor(post, viewer))
        .helpfulCount(stat.helpfulCount())
        .helpful(stat.helpful())
        .replyCount(stat.replyCount())
        .build();
  }

  /// 访客能不能看到作者：署名的谁都能，匿名的只有作者本人。
  ///
  /// @param post 经验
  /// @param viewer 访客
  /// @return 能看到时为 `true`
  private static boolean authorVisible(PostAggregate post, Long viewer) {
    return !post.anonymous() || isAuthor(post, viewer);
  }

  /// 访客是不是作者。
  ///
  /// @param post 经验
  /// @param viewer 访客
  /// @return 是作者时为 `true`
  private static boolean isAuthor(PostAggregate post, Long viewer) {
    return viewer != null && post.isAuthor(viewer);
  }
}
