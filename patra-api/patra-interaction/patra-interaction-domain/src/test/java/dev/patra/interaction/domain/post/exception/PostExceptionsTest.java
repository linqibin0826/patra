package dev.patra.interaction.domain.post.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.Test;

/// post 子域的异常：语义特征与原因码；游标解析。
class PostExceptionsTest {

  /// 从未存在只带 NOT_FOUND；已删除另带 SUBJECT_UNAVAILABLE（与讨论区「对象已删除」同一个码）。
  @Test
  void should_distinguish_missing_from_deleted() {
    assertThat(PostNotFoundException.notFound().getErrorTraits())
        .containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(PostNotFoundException.deleted().getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.NOT_FOUND, SubjectErrorTrait.SUBJECT_UNAVAILABLE);
    assertThat(PostNotFoundException.notFound()).hasMessage("投稿经验不存在或已被删除");
  }

  /// 不是作者：403。
  @Test
  void should_forbid_non_author() {
    assertThat(new PostForbiddenException().getErrorTraits())
        .containsExactly(StandardErrorTrait.FORBIDDEN);
  }

  /// 游标是规范的十进制正整数；没给为 null；其余 422。
  @Test
  void should_parse_cursor() {
    assertThat(PostId.parseCursor("123")).isEqualTo(PostId.of(123L));
    assertThat(PostId.parseCursor(null)).isNull();
    assertThat(PostId.parseCursor(" ")).isNull();
    assertThatThrownBy(() -> PostId.parseCursor("012"))
        .isInstanceOfSatisfying(
            InvalidPostRequestException.class,
            ex ->
                assertThat(ex.getErrorTraits()).containsExactly(StandardErrorTrait.RULE_VIOLATION));
    assertThatThrownBy(() -> PostId.parseCursor("99999999999999999999"))
        .isInstanceOf(InvalidPostRequestException.class);
  }
}
