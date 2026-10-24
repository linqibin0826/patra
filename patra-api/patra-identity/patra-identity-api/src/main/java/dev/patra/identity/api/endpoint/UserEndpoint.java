package dev.patra.identity.api.endpoint;

import dev.patra.identity.api.dto.UserSummariesReq;
import dev.patra.identity.api.dto.UserSummariesResp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 用户内部 API 契约：批量用户摘要（PAP-108），interaction 拼收藏夹所有者、判公开收藏夹对访客是否可见时调。
///
/// 此接口同时用于：
///
/// - **服务端**：`UserEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：调用方通过 HTTP Interface 代理调用（`lb://patra-identity`，不经网关）
///
/// 端点：
///
/// - POST /_internal/users/summaries - 一次最多 100 个用户 ID，按请求顺序返回摘要与 `profileVisible`
///
/// 主页可见性规则只在 identity 里有一份，调用方只透传访客 ID，不自己判。
@HttpExchange(
    url = "/_internal/users",
    accept = "application/json",
    contentType = "application/json")
public interface UserEndpoint {

  /// 批量用户摘要。
  ///
  /// @param request 用户 ID（≤ 100，可重复）与可空的访客 ID
  /// @return 摘要，按去重后的请求顺序；identity 不认识的 ID 省略；已注销的是占位（不带用户名）
  @PostExchange("/summaries")
  UserSummariesResp summaries(@RequestBody UserSummariesReq request);
}
