package dev.patra.identity.adapter.rest.internal;

import dev.patra.identity.adapter.rest.AvatarUrls;
import dev.patra.identity.api.dto.AccountStatus;
import dev.patra.identity.api.dto.UserSummariesReq;
import dev.patra.identity.api.dto.UserSummariesResp;
import dev.patra.identity.api.dto.UserSummaryResp;
import dev.patra.identity.api.endpoint.UserEndpoint;
import dev.patra.identity.app.usecase.user.query.UserSummaryQueryService;
import dev.patra.identity.domain.model.read.UserSummaryView;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 用户内部端点：`POST /_internal/users/summaries`（PAP-108）。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403），interaction 经服务发现直连。
/// 信任边界：访客 ID 取自请求体，不取转发的身份断言——identity 信任内部调用方；注销清理任务没有访客，填空。
@RestController
@RequiredArgsConstructor
public class UserEndpointImpl implements UserEndpoint {

  private final UserSummaryQueryService userSummaryQueryService;

  /// 批量用户摘要。
  ///
  /// @param request 用户 ID 与可空的访客 ID
  /// @return 摘要
  @Override
  public UserSummariesResp summaries(UserSummariesReq request) {
    return UserSummariesResp.of(
        userSummaryQueryService
            .summaries(request.userIds(), Optional.ofNullable(request.viewerUserId()))
            .stream()
            .map(UserEndpointImpl::toResp)
            .toList());
  }

  /// 视图映射成响应。
  ///
  /// @param view 摘要视图
  /// @return 响应
  private static UserSummaryResp toResp(UserSummaryView view) {
    return UserSummaryResp.builder()
        .userId(view.userId())
        .username(view.username())
        .displayName(view.displayName())
        .avatarUrl(AvatarUrls.of(view.avatar()))
        .avatarPalette(view.avatarPalette())
        .accountStatus(AccountStatus.valueOf(view.status().name()))
        .emailVerified(view.emailVerified())
        .profileVisible(view.profileVisible())
        .build();
  }
}
