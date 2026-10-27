package dev.patra.interaction.domain.post.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.EditKind;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.enums.PostType;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostBody;
import dev.patra.interaction.domain.post.model.vo.PostEdit;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/// 帖子聚合：发布、作者判定与编辑决策（内容没变什么都不做；发布后 5 分钟内覆盖不记版本；之后记下被替换的旧版本）。
class PostAggregateTest {

  private static final Instant CREATED = Instant.parse("2026-10-27T02:00:00Z");
  private static final long AUTHOR = 7L;

  private static final ExperienceFields FIELDS =
      ExperienceFields.builder()
          .submittedOn(LocalDate.parse("2026-03-02"))
          .firstDecisionOn(LocalDate.parse("2026-04-13"))
          .firstDecision(FirstDecision.MAJOR_REVISION)
          .finalOutcome(FinalOutcome.IN_PROGRESS)
          .articleType(ArticleType.ORIGINAL_RESEARCH)
          .build();

  private static final ExperienceDraft DRAFT = ExperienceDraft.of(FIELDS, new PostBody("第一版"));

  private static final ExperienceDraft CHANGED =
      ExperienceDraft.of(
          FIELDS.toBuilder().finalOutcome(FinalOutcome.ACCEPTED).build(), new PostBody("第二版"));

  private final PostAggregate post =
      PostAggregate.publish(PostId.of(1L), SubjectId.of(2L), AUTHOR, true, DRAFT, CREATED);

  /// 发布出来的是 ACTIVE 的投稿经验，没有修改记录。
  @Test
  void should_publish_active_experience_without_revision() {
    assertThat(post.type()).isEqualTo(PostType.SUBMISSION_EXPERIENCE);
    assertThat(post.status()).isEqualTo(PostStatus.ACTIVE);
    assertThat(post.anonymous()).isTrue();
    assertThat(post.fields()).isEqualTo(FIELDS);
    assertThat(post.body()).isEqualTo(new PostBody("第一版"));
    assertThat(post.createdAt()).isEqualTo(CREATED);
    assertThat(post.revisedAt()).isNull();
    assertThat(post.edited()).isFalse();
    assertThat(post.contentWrittenAt()).isEqualTo(CREATED);
  }

  /// 只有作者本人是作者。
  @Test
  void should_identify_author() {
    assertThat(post.isAuthor(AUTHOR)).isTrue();
    assertThat(post.isAuthor(8L)).isFalse();
  }

  /// 内容一字没变：什么都不做。
  @Test
  void should_not_change_anything_when_content_is_equal() {
    PostEdit edit = post.edit(DRAFT, CREATED.plus(Duration.ofHours(1)));

    assertThat(edit.kind()).isEqualTo(EditKind.NONE);
    assertThat(edit.after()).isEqualTo(post);
    assertThat(edit.replaced()).isNull();
  }

  /// 发布后 4 分 59 秒内改：覆盖，不记版本。
  @Test
  void should_overwrite_without_revision_within_grace() {
    PostEdit edit = post.edit(CHANGED, CREATED.plus(Duration.ofSeconds(299)));

    assertThat(edit.kind()).isEqualTo(EditKind.OVERWRITE);
    assertThat(edit.after().fields()).isEqualTo(CHANGED.fields());
    assertThat(edit.after().body()).isEqualTo(CHANGED.body());
    assertThat(edit.after().revisedAt()).isNull();
    assertThat(edit.replaced()).isNull();
  }

  /// 恰好 5 分钟起：记下被替换的旧版本，第一版的成文时刻是发布时刻。
  @Test
  void should_record_revision_from_five_minutes() {
    Instant now = CREATED.plus(Duration.ofMinutes(5));

    PostEdit edit = post.edit(CHANGED, now);

    assertThat(edit.kind()).isEqualTo(EditKind.REVISE);
    assertThat(edit.replaced().fields()).isEqualTo(FIELDS);
    assertThat(edit.replaced().body()).isEqualTo(new PostBody("第一版"));
    assertThat(edit.replaced().writtenAt()).isEqualTo(CREATED);
    assertThat(edit.replaced().replacedAt()).isEqualTo(now);
    assertThat(edit.after().revisedAt()).isEqualTo(now);
    assertThat(edit.after().edited()).isTrue();
    assertThat(edit.after().contentWrittenAt()).isEqualTo(now);
  }

  /// 第二次记版本时，旧版本的成文时刻是上一次记版本的时刻。
  @Test
  void should_date_next_revision_from_previous_revision() {
    Instant first = CREATED.plus(Duration.ofMinutes(10));
    Instant second = CREATED.plus(Duration.ofMinutes(20));
    PostAggregate revised = post.edit(CHANGED, first).after();

    PostEdit edit = revised.edit(DRAFT, second);

    assertThat(edit.kind()).isEqualTo(EditKind.REVISE);
    assertThat(edit.replaced().writtenAt()).isEqualTo(first);
    assertThat(edit.after().revisedAt()).isEqualTo(second);
  }

  /// 已删除的不能编辑（程序错误：调用方先判）。
  @Test
  void should_refuse_editing_deleted_post() {
    PostAggregate deleted = post.toBuilder().status(PostStatus.DELETED).build();

    assertThat(deleted.isDeleted()).isTrue();
    assertThatThrownBy(() -> deleted.edit(CHANGED, CREATED.plus(Duration.ofHours(1))))
        .isInstanceOf(IllegalStateException.class);
  }
}
