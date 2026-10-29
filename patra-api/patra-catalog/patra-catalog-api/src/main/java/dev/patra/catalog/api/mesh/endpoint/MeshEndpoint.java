package dev.patra.catalog.api.mesh.endpoint;

import dev.patra.catalog.api.mesh.dto.MeshDescriptorResp;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// MeSH 内部 API 契约：按 UI 取主题词摘要（identity 保存关注领域时校验 UI 并取英文名快照）。
///
/// 此接口同时用于：
///
/// - **服务端**：`MeshEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用远程服务（`RestClientFactory.createProxy`）
///
/// 端点：
///
/// - GET /_internal/mesh/descriptors/{ui} - 按主题词 UI 取摘要（不限 class）；格式不对或不存在为 404
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(url = "/_internal/mesh", accept = "application/json")
public interface MeshEndpoint {

  /// 按 UI 取主题词摘要。
  ///
  /// @param ui 主题词 UI（`^D\d{6}(\d{3})?$`）
  /// @return 主题词摘要
  @GetExchange("/descriptors/{ui}")
  MeshDescriptorResp getDescriptor(@PathVariable("ui") String ui);
}
