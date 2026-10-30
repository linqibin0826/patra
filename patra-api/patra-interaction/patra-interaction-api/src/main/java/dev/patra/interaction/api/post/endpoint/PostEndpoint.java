package dev.patra.interaction.api.post.endpoint;

import dev.patra.interaction.api.post.dto.ExperienceOutlinesReq;
import dev.patra.interaction.api.post.dto.ExperienceOutlinesResp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 投稿经验的内部 API 契约（PAP-143）：通知服务在提醒列表与提醒邮件里写经验的结果行（「大修 → 录用 · 审稿 6 周」）
/// 时，按帖子 ID 批量现查。对象视图（`SubjectEndpoint`）归登记表底座，经验结果归投稿经验子域，各自公开自己的事实。
///
/// 此接口同时用于：
///
/// - **服务端**：`PostEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：调用方通过 HTTP Interface 代理调用（`lb://patra-interaction`，不经网关）
///
/// 端点：
///
/// - POST /_internal/posts/outlines - 一次最多 100 个帖子 ID，按请求顺序返回结果，查不到的省略；已删除的也返回
@HttpExchange(
    url = "/_internal/posts",
    accept = "application/json",
    contentType = "application/json")
public interface PostEndpoint {

  /// 按帖子 ID 批量取经验结果。
  ///
  /// @param request 帖子 ID（1 ~ 100 个，重复的只算一次）
  /// @return 结果，按去重后的请求顺序；查不到的省略
  @PostExchange("/outlines")
  ExperienceOutlinesResp outlines(@RequestBody ExperienceOutlinesReq request);
}
