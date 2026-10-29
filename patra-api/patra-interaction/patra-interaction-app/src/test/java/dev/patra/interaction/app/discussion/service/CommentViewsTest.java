package dev.patra.interaction.app.discussion.service;

import static dev.patra.interaction.app.discussion.DiscussionFixture.card;
import static dev.patra.interaction.app.discussion.DiscussionFixture.post;
import static dev.patra.interaction.app.discussion.DiscussionFixture.publication;
import static dev.patra.interaction.app.discussion.DiscussionFixture.reply;
import static dev.patra.interaction.app.discussion.DiscussionFixture.root;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.interaction.app.discussion.usecase.query.MentionView;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// 视图拼装：批量取名片、匿名所有者遮蔽、「作者」与「这条是我发的」、「回复 @X」、占位、提及实体、主页不可见时置空
/// 用户名与头像。
class CommentViewsTest {

  private static final long OWNER = 9L;
  private static final long VIEWER = 5L;

  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final CommentViews views = new CommentViews(users);
  private final Map<Long, UserCard> directory = new HashMap<>();

  /// identity 返回目录里有的人。
  CommentViewsTest() {
    when(users.findCards(anyCollection(), any()))
        .thenAnswer(
            invocation -> {
              Collection<Long> ids = invocation.getArgument(0);
              return ids.stream()
                  .filter(directory::containsKey)
                  .collect(Collectors.toMap(id -> id, directory::get));
            });
  }

  /// 匿名所有者的评论对别人：没有作者名片，标匿名与「作者」，不向 identity 要他的名片。
  @Test
  void should_mask_anonymous_owner_for_others() {
    directory.put(OWNER, card(OWNER));

    CommentView view =
        views
            .assemble(List.of(root(1L, OWNER).build()), post(OWNER, true), VIEWER, Map.of())
            .getFirst();

    assertThat(view.author()).isNull();
    assertThat(view.anonymous()).isTrue();
    assertThat(view.owner()).isTrue();
    assertThat(view.mine()).isFalse();
    verify(users, never()).findCards(anyCollection(), any());
  }

  /// 匿名所有者本人看自己的评论：作者是他自己的名片。
  @Test
  void should_give_anonymous_owner_their_own_card() {
    directory.put(OWNER, card(OWNER));

    CommentView view =
        views
            .assemble(List.of(root(1L, OWNER).build()), post(OWNER, true), OWNER, Map.of())
            .getFirst();

    assertThat(view.author().userId()).isEqualTo(OWNER);
    assertThat(view.anonymous()).isTrue();
    assertThat(view.mine()).isTrue();
  }

  /// 非匿名所有者：名片照常，标「作者」。
  @Test
  void should_mark_non_anonymous_owner() {
    directory.put(OWNER, card(OWNER));

    CommentView view =
        views
            .assemble(List.of(root(1L, OWNER).build()), post(OWNER, false), VIEWER, Map.of())
            .getFirst();

    assertThat(view.owner()).isTrue();
    assertThat(view.anonymous()).isFalse();
    assertThat(view.author().userId()).isEqualTo(OWNER);
  }

  /// 主页对访客不可见：去掉用户名与头像地址，昵称照常。
  @Test
  void should_hide_username_and_avatar_when_profile_invisible() {
    directory.put(2L, card(2L, AccountState.BANNED, false));

    CommentView view =
        views.assemble(List.of(root(1L, 2L).build()), publication(), VIEWER, Map.of()).getFirst();

    assertThat(view.author().username()).isNull();
    assertThat(view.author().avatarUrl()).isNull();
    assertThat(view.author().displayName()).isEqualTo("D2");
    assertThat(view.author().state()).isEqualTo(AccountState.BANNED);
  }

