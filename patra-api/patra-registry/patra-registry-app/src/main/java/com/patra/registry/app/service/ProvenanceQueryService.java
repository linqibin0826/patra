package com.patra.registry.app.service;

import com.patra.registry.app.converter.ProvenanceQueryAssembler;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.port.ProvenanceConfigRepository;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 数据源配置查询服务。
///
/// 职责：
///
/// - 提供数据源元数据和配置的查询能力
///   - 协调领域仓储完成数据检索
///   - 将领域对象转换为查询 DTO 供外部客户端消费
///
/// 典型用例：
///
/// - 列出所有可用数据源
///   - 查询单个数据源的元数据
///   - 加载指定时间点的完整配置聚合(支持时态切片)
///
/// 设计模式：查询服务,不包含业务逻辑,仅负责用例协调和对象转换。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceQueryService {

  private final ProvenanceConfigRepository repository;
  private final ProvenanceQueryAssembler assembler;

  /// 列出所有可用数据源。
  ///
  /// 用例说明：查询系统中注册的所有数据源元数据,返回只读 DTO 列表。
  ///
  /// @return 数据源查询 DTO 列表
  public List<ProvenanceQuery> listProvenances() {
    List<ProvenanceQuery> provenances =
        repository.findAllProvenances().stream().map(assembler::toQuery).toList();

    log.info("检索到 {} 个数据源配置", provenances.size());
    return provenances;
  }

  /// 根据代码查找单个数据源。
  ///
  /// 用例说明：通过数据源代码(如 PUBMED)查询对应的元数据。
  ///
  /// @param provenanceCode 数据源代码
  /// @return 查询 DTO 的 Optional,如果找到则包含数据源元数据
  public Optional<ProvenanceQuery> findProvenance(ProvenanceCode provenanceCode) {
    Optional<ProvenanceQuery> result =
        repository.findProvenanceByCode(provenanceCode).map(assembler::toQuery);

    if (result.isPresent()) {
      log.debug("找到数据源配置,代码: [{}]", provenanceCode.getCode());
    } else {
      log.warn("未找到数据源配置,代码: [{}]", provenanceCode.getCode());
    }

    return result;
  }

  /// 加载数据源在特定操作类型下的完整配置聚合。
  ///
  /// 用例说明：
  ///
  /// - 通过时态切片机制查询指定时间点的有效配置
  ///   - 整合所有维度配置(HTTP、分页、重试、速率限制等)为统一视图
  ///   - 支持按操作类型过滤(如 HARVEST、UPDATE)
  ///
  /// 事务边界：只读查询,无需事务管理。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationType 操作类型(如 HARVEST/UPDATE);`null` 表示查询所有类型
  /// @param at 查询有效配置的时间点;`null` 默认使用当前时间
  /// @return 配置查询 DTO 的 Optional,如果数据源存在则包含配置聚合
  public Optional<ProvenanceConfigQuery> loadConfiguration(
      ProvenanceCode provenanceCode, String operationType, Instant at) {
    Instant effectiveTime = at != null ? at : Instant.now();

    Optional<ProvenanceConfigQuery> result =
        repository
            .findProvenanceByCode(provenanceCode)
            .flatMap(
                provenance ->
                    repository
                        .loadConfiguration(provenance.id(), operationType, effectiveTime)
                        .map(assembler::toQuery));

    if (result.isPresent()) {
      log.info(
          "加载配置成功 - 数据源: [{}], 操作类型: [{}], 时间点: [{}]",
          provenanceCode.getCode(),
          operationType,
          effectiveTime);
    } else {
      log.warn(
          "配置未找到 - 数据源: [{}], 操作类型: [{}], 时间点: [{}]",
          provenanceCode.getCode(),
          operationType,
          effectiveTime);
    }

    return result;
  }
}
