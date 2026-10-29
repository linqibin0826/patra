package dev.patra.identity.adapter.account.internal;

import dev.patra.identity.adapter.account.rest.converter.AvatarUrls;
import dev.patra.identity.api.account.dto.AccountStatus;
import dev.patra.identity.api.account.dto.UserResolutionResp;
import dev.patra.identity.api.account.dto.UserResolveByUsernamesReq;
import dev.patra.identity.api.account.dto.UserResolveResp;
import dev.patra.identity.api.account.dto.UserSuggestReq;
import dev.patra.identity.api.account.dto.UserSuggestionsResp;
import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummariesResp;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.identity.app.account.usecase.query.UserResolutionQueryService;
import dev.patra.identity.app.account.usecase.query.UserSuggestionQueryService;
import dev.patra.identity.app.account.usecase.query.UserSummaryQueryService;
import dev.patra.identity.domain.account.model.read.UserSummaryView;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 用户内部端点：`POST /_internal/users/summaries`（PAP-108）、`POST /_internal/users/suggest` 与
/// `POST /_internal/users/resolve/by-usernames`（PAP-118）。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403），interaction 经服务发现直连。
/// 信任边界：访客 ID 取自请求体，不取转发的身份断言——identity 信任内部调用方；注销清理任务没有访客，填空。
@RestController
@RequiredArgsConstructor
public class UserEndpointImpl implements UserEndpoint {

  private final UserSummaryQueryService userSummaryQueryService;
  private final UserSuggestionQueryService userSuggestionQueryService;
  private final UserResolutionQueryService userResolutionQueryService;

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

  /// @ 联想。
  ///
  /// @param request 前缀、可空的访客 ID 与条数上限
  /// @return 摘要，用户名命中的在前、昵称命中的在后
  @Override
  public UserSuggestionsResp suggest(UserSuggestReq request) {
    return UserSuggestionsResp.of(
        userSuggestionQueryService
            .suggest(request.prefix(), Optional.ofNullable(request.viewerUserId()), request.limit())
            .stream()
            .map(UserEndpointImpl::toResp)
            .toList());
  }

  /// 按用户名批量解析。
  ///
  /// @param request 用户名原文
  /// @return 解析到的那些，回显原文
  @Override
  public UserResolveResp resolveByUsernames(UserResolveByUsernamesReq request) {
    return UserResolveResp.of(
        userResolutionQueryService.resolveByUsernames(request.usernames()).stream()
            .map(
                user ->
                    UserResolutionResp.of(
                        user.username(),
                        user.userId(),
                        AccountStatus.valueOf(user.status().name())))
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