  /// 「回复 @X」：回复根评论没有；回复正常评论给作者；回复占位标已删除；回复匿名所有者标匿名。
  @Test
  void should_describe_reply_targets() {
    directory.put(6L, card(6L));
    directory.put(7L, card(7L));

    List<CommentView> result =
        views.assemble(
            List.of(
                reply(11L, 1L, 1L, 6L, 7L).build(),
                reply(12L, 1L, 11L, 6L, 7L).build(),
                reply(13L, 1L, 11L, 6L, 7L).parentState(CommentState.DELETED).build(),
                reply(14L, 1L, 11L, OWNER, 7L).build()),
            post(OWNER, true),
            VIEWER,
            Map.of());

    assertThat(result.get(0).replyTo()).isNull();
    assertThat(result.get(1).replyTo().author().userId()).isEqualTo(6L);
    assertThat(result.get(2).replyTo().deleted()).isTrue();
    assertThat(result.get(2).replyTo().author()).isNull();
    assertThat(result.get(3).replyTo().anonymous()).isTrue();
    assertThat(result.get(3).replyTo().author()).isNull();
  }

  /// 占位只剩位置：没有作者、正文、提及。
  @Test
  void should_render_placeholders_without_content() {
    CommentView view =
        views
            .assemble(
                List.of(
                    root(1L, VIEWER)
                        .state(CommentState.DELETED)
                        .body(null)
                        .hasReplies(true)
                        .build()),
                publication(),
                VIEWER,
                Map.of())
            .getFirst();

    assertThat(view.deleted()).isTrue();
    assertThat(view.author()).isNull();
    assertThat(view.body()).isNull();
    assertThat(view.mentions()).isEmpty();
    assertThat(view.mine()).isFalse();
    assertThat(view.hasReplies()).isTrue();
  }

  /// 提及实体按出现顺序：可见的给用户名；不可见的只给昵称；已注销与 identity 省略的标已注销。
  @Test
  void should_build_mention_entities_in_order() {
    directory.put(2L, card(2L));
    directory.put(7L, card(7L));
    directory.put(8L, card(8L, AccountState.ACTIVE, false));
    directory.put(10L, card(10L, AccountState.DELETED, false));

    CommentView view =
        views
            .assemble(
                List.of(root(1L, 2L).body("<@7> <@8> <@10> <@11>").build()),
                publication(),
                VIEWER,
                Map.of())
            .getFirst();

    assertThat(view.mentions())
        .containsExactly(
            MentionView.of(7L, "u7", "D7", false),
            MentionView.of(8L, null, "D8", false),
            MentionView.of(10L, null, null, true),
            MentionView.of(11L, null, null, true));
  }

  /// identity 省略了作者：作者为空、不算匿名。
  @Test
  void should_treat_omitted_author_as_null() {
    CommentView view =
        views.assemble(List.of(root(1L, 2L).build()), publication(), VIEWER, Map.of()).getFirst();

    assertThat(view.author()).isNull();
    assertThat(view.anonymous()).isFalse();
  }

  /// 每 100 个一批向 identity 要名片，已知的不再要。
  @Test
  void should_batch_lookups_by_hundred_and_skip_known_cards() {
    String body =
        LongStream.rangeClosed(1, 150)
            .mapToObj(id -> "<@" + (1000 + id) + ">")
            .collect(Collectors.joining(" "));

    views.assemble(
        List.of(root(1L, 2L).body(body).build()), publication(), VIEWER, Map.of(2L, card(2L)));

    ArgumentCaptor<Collection<Long>> batches = ArgumentCaptor.captor();
    verify(users, times(2)).findCards(batches.capture(), eq(VIEWER));
    assertThat(batches.getAllValues()).extracting(Collection::size).containsExactly(100, 50);
    assertThat(batches.getAllValues()).allSatisfy(batch -> assertThat(batch).doesNotContain(2L));
  }

  /// identity 不可用原样抛出。
  @Test
  void should_propagate_identity_outage() {
    when(users.findCards(anyCollection(), any()))
        .thenThrow(new UserDirectoryUnavailableException(new IllegalStateException("down")));

    assertThatThrownBy(
            () -> views.assemble(List.of(root(1L, 2L).build()), publication(), VIEWER, Map.of()))
        .isInstanceOf(UserDirectoryUnavailableException.class);
  }
}
