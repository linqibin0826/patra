package dev.patra.identity.api.session.endpoint;

import dev.patra.identity.api.session.dto.SessionIntrospectionReq;
import dev.patra.identity.api.session.dto.SessionIntrospectionResp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 会话内部 API 契约：网关每个带令牌的请求按令牌查会话（PAP-106）。
///
/// 此接口同时用于：
///
/// - **服务端**：`SessionEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：网关通过 HTTP Interface 代理调用（`RestClientFactory.createProxy`）
///
/// 端点：
///
/// - POST /_internal/sessions/introspect - 按令牌查会话并节流续期；查不到回 `active: false`
///
/// 令牌放请求体而不放 `Authorization`：identity 的过滤器链把 `Authorization: Bearer` 当身份断言验签。
@HttpExchange(
    url = "/_internal/sessions",
    accept = "application/json",
    contentType = "application/json")
public interface SessionEndpoint {

  /// 按令牌查会话并节流续期。
  ///
  /// @param request 令牌原文
  /// @return 查询结果；令牌格式不对、会话不在或已过绝对期时 `active` 为 `false`
  @PostExchange("/introspect")
  SessionIntrospectionResp introspect(@RequestBody SessionIntrospectionReq request);
}
