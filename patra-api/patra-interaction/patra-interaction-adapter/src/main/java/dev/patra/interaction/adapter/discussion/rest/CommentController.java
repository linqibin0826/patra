package dev.patra.interaction.adapter.discussion.rest;

import dev.patra.interaction.adapter.discussion.rest.request.EditCommentRequest;
import dev.patra.interaction.adapter.discussion.rest.response.CommentPageResponse;
import dev.patra.interaction.adapter.discussion.rest.response.CommentResponse;
import dev.patra.interaction.adapter.discussion.rest.response.CommentSourceResponse;
import dev.patra.interaction.adapter.discussion.rest.response.ConversationResponse;
import dev.patra.interaction.adapter.discussion.rest.response.DeleteCommentResponse;
import dev.patra.interaction.adapter.discussion.rest.response.RevisionsResponse;
import dev.patra.interaction.app.discussion.usecase.comment.DeleteCommentCommand;
import dev.patra.interaction.app.discussion.usecase.comment.EditCommentCommand;
import dev.patra.interaction.app.discussion.usecase.query.DiscussionQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 按评论 ID 的操作：楼内更多回复、对话链、修改历史（登录可选）；取编辑原文、编辑、删除（要登录，只能是作者）。
@Tag(name = "Comments", description = "评论")
@RestController
@RequiredArgsConstructor
public class CommentController {

  private final CommandBus commandBus;
  private final DiscussionQueryService discussions;

  /// 一楼里剩下的回复（「展开更多回复」）。`commentId` 必须是根评论。
  ///
  /// @param commentId 根评论
  /// @param cursor 楼的 `repliesCursor` 或上一页的 `nextCursor`
  /// @param size 每页几条，1 ~ 50，默认 50
  /// @return 一页
  @GetMapping("/comments/{commentId}/replies")
  public CommentPageResponse replies(
      @PathVariable long commentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return CommentPageResponse.from(discussions.replies(commentId, cursor, size));
  }

  /// 对话链：从根评论到这一条（含占位）。
  ///
  /// @param commentId 评论
  /// @return 对话链
  @GetMapping("/comments/{commentId}/conversation")
  public ConversationResponse conversation(@PathVariable long commentId) {
    return ConversationResponse.from(discussions.conversation(commentId));
  }

  /// 修改历史（公开）：最新在前，最后一个是最初版本。
  ///
  /// @param commentId 评论
  /// @return 修改历史
  @GetMapping("/comments/{commentId}/revisions")
  public RevisionsResponse revisions(@PathVariable long commentId) {
    return RevisionsResponse.from(discussions.revisions(commentId));
  }

  /// 编辑原文（作者本人）：提及换回 `@当前用户名`。
  ///
  /// @param commentId 评论
  /// @return 编辑原文
  @GetMapping("/comments/{commentId}/source")
  public CommentSourceResponse source(@PathVariable long commentId) {
    return new CommentSourceResponse(discussions.source(commentId));
  }

  /// 编辑自己的评论。
  ///
  /// @param commentId 评论
  /// @param request 新正文
  /// @return 改后的完整条目
  @PutMapping("/comments/{commentId}")
  public CommentResponse edit(
      @PathVariable long commentId, @RequestBody EditCommentRequest request) {
    return CommentResponse.from(
        commandBus.handle(EditCommentCommand.of(commentId, request.body())));
  }

  /// 删除自己的评论：有回复留占位，没有回复整条去掉（连带去掉已经没有回复的占位）。
  ///
  /// @param commentId 评论
  /// @return 结果
  @DeleteMapping("/comments/{commentId}")
  public DeleteCommentResponse delete(@PathVariable long commentId) {
    return DeleteCommentResponse.from(commandBus.handle(DeleteCommentCommand.of(commentId)));
  }
}
