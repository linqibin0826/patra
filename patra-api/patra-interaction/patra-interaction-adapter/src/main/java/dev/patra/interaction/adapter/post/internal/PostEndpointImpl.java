package dev.patra.interaction.adapter.post.internal;

import dev.patra.interaction.api.post.dto.ExperienceOutlineResp;
import dev.patra.interaction.api.post.dto.ExperienceOutlinesReq;
import dev.patra.interaction.api.post.dto.ExperienceOutlinesResp;
import dev.patra.interaction.api.post.endpoint.PostEndpoint;
import dev.patra.interaction.app.post.usecase.query.ExperienceOutlineQueryService;
import dev.patra.interaction.app.post.usecase.query.ExperienceOutlineView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 经验结果的内部接口（`POST /_internal/posts/outlines`，PAP-143），给通知服务读时现查。网关拒绝 `/*/_internal/**`，
/// 只有服务之间直连能调。枚举按名字转成字符串。
@RestController
@RequiredArgsConstructor
public class PostEndpointImpl implements PostEndpoint {

  private final ExperienceOutlineQueryService query;

  /// 批量取经验结果。
  ///
  /// @param request 帖子 ID
  /// @return 结果，按去重后的请求顺序
  @Override
  public ExperienceOutlinesResp outlines(ExperienceOutlinesReq request) {
    return ExperienceOutlinesResp.of(
        query.outlines(request.postIds()).stream().map(PostEndpointImpl::toResp).toList());
  }

  /// 结果 → 响应。
  ///
  /// @param view 结果
  /// @return 响应
  private static ExperienceOutlineResp toResp(ExperienceOutlineView view) {
    return ExperienceOutlineResp.builder()
        .postId(view.postId())
        .firstDecision(view.outline().firstDecision().name())
        .finalOutcome(view.outline().finalOutcome().name())
        .firstRoundDays(view.outline().firstRoundDays())
        .totalDays(view.outline().totalDays())
        .build();
  }
}
