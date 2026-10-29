package dev.patra.catalog.api.venue.endpoint;

import dev.patra.catalog.api.venue.dto.VenueResolveByIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveByNlmIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 期刊内部 API 契约：interaction 的对象登记表按期刊 ID 首次解析、按 NLM ID 每日对账（PAP-117）。
///
/// 此接口同时用于：
///
/// - **服务端**：`VenueEndpointImpl` 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用（`RestClientFactory.createProxy`）
///
/// 端点：
///
/// - POST /_internal/venues/resolve/by-ids - 按期刊 ID 批量解析（1 ~ 100）
/// - POST /_internal/venues/resolve/by-nlm-ids - 按 NLM ID 批量解析（1 ~ 100，主、副 NLM 都可以）
///
/// 每个请求键一行，查不到的（含非期刊类型）`exists = false`；软删的期刊照样返回，`state = DELETED`。
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(
    url = "/_internal/venues",
    accept = "application/json",
    contentType = "application/json")
public interface VenueEndpoint {

  /// 按期刊 ID 批量解析。
  ///
  /// @param request 期刊主键列表
  /// @return 每个去重后的键一行，按请求顺序
  @PostExchange("/resolve/by-ids")
  VenueResolveResp resolveByIds(@Valid @RequestBody VenueResolveByIdsReq request);

  /// 按 NLM ID 批量解析：返回的 `nlmId` 总是主 NLM，请求副 NLM 时与 `key` 不同。
  ///
  /// @param request NLM ID 列表
  /// @return 每个去重后的键一行，按请求顺序
  @PostExchange("/resolve/by-nlm-ids")
  VenueResolveResp resolveByNlmIds(@Valid @RequestBody VenueResolveByNlmIdsReq request);
}
