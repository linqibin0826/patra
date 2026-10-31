package dev.patra.interaction.api.subject.endpoint;

import dev.patra.interaction.api.subject.dto.SubjectViewsReq;
import dev.patra.interaction.api.subject.dto.SubjectViewsResp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 登记对象的内部 API 契约（PAP-143）：通知服务读提醒列表、拼提醒邮件时，按登记表 ID 现查对象的展示信息（门户链接
/// 里的 key、标题、期刊、是否还在、所有者）。展示信息不进事件：文献的 key 在 catalog 重导后会变，读时现查才不会过期。
///
/// 此接口同时用于：
///
/// - **服务端**：`SubjectEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：调用方通过 HTTP Interface 代理调用（`lb://patra-interaction`，不经网关）
///
/// 端点：
///
/// - POST /_internal/subjects/views - 一次最多 100 个登记表 ID，按请求顺序返回视图，查不到的省略
@HttpExchange(
    url = "/_internal/subjects",
    accept = "application/json",
    contentType = "application/json")
public interface SubjectEndpoint {

  /// 按登记表 ID 批量取对象视图。
  ///
  /// @param request 登记表 ID（1 ~ 100 个，重复的只算一次）
  /// @return 视图，按去重后的请求顺序；查不到的省略
  @PostExchange("/views")
  SubjectViewsResp views(@RequestBody SubjectViewsReq request);
}
