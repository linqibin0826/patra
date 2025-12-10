package com.patra.registry.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.app.converter.ExprQueryAssembler;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import com.patra.registry.domain.model.vo.expr.ExprSnapshot;
import com.patra.registry.domain.port.ExprRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 表达式配置查询服务。
///
/// 职责：
///
/// - 提供表达式快照的查询能力
///   - 协调领域仓储检索表达式元数据
///   - 将领域对象转换为查询 DTO 供外部消费
///
/// 典型用例：
///
/// - 加载数据源的完整表达式快照(字段字典、能力、渲染规则、API 参数映射)
///   - 支持按操作类型和端点名称过滤
///   - 支持时态切片查询
///
/// 设计模式：查询服务,不包含业务逻辑,仅负责用例协调和对象转换。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class ExprQueryService {

  private final ExprRepository exprRepository;
  private final ExprQueryAssembler assembler;

  /// 加载数据源的完整表达式快照。
  ///
  /// 用例说明：
  ///
  /// - 通过时态切片机制查询指定时间点的有效表达式配置
  ///   - 支持按操作类型(如 HARVEST、UPDATE)和端点名称(如 SEARCH、DETAIL)过滤
  ///   - 整合字段定义、能力、渲染规则和 API 参数映射为统一快照
  ///
  /// 事务边界：只读查询,无需事务管理。
  ///
  /// @param provenanceCode 数据源代码字符串(如 "PUBMED")
  /// @param operationType 操作类型(如 HARVEST/UPDATE);`null` 表示查询所有类型
  /// @param endpointName 端点名称(如 SEARCH/DETAIL);`null` 表示查询所有端点
  /// @param at 查询有效配置的时间点;`null` 默认使用当前时间
  /// @return 表达式快照查询 DTO
  public ExprSnapshotQuery loadSnapshot(
      String provenanceCode, String operationType, String endpointName, Instant at) {
    ProvenanceCode code = ProvenanceCode.parse(provenanceCode);

    ExprSnapshot domainSnapshot =
        exprRepository.loadSnapshot(code, operationType, endpointName, at);
    ExprSnapshotQuery snapshot = assembler.toQuery(domainSnapshot);

    log.info(
        "加载表达式快照成功 - 数据源: [{}], 操作类型: [{}], 端点: [{}] | 字段: {}, 能力: {}, 渲染规则: {}, API参数: {}",
        code.getCode(),
        operationType,
        endpointName,
        snapshot.fields().size(),
        snapshot.capabilities().size(),
        snapshot.renderRules().size(),
        snapshot.apiParamMappings().size());

    return snapshot;
  }
}
