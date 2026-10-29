package dev.patra.interaction.adapter.reaction.rest;

import dev.patra.interaction.adapter.reaction.rest.response.HelpfulResponse;
import dev.patra.interaction.app.reaction.usecase.helpful.MarkHelpfulCommand;
import dev.patra.interaction.app.reaction.usecase.helpful.UnmarkHelpfulCommand;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/// 「有帮助」：给别人的评论（根评论、楼内回复）与投稿经验点、取消。都要登录，都幂等，响应带当前状态与计数。
///
/// 点的失败：404 `HELPFUL_TARGET_NOT_FOUND`（不存在、占位、已删除、所在对象已失效）；403 `SELF_HELPFUL`（自己的内容，
/// 含匿名经验的作者）；409 `ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED`；429 `DAILY_LIMIT_REACHED`（每天 300 次，带
/// `Retry-After`）。取消只要登录：没点过或目标已删除时照样 200。
@Tag(name = "Helpful", description = "有帮助")
@RestController
@RequiredArgsConstructor
public class HelpfulController {

  private final CommandBus commandBus;

  /// 给评论点有帮助。
  ///
  /// @param commentId 评论
  /// @return 点之后的状态
  @PutMapping("/comments/{commentId}/helpful")
  public HelpfulResponse markComment(@PathVariable long commentId) {
    return HelpfulResponse.from(
        commandBus.handle(MarkHelpfulCommand.of(HelpfulTargetType.COMMENT, commentId)));
  }

  /// 取消对评论的有帮助。
  ///
  /// @param commentId 评论
  /// @return 取消之后的状态
  @DeleteMapping("/comments/{commentId}/helpful")
  public HelpfulResponse unmarkComment(@PathVariable long commentId) {
    return HelpfulResponse.from(
        commandBus.handle(UnmarkHelpfulCommand.of(HelpfulTargetType.COMMENT, commentId)));
  }

  /// 给投稿经验点有帮助。
  ///
  /// @param postId 经验
  /// @return 点之后的状态
  @PutMapping("/posts/{postId}/helpful")
  public HelpfulResponse markPost(@PathVariable long postId) {
    return HelpfulResponse.from(
        commandBus.handle(MarkHelpfulCommand.of(HelpfulTargetType.POST, postId)));
  }

  /// 取消对投稿经验的有帮助。
  ///
  /// @param postId 经验
  /// @return 取消之后的状态
  @DeleteMapping("/posts/{postId}/helpful")
  public HelpfulResponse unmarkPost(@PathVariable long postId) {
    return HelpfulResponse.from(
        commandBus.handle(UnmarkHelpfulCommand.of(HelpfulTargetType.POST, postId)));
  }
}
