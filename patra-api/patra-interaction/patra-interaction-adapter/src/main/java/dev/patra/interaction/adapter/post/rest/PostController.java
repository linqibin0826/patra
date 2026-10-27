package dev.patra.interaction.adapter.post.rest;

import dev.patra.interaction.adapter.post.rest.request.ExperienceRequest;
import dev.patra.interaction.adapter.post.rest.response.ExperienceDetailResponse;
import dev.patra.interaction.adapter.post.rest.response.RevisionResponse;
import dev.patra.interaction.adapter.post.rest.response.RevisionsResponse;
import dev.patra.interaction.app.post.usecase.experience.DeleteExperienceCommand;
import dev.patra.interaction.app.post.usecase.experience.EditExperienceCommand;
import dev.patra.interaction.app.post.usecase.query.ExperienceQueryService;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 一条投稿经验（经验页 `/posts/{id}`）：详情与修改历史（登录可选），编辑与删除（作者本人）。
@Tag(name = "Experiences", description = "投稿经验")
@RestController
@RequestMapping("/posts/{postId}")
@RequiredArgsConstructor
public class PostController {

  private final CommandBus commandBus;
  private final ExperienceQueryService query;

  /// 详情。已删除也是 200（`deleted: true`，只有期刊）；404 不存在。
  ///
  /// @param postId 经验 ID
  /// @return 详情
  @GetMapping
  public ExperienceDetailResponse detail(@PathVariable long postId) {
    return ExperienceDetailResponse.from(query.detail(postId));
  }

  /// 编辑（整体替换字段与正文；不能改匿名、不能换期刊）。422 字段错误；403 不是作者；404 不存在 / 已删除（带
  /// `SUBJECT_UNAVAILABLE`）；409 重复投稿日期或资格不符。
  ///
  /// @param postId 经验 ID
  /// @param request 请求体（`anonymous` 忽略）
  /// @return 编辑后的详情
  @PutMapping
  public ExperienceDetailResponse edit(
      @PathVariable long postId, @RequestBody ExperienceRequest request) {
    return ExperienceDetailResponse.from(
        commandBus.handle(EditExperienceCommand.of(postId, request.toInput())));
  }

  /// 删除（幂等）。403 不是作者；404 不存在。
  ///
  /// @param postId 经验 ID
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long postId) {
    commandBus.handle(DeleteExperienceCommand.of(postId));
  }

  /// 修改历史：当前版本在前。404 不存在 / 已删除（带 `SUBJECT_UNAVAILABLE`）。
  ///
  /// @param postId 经验 ID
  /// @return 版本
  @GetMapping("/revisions")
  public RevisionsResponse revisions(@PathVariable long postId) {
    return RevisionsResponse.of(
        query.revisions(postId).stream().map(RevisionResponse::from).toList());
  }
}
