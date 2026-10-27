package dev.patra.interaction.adapter.post.rest;

import dev.patra.interaction.adapter.post.rest.response.CursorPageResponse;
import dev.patra.interaction.adapter.post.rest.response.UserExperienceResponse;
import dev.patra.interaction.app.post.usecase.query.ExperienceQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 用户主页的「投稿经验」标签（登录可选）：本人看全部（匿名的带 `anonymous: true`）；别人只在主页对他可见时看署名的，
/// 不可见回空页。
@Tag(name = "UserExperiences", description = "用户的投稿经验")
@RestController
@RequiredArgsConstructor
public class UserPostController {

  private final ExperienceQueryService query;

  /// 用户的经验一页：投稿日期倒序。
  ///
  /// @param userId 主页主人
  /// @param cursor 上一页的 `nextCursor`
  /// @param limit 每页 1 ~ 20，默认 20（「关于」页用 1 判有没有）
  /// @return 一页
  @GetMapping("/users/{userId}/posts")
  public CursorPageResponse<UserExperienceResponse> list(
      @PathVariable long userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return CursorPageResponse.from(
        query.userExperiences(userId, cursor, limit), UserExperienceResponse::from);
  }
}
