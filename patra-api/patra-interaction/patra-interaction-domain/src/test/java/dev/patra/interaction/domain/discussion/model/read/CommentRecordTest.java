package dev.patra.interaction.domain.discussion.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/// 读出来的评论代为给出正文里的提及（别的子域转纯文本要用，`MentionSyntax` 不在讨论的公开包里，PAP-141）。
class CommentRecordTest {

  /// 存储形式里服务端写下的提及：去重、按第一次出现；转义了的不算。
  @Test
  void should_derive_mentioned_ids_from_body() {
    assertThat(comment(CommentState.ACTIVE, "看 <@7> 和 <@9>，再 <@7>，\\<@8>").mentionedUserIds())
        .containsExactly(7L, 9L);
  }

  /// 占位没有正文，也就没有提及。
  @Test
  void should_have_no_mentions_when_placeholder() {
    assertThat(comment(CommentState.DELETED, null).mentionedUserIds()).isEmpty();
  }

  /// 一条根评论。
  ///
  /// @param state 状态
  /// @param body 正文
  /// @return 评论
  private static CommentRecord comment(CommentState state, String body) {
    return CommentRecord.builder()
        .commentId(1L)
        .subjectId(SubjectId.of(2L))
        .rootId(1L)
        .authorId(3L)
        .state(state)
        .body(body)
        .createdAt(Instant.parse("2026-10-31T01:00:00Z"))
        .build();
  }
}
