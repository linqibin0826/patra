package dev.patra.catalog.api.organization.endpoint;

import dev.patra.catalog.api.organization.dto.OrganizationResp;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// 机构内部 API 契约：按 ROR ID 取机构摘要（identity 保存资料时校验 ID 并取名称快照）。
///
/// 此接口同时用于：
///
/// - **服务端**：`OrganizationEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用远程服务（`RestClientFactory.createProxy`）
///
/// 端点：
///
/// - GET /_internal/organizations/{rorId} - 按 9 位裸 ROR ID 取摘要；格式不对、不存在或非 active 为 404
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(url = "/_internal/organizations", accept = "application/json")
public interface OrganizationEndpoint {

  /// 按 9 位裸 ROR ID 取机构摘要。
  ///
  /// @param rorId 裸 ROR ID（`^0[a-z0-9]{8}$`）
  /// @return 机构摘要
  @GetExchange("/{rorId}")
  OrganizationResp getOrganization(@PathVariable("rorId") String rorId);
}
