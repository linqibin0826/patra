package dev.linqibin.patra.registry.api.endpoint;

import dev.linqibin.patra.registry.api.dto.expr.ExprSnapshotResp;
import java.time.Instant;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// Expression 内部 API 契约接口，定义表达式快照查询端点。
///
/// 此接口同时用于：
///
/// - **服务端**：Controller 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用远程服务
///
/// 端点：
///
/// - GET /_internal/expr/snapshot - 获取聚合表达式快照
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(url = "/_internal/expr", accept = "application/json")
public interface ExprEndpoint {

  /// 加载数据源的聚合表达式快照。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationType 操作类型，`null` 表示所有操作
  /// @param endpointName 端点名称过滤器，`null` 表示所有端点
  /// @param at 时态切片时间点，`null` 默认为当前时间
  /// @return 聚合表达式快照
  @GetExchange("/snapshot")
  ExprSnapshotResp getSnapshot(
      @RequestParam("provenanceCode") String provenanceCode,
      @RequestParam(value = "operationType", required = false) String operationType,
      @RequestParam(value = "endpointName", required = false) String endpointName,
      @RequestParam(value = "at", required = false) Instant at);
}
