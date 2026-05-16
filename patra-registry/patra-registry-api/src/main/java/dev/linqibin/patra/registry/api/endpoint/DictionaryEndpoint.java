package dev.linqibin.patra.registry.api.endpoint;

import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveReq;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveResp;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/// 字典解析内部 API 契约接口。
///
/// 此接口同时用于：
///
/// - **服务端**：Controller 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用远程服务
///
/// 端点：
///
/// - POST /_internal/dictionaries/resolve - 批量解析字典值
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(
    url = "/_internal/dictionaries",
    accept = "application/json",
    contentType = "application/json")
public interface DictionaryEndpoint {

  /// 批量解析字典值。
  ///
  /// @param request 批量解析请求
  /// @return 批量解析结果
  @PostExchange("/resolve")
  DictionaryResolveResp resolve(@RequestBody DictionaryResolveReq request);
}
