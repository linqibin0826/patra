package dev.patra.identity.api.account.endpoint;

import dev.patra.identity.api.account.dto.UserRecipientsReq;
import dev.patra.identity.api.account.dto.UserRecipientsResp;
import dev.patra.identity.api.account.dto.UserResolveByUsernamesReq;
import dev.patra.identity.api.account.dto.UserResolveResp;
import dev.patra.identity.api.account.dto.UserSuggestReq;
import dev.patra.identity.api.account.dto.UserSuggestionsResp;
import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummariesResp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 用户内部 API 契约：批量用户摘要（PAP-108），interaction 拼收藏夹所有者、判公开收藏夹对访客是否可见时调；
/// @ 提及支撑（PAP-118），interaction 做 @ 联想、写评论时把 `@用户名` 换成用户 ID 时调；批量收件信息（PAP-142），
/// notification 的退订落地页取脱敏邮箱、发提醒邮件时取收件地址时调。
///
/// 此接口同时用于：
///
/// - **服务端**：`UserEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：调用方通过 HTTP Interface 代理调用（`lb://patra-identity`，不经网关）
///
/// 端点：
///
/// - POST /_internal/users/summaries - 一次最多 100 个用户 ID，按请求顺序返回摘要与 `profileVisible`
/// - POST /_internal/users/suggest - 按用户名或昵称前缀列出主页对访客可见的正常账号，最多 20 个
/// - POST /_internal/users/resolve/by-usernames - 一次最多 100 个用户名，含 90 天保留期内的旧名
/// - POST /_internal/users/recipients - 一次最多 100 个用户 ID，按请求顺序返回邮箱、是否已验证、状态、用户名与显示名
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

  /// @ 联想：按用户名前缀与昵称前缀列出主页对访客可见的正常账号（PAP-118）。
  ///
  /// @param request 前缀、可空的访客 ID 与条数上限（1 ~ 20）
  /// @return 用户名命中的在前、昵称命中的在后，最多条数上限个，不含访客本人
  @PostExchange("/suggest")
  UserSuggestionsResp suggest(@RequestBody UserSuggestReq request);

  /// 按用户名批量解析：先当前用户名，再 90 天保留期内的旧名（PAP-118）。
  ///
  /// @param request 用户名原文，最多 100 个
  /// @return 解析到的那些，按请求顺序，回显原文、带账号状态
  @PostExchange("/resolve/by-usernames")
  UserResolveResp resolveByUsernames(@RequestBody UserResolveByUsernamesReq request);

  /// 批量收件信息（PAP-142）。
  ///
  /// @param request 用户 ID（≤ 100，可重复）
  /// @return 收件信息，按去重后的请求顺序；identity 不认识的 ID 省略；已注销的只有 ID 与状态
  @PostExchange("/recipients")
  UserRecipientsResp recipients(@RequestBody UserRecipientsReq request);
}
