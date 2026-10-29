package dev.patra.identity.adapter.session.internal;

import dev.patra.identity.api.session.dto.SessionIntrospectionReq;
import dev.patra.identity.api.session.dto.SessionIntrospectionResp;
import dev.patra.identity.api.session.endpoint.SessionEndpoint;
import dev.patra.identity.app.session.usecase.query.SessionIntrospectionService;
import dev.patra.tool.core.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 会话内部端点：`POST /_internal/sessions/introspect`（PAP-106）。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403）；网关调用时不带断言，按匿名到达。
/// 会话存储暂时不可用时抛的异常带 `DEP_UNAVAILABLE`，错误引擎映射为 503。
@RestController
@RequiredArgsConstructor
public class SessionEndpointImpl implements SessionEndpoint {

  private final SessionIntrospectionService sessionIntrospectionService;

  /// 按令牌查会话并节流续期。
  ///
  /// @param request 令牌原文
  /// @return 查询结果；查不到时 `active` 为 `false`
  @Override
  public SessionIntrospectionResp introspect(SessionIntrospectionReq request) {
    return sessionIntrospectionService
        .introspect(request.token())
        .map(SessionEndpointImpl::active)
        .orElseGet(SessionIntrospectionResp::inactive);
  }

  /// 有效会话的结果。
  ///
  /// @param user 当前用户
  /// @return 结果
  private static SessionIntrospectionResp active(CurrentUser user) {
    return SessionIntrospectionResp.builder()
        .active(true)
        .userId(user.userId())
        .sessionId(user.sessionId())
        .accountType(user.accountType())
        .clientType(user.clientType())
        .build();
  }
}
