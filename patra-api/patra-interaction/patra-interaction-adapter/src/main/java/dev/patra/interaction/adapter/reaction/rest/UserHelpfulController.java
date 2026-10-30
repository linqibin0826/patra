package dev.patra.interaction.adapter.reaction.rest;

import dev.patra.interaction.adapter.reaction.rest.response.HelpfulReceivedResponse;
import dev.patra.interaction.app.reaction.usecase.query.HelpfulQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/// 用户主页上的有帮助统计（不要会话）。
@Tag(name = "UserHelpful", description = "用户获赞")
@RestController
@RequiredArgsConstructor
public class UserHelpfulController {

  private final HelpfulQueryService helpfulQuery;

  /// 主页的获赞：没有记录为 0，不判断用户是否存在。
  ///
  /// @param userId 主页的主人
  /// @return 获赞
  @GetMapping("/users/{userId}/helpful-received")
  public HelpfulReceivedResponse helpfulReceived(@PathVariable long userId) {
    return HelpfulReceivedResponse.of(helpfulQuery.helpfulReceived(userId));
  }
}
