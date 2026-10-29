package dev.patra.interaction.app.post;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.model.vo.ExperienceInput;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.time.Instant;
import java.time.LocalDate;

/// post 子域 app 层测试的共用数据：今天 2026-10-27（北京时间），期刊 100（本地 ID 21），经验 500。
public final class PostFixtures {

  /// 现在。
  public static final Instant NOW = Instant.parse("2026-10-27T02:00:00Z");

  /// 北京时间的今天。
  public static final LocalDate TODAY = LocalDate.parse("2026-10-27");

  /// 期刊的对外 key。
  public static final String JOURNAL_KEY = "100";

  /// 期刊的本地 ID。
  public static final SubjectId JOURNAL = SubjectId.of(21L);

  /// 经验的登记 ID。
  public static final SubjectId POST_SUBJECT = SubjectId.of(31L);

  /// 经验 ID。
  public static final PostId POST_ID = PostId.of(500L);

  /// 只有静态成员。
  private PostFixtures() {}

  /// 合法的输入：3 月 2 日投稿、4 月 13 日大修、进行中、原创研究。
  ///
  /// @return 构建器
  public static ExperienceInput.ExperienceInputBuilder input() {
    return ExperienceInput.builder()
        .submittedOn("2026-03-02")
        .firstDecisionOn("2026-04-13")
        .firstDecision("MAJOR_REVISION")
        .finalOutcome("IN_PROGRESS")
        .articleType("ORIGINAL_RESEARCH");
  }

  /// 一条已发布的经验。
  ///
  /// @param author 作者
  /// @param anonymous 是否匿名
  /// @param createdAt 发布时刻
  /// @return 经验
  public static PostAggregate post(long author, boolean anonymous, Instant createdAt) {
    return PostAggregate.publish(
        POST_ID,
        JOURNAL,
        author,
        anonymous,
        ExperienceDraft.parse(input().body("正文").build(), TODAY),
        createdAt);
  }

  /// 期刊的登记视图：全称 `Journal of X`、缩写 `J X`。
  ///
  /// @return 视图
  public static SubjectView journalView() {
    return SubjectView.builder()
        .subjectId(JOURNAL)
        .type(SubjectType.JOURNAL)
        .key(JOURNAL_KEY)
        .state(SubjectState.ACTIVE)
        .snapshot(SubjectSnapshot.of("Journal of X", null, null, "J X"))
        .build();
  }

  /// 经验的登记视图（投稿月份 2026-03）。
  ///
  /// @param owner 作者
  /// @param anonymous 是否匿名
  /// @return 视图
  public static SubjectView postView(long owner, boolean anonymous) {
    return SubjectView.builder()
        .subjectId(POST_SUBJECT)
        .type(SubjectType.POST)
        .key("500")
        .state(SubjectState.ACTIVE)
        .snapshot(SubjectSnapshot.of(null, "Journal of X", "2026-03", "J X"))
        .owner(SubjectOwner.of(owner, anonymous))
        .build();
  }

  /// 一张正常用户的名片。
  ///
  /// @param userId 用户
  /// @param profileVisible 主页对访客是否可见
  /// @return 名片
  public static UserCard card(long userId, boolean profileVisible) {
    return UserCard.builder()
        .userId(userId)
        .username("user-" + userId)
        .displayName("用户" + userId)
        .avatarUrl("/users/" + userId + "/avatar")
        .avatarPalette(3)
        .state(AccountState.ACTIVE)
        .emailVerified(true)
        .profileVisible(profileVisible)
        .build();
  }
}
