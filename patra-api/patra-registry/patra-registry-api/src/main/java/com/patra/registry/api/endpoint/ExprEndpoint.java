package com.patra.registry.api.endpoint;

import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Expression 内部 API 契约接口,定义表达式快照查询端点。
 *
 * <p>通过 Feign 客户端集成向内部微服务暴露表达式配置快照查询能力。
 *
 * <p>端点:
 *
 * <ul>
 *   <li>GET /_internal/expr/snapshot - 获取聚合表达式快照
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExprEndpoint {

  String BASE_PATH = "/_internal/expr";

  /**
   * 加载数据源的聚合表达式快照。
   *
   * @param provenanceCode 数据源代码
   * @param operationType 操作类型,{@code null} 表示所有操作
   * @param endpointName 端点名称过滤器,{@code null} 表示所有端点
   * @param at 时态切片时间点,{@code null} 默认为当前时间
   * @return 聚合表达式快照
   */
  @GetMapping(BASE_PATH + "/snapshot")
  ExprSnapshotResp getSnapshot(
      @RequestParam("provenanceCode") String provenanceCode,
      @RequestParam(value = "operationType", required = false) String operationType,
      @RequestParam(value = "endpointName", required = false) String endpointName,
      @RequestParam(value = "at", required = false) Instant at);
}
