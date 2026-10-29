package dev.patra.catalog.api.publication.endpoint;

import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveByPmidsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 文献内部 API 契约：interaction 的对象登记表按内部 ID 首次解析、按 PMID 每日对账（PAP-109）。
///
/// 此接口同时用于：
///
/// - **服务端**：`PublicationEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用（`RestClientFactory.createProxy`）
///
/// 端点：
///
/// - POST /_internal/publications/resolve/by-ids - 按内部 ID 批量解析（1 ~ 100）
/// - POST /_internal/publications/resolve/by-pmids - 按 PMID 批量解析（1 ~ 100）
///
/// 每个请求键一行，查不到的 `exists = false`；软删的文献照样返回，`state = DELETED`。
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(
    url = "/_internal/publications",
    accept = "application/json",
    contentType = "application/json")
public interface PublicationEndpoint {

  /// 按内部 ID 批量解析。
  ///
  /// @param request 文献主键列表
  /// @return 每个去重后的键一行，按请求顺序
  @PostExchange("/resolve/by-ids")
  PublicationResolveResp resolveByIds(@Valid @RequestBody PublicationResolveByIdsReq request);

  /// 按 PMID 批量解析。
  ///
  /// @param request PMID 列表
  /// @return 每个去重后的键一行，按请求顺序
  @PostExchange("/resolve/by-pmids")
  PublicationResolveResp resolveByPmids(@Valid @RequestBody PublicationResolveByPmidsReq request);
}
