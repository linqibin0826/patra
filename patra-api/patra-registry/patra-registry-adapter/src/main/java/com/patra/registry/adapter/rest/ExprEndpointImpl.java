package com.patra.registry.adapter.rest;

import com.patra.registry.adapter.rest.converter.ExprApiConverter;
import com.patra.registry.api.dto.expr.ExprSnapshotResp;
import com.patra.registry.api.endpoint.ExprEndpoint;
import com.patra.registry.app.service.ExprQueryOrchestrator;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expression REST 控制器,提供表达式快照查询 HTTP API。
 *
 * <p>端点:
 *
 * <ul>
 *   <li>GET /_internal/expr/snapshot - 获取完整表达式快照(字段定义、渲染规则、参数映射、能力)
 * </ul>
 *
 * <p>职责:
 *
 * <ul>
 *   <li>接收 HTTP 请求参数并验证
 *   <li>调用 {@link ExprQueryOrchestrator} 执行用例
 *   <li>通过 {@link ExprApiConverter} 转换为 API 响应 DTO
 * </ul>
 *
 * <p>权限: 内部服务间调用(/_internal 路径)
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExprEndpointImpl implements ExprEndpoint {

  private final ExprQueryOrchestrator orchestrator;
  private final ExprApiConverter converter;

  /**
   * 获取表达式快照(支持时态切片)。
   *
   * <p>快照内容包括:
   *
   * <ul>
   *   <li>字段定义({@code ExprFieldResp})
   *   <li>渲染规则({@code ExprRenderRuleResp})
   *   <li>参数映射({@code ApiParamMappingResp})
   *   <li>能力配置({@code ExprCapabilityResp})
   * </ul>
   *
   * @param provenanceCode 数据源代码
   * @param operationType 操作类型,为 {@code null} 时表示所有操作
   * @param endpointName 端点名称过滤器,为 {@code null} 时表示所有端点
   * @param at 时态切片时间点,为 {@code null} 时默认为当前时间
   * @return 表达式快照响应 DTO
   */
  @Override
  public ExprSnapshotResp getSnapshot(
      String provenanceCode, String operationType, String endpointName, Instant at) {
    log.debug(
        "Received expression snapshot request for provenance [{}], operationType [{}], endpoint [{}], at [{}]",
        provenanceCode,
        operationType,
        endpointName,
        at);

    ExprSnapshotQuery snapshot =
        orchestrator.loadSnapshot(provenanceCode, operationType, endpointName, at);

    return converter.toResp(snapshot);
  }
}
