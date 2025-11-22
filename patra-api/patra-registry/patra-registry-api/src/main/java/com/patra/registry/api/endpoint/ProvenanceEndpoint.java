package com.patra.registry.api.endpoint;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/// Provenance 内部 API 契约接口,定义数据源元数据和配置查询端点。
/// 
/// 通过 Feign 客户端集成向内部微服务暴露数据源元数据和有效配置查询能力。
/// 
/// 端点:
/// 
/// - GET /_internal/provenances - 列出所有数据源
///   - GET /_internal/provenances/{code} - 获取单个数据源
///   - GET /_internal/provenances/{code}/config - 加载配置聚合
/// 
/// @author linqibin
/// @since 0.1.0
public interface ProvenanceEndpoint {

  String BASE_PATH = "/_internal/provenances";

  /// 列出所有可用数据源。
/// 
/// @return 数据源元数据列表
  @GetMapping(BASE_PATH)
  List<ProvenanceResp> listProvenances();

  /// 根据代码获取单个数据源。
/// 
/// @param code 数据源代码
/// @return 数据源元数据
  @GetMapping(BASE_PATH + "/{code}")
  ProvenanceResp getProvenance(@PathVariable("code") ProvenanceCode code);

  /// 加载数据源的聚合配置。
/// 
/// 通过解析时态切片并组装所有配置维度到统一视图来检索有效配置。
/// 
/// @param code 数据源代码
/// @param operationType 操作类型(如 HARVEST/UPDATE),`null` 表示所有类型
/// @param at 查询有效配置的时间点,`null` 默认为当前时间
/// @return 聚合数据源配置
  @GetMapping(BASE_PATH + "/{code}/config")
  ProvenanceConfigResp getConfiguration(
      @PathVariable("code") ProvenanceCode code,
      @RequestParam(value = "operationType", required = false) String operationType,
      @RequestParam(value = "at", required = false) Instant at);
}
