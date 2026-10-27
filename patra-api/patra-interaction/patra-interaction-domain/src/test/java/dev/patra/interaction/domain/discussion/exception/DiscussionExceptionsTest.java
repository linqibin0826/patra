package dev.patra.interaction.domain.discussion.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 讨论的异常：标准特征定 HTTP 状态，原因码与字段码给门户选文案。
class DiscussionExceptionsTest {

  /// 评论不存在、回复目标已删除、不是作者各带自己的特征。
  @Test
  void should_carry_traits() {
    assertThat(new CommentNotFoundException().getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.NOT_FOUND, DiscussionErrorTrait.COMMENT_NOT_FOUND);
    assertThat(new ReplyTargetGoneException().getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, DiscussionErrorTrait.REPLY_TARGET_GONE);
    assertThat(new CommentNotOwnedException().getErrorTraits())
        .containsExactly(StandardErrorTrait.FORBIDDEN);
    assertThat(new InvalidDiscussionRequestException("不支持的排序").getErrorTraits())
        .containsExactly(StandardErrorTrait.RULE_VIOLATION);
  }

  /// 正文的四种问题都是 `body` 字段上的一条错误。
  @Test
  void should_report_body_problems_as_field_violations() {
    assertThat(InvalidCommentException.tooManyMentions().getErrorTraits())
        .containsExactly(StandardErrorTrait.RULE_VIOLATION);
    assertThat(
            List.of(
                InvalidCommentException.required(),
                InvalidCommentException.invalidCharacter(),
                InvalidCommentException.tooLong(),
                InvalidCommentException.tooManyMentions()))
        .flatExtracting(InvalidCommentException::getFieldViolations)
        .extracting(FieldViolation::field, FieldViolation::code, FieldViolation::message)
        .containsExactly(
            tuple("body", "REQUIRED", "写点什么再发表吧"),
            tuple("body", "INVALID_CHARACTER", "含有不允许的字符"),
            tuple("body", "TOO_LONG", "最多 5,000 字"),
            tuple("body", "TOO_MANY_MENTIONS", "一条评论最多 @ 10 人"));
  }
}
