package dev.patra.interaction.app.discussion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.exception.InvalidCommentException;
import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/// @ 的解析（spec 5.4.2）：本地先数候选，一次向 identity 解析；只有正常账号、或编辑时当前版本里已有的人换成标记；
/// 最终不超过 10 人。
class MentionResolutionTest {

  private final MentionDirectoryPort directory = mock(MentionDirectoryPort.class);
  private final MentionResolution resolution = new MentionResolution(directory);

  /// 没有候选不调 identity。
  @Test
  void should_not_call_identity_without_candidates() {
    ResolvedBody body = resolution.resolve(resolution.draft("没有提及 a@b.com"), Set.of());

    assertThat(body).isEqualTo(ResolvedBody.of("没有提及 a@b.com", List.of()));
    verifyNoInteractions(directory);
  }

  /// 只有正常账号换成标记。
  @Test
  void should_turn_only_active_users_into_tokens() {
    when(directory.resolve(List.of("bob", "ann", "nobody")))
        .thenReturn(
            Map.of(
                "bob", MentionTarget.of(7L, AccountState.ACTIVE),
                "ann", MentionTarget.of(8L, AccountState.BANNED)));

    assertThat(resolution.resolve(resolution.draft("@bob @ann @nobody"), Set.of()))
        .isEqualTo(ResolvedBody.of("<@7> @ann @nobody", List.of(7L)));
  }

  /// 编辑时当前版本里已有的人，不论现在什么状态都保留提及。
  @Test
  void should_keep_carried_over_mentions_whatever_their_state() {
    when(directory.resolve(List.of("bob", "ann", "nobody")))
        .thenReturn(
            Map.of(
                "bob", MentionTarget.of(7L, AccountState.ACTIVE),
                "ann", MentionTarget.of(8L, AccountState.BANNED)));

    assertThat(resolution.resolve(resolution.draft("@bob @ann @nobody"), Set.of(8L)))
        .isEqualTo(ResolvedBody.of("<@7> <@8> @nobody", List.of(7L, 8L)));
  }

  /// 按人数算，不按写法：大小写不同解析到同一个人算一个。
  @Test
  void should_count_people_not_spellings() {
    when(directory.resolve(List.of("Bob", "bob")))
        .thenReturn(
            Map.of(
                "Bob", MentionTarget.of(7L, AccountState.ACTIVE),
                "bob", MentionTarget.of(7L, AccountState.ACTIVE)));

    assertThat(resolution.resolve(resolution.draft("@Bob @bob"), Set.of()).mentionedUserIds())
        .containsExactly(7L);
  }

  /// 11 个不同的正常账号：超过 10 人。
  @Test
  void should_reject_more_than_ten_people() {
    String text = IntStream.rangeClosed(1, 11).mapToObj(i -> "@user-" + i).collect(joining());
    when(directory.resolve(anyCollection()))
        .thenAnswer(
            invocation ->
                IntStream.rangeClosed(1, 11)
                    .boxed()
                    .collect(
                        Collectors.toMap(
                            i -> "user-" + i, i -> MentionTarget.of(i, AccountState.ACTIVE))));

    assertTooManyMentions(() -> resolution.resolve(resolution.draft(text), Set.of()));
  }

  /// 101 个不同的候选：本地就拒绝，不调 identity。
  @Test
  void should_reject_more_than_hundred_candidates_without_calling_identity() {
    String text = IntStream.rangeClosed(1, 101).mapToObj(i -> "@user-" + i).collect(joining());

    assertTooManyMentions(() -> resolution.draft(text));
    verifyNoInteractions(directory);
  }

  /// 空格分隔。
  ///
  /// @return 收集器
  private static Collector<CharSequence, ?, String> joining() {
    return Collectors.joining(" ");
  }

  /// 断言是「@ 太多」。
  ///
  /// @param action 动作
  private static void assertTooManyMentions(ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            InvalidCommentException.class,
            ex ->
                assertThat(ex.getFieldViolations())
                    .extracting(FieldViolation::code)
                    .containsExactly(InvalidCommentException.TOO_MANY_MENTIONS));
  }
}
