package com.patra.registry.api.endpoint;

import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/// Provenance 内部 API 契约接口，定义数据源元数据和配置查询端点。
///
/// 此接口同时用于：
///
/// - **服务端**：Controller 实现此接口，Spring MVC 自动识别 `@HttpExchange` 注解
/// - **客户端**：通过 HTTP Interface 代理调用远程服务
///
/// 端点：
///
/// - GET /_internal/provenances - 列出所有数据源
/// - GET /_internal/provenances/{code} - 获取单个数据源
/// - GET /_internal/provenances/{code}/config - 加载配置聚合
///
/// @author linqibin
/// @since 0.1.0
@HttpExchange(url = "/_internal/provenances", accept = "application/json")
public interface ProvenanceEndpoint {

  /// 列出所有可用数据源。
  ///
  /// @return 数据源元数据列表
  @GetExchange
  List<ProvenanceResp> listProvenances();

  /// 根据代码获取单个数据源。
  ///
  /// @param code 数据源代码
  /// @return 数据源元数据
  @GetExchange("/{code}")
  ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

  /// 加载数据源的聚合配置。
  ///
  /// 通过解析时态切片并组装所有配置维度到统一视图来检索有效配置。
  ///
  /// @param code 数据源代码
  /// @param operationType 操作类型（如 HARVEST/UPDATE），`null` 表示所有类型
  /// @param at 查询有效配置的时间点，`null` 默认为当前时间
  /// @return 聚合数据源配置
  @GetExchange("/{code}/config")
  ProvenanceConfigResp getConfiguration(
      @PathVariable("code") ProvenanceCode code,
      @RequestParam(value = "operationType", required = false) String operationType,
      @RequestParam(value = "at", required = false) Instant at);
}
