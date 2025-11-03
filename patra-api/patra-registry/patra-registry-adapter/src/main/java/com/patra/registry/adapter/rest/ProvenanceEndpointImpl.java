package com.patra.registry.adapter.rest;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.rest.converter.ProvenanceApiConverter;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.api.endpoint.ProvenanceEndpoint;
import com.patra.registry.app.service.ProvenanceConfigOrchestrator;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provenance REST 控制器,提供数据源元数据和配置查询 HTTP API。
 *
 * <p>端点:
 *
 * <ul>
 *   <li>GET /_internal/provenances - 列出所有数据源
 *   <li>GET /_internal/provenances/{code} - 根据代码获取单个数据源
 *   <li>GET /_internal/provenances/{code}/config - 加载完整配置聚合(支持时态切片)
 * </ul>
 *
 * <p>职责:
 *
 * <ul>
 *   <li>接收 HTTP 请求参数并验证
 *   <li>调用 {@link ProvenanceConfigOrchestrator} 执行用例
 *   <li>通过 {@link ProvenanceApiConverter} 转换为 API 响应 DTO
 *   <li>处理异常并抛出 {@link ProvenanceNotFoundException}
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
public class ProvenanceEndpointImpl implements ProvenanceEndpoint {

  private final ProvenanceConfigOrchestrator orchestrator;
  private final ProvenanceApiConverter converter;

  /**
   * 列出所有可用数据源。
   *
   * @return 数据源响应 DTO 列表
   */
  @Override
  public List<ProvenanceResp> listProvenances() {
    log.debug("Received request to list all provenances");

    List<ProvenanceResp> response = converter.toResp(orchestrator.listProvenances());

    return response;
  }

  /**
   * 根据代码获取单个数据源。
   *
   * @param code 数据源代码
   * @return 数据源响应 DTO
   * @throws ProvenanceNotFoundException 当数据源不存在时
   */
  @Override
  public ProvenanceResp getProvenance(ProvenanceCode code) {
    log.debug("Received request to get provenance for code [{}]", code.getCode());

    ProvenanceResp response =
        orchestrator
            .findProvenance(code)
            .map(converter::toResp)
            .orElseThrow(
                () ->
                    new ProvenanceNotFoundException(
                        String.format("Provenance not found for code [%s]", code.getCode())));

    return response;
  }

  /**
   * 加载数据源的聚合配置(支持时态切片)。
   *
   * <p>通过 operationType 和 at 参数进行配置过滤:
   *
   * <ul>
   *   <li>operationType 为 null 表示所有操作类型
   *   <li>at 为 null 表示当前时间
   * </ul>
   *
   * @param code 数据源代码
   * @param operationType 操作类型(HARVEST/UPDATE 等),为 {@code null} 时表示所有操作
   * @param at 时态切片时间点,为 {@code null} 时默认为当前时间
   * @return 数据源配置响应 DTO,包含 7 个配置维度
   * @throws ProvenanceNotFoundException 当配置不存在时
   */
  @Override
  public ProvenanceConfigResp getConfiguration(
      ProvenanceCode code, String operationType, Instant at) {
    log.debug(
        "Received provenance configuration request for code [{}], operationType [{}], at [{}]",
        code.getCode(),
        operationType,
        at);

    ProvenanceConfigResp response =
        orchestrator
            .loadConfiguration(code, operationType, at)
            .map(converter::toResp)
            .orElseThrow(
                () ->
                    new ProvenanceNotFoundException(
                        String.format(
                            "Provenance configuration not found for code [%s] and operationType [%s]",
                            code.getCode(), operationType)));

    return response;
  }
}
