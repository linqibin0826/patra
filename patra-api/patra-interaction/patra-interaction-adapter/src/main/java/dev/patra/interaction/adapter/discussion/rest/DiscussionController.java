package dev.patra.interaction.adapter.discussion.rest;

import dev.patra.interaction.adapter.discussion.rest.request.PublishCommentRequest;
import dev.patra.interaction.adapter.discussion.rest.response.CommentResponse;
import dev.patra.interaction.adapter.discussion.rest.response.SingleThreadResponse;
import dev.patra.interaction.adapter.discussion.rest.response.SuggestionResponse;
import dev.patra.interaction.adapter.discussion.rest.response.SuggestionsResponse;
import dev.patra.interaction.adapter.discussion.rest.response.ThreadPageResponse;
import dev.patra.interaction.app.discussion.usecase.comment.PublishCommentCommand;
import dev.patra.interaction.app.discussion.usecase.query.DiscussionQueryService;
import dev.patra.interaction.app.discussion.usecase.query.MentionSuggestionQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 一个对象下的讨论（`type` 是 `publication` 或 `post`，`key` 是门户 URL 里的 ID）。读登录可选（已登录时带会话，拿
/// 「这条是我发的」与主页可见性）；发表与 @ 联想要登录。
@Tag(name = "Discussions", description = "文献与投稿经验下的讨论")
@RestController
@RequiredArgsConstructor
public class DiscussionController {

  private final CommandBus commandBus;
  private final DiscussionQueryService discussions;
  private final MentionSuggestionQueryService suggestions;

  /// 讨论列表的一页：每楼根评论加前 3 条回复与楼统计，带对象的评论总数。对象还没人讨论过时为空列表、总数 0。
  /// 游标与排序配套，格式对不上 422。
  ///
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param sort `asc`（默认，正序）/ `latest`（最新）/ `helpful`（根评论有帮助数降序，相同时新的在前）
  /// @param cursor 上一页的 `nextCursor`
  /// @param size 每页几楼，1 ~ 50，默认 20
  /// @return 一页
  @GetMapping("/discussions/{type}/{key}/threads")
  public ThreadPageResponse threads(
      @PathVariable String type,
      @PathVariable String key,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ThreadPageResponse.from(discussions.threads(type, key, sort, cursor, size));
  }

  /// 单楼视图：目标所在的整楼。目标不存在、已整条去掉或不在这个对象下：404 `COMMENT_NOT_FOUND`。
  ///
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param commentId 目标评论
  /// @return 整楼与目标
  @GetMapping("/discussions/{type}/{key}/comments/{commentId}/thread")
  public SingleThreadResponse thread(
      @PathVariable String type, @PathVariable String key, @PathVariable long commentId) {
    return SingleThreadResponse.from(discussions.thread(type, key, commentId));
  }

  /// 发表根评论或回复（要登录）。
  ///
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param request 正文与父评论
  /// @return 新评论的完整条目
  @PostMapping("/discussions/{type}/{key}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CommentResponse publish(
      @PathVariable String type,
      @PathVariable String key,
      @RequestBody PublishCommentRequest request) {
    return CommentResponse.from(
        commandBus.handle(PublishCommentCommand.of(type, key, request.parentId(), request.body())));
  }

  /// @ 联想（要登录）：本讨论的参与者在前，再并上用户名或昵称前缀匹配的人，合计最多 8 位。
  ///
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param q `@` 后面已输入的原文，可空
  /// @return 联想项
  @GetMapping("/discussions/{type}/{key}/mention-suggestions")
  public SuggestionsResponse suggestions(
      @PathVariable String type,
      @PathVariable String key,
      @RequestParam(required = false) String q) {
    return SuggestionsResponse.of(
        suggestions.suggest(type, key, q).stream().map(SuggestionResponse::from).toList());
  }
}
