package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 分页配置值对象,定义页码、游标、令牌、滚动等分页参数和响应提取规则。
///
/// **不可变性**:此对象一旦创建不可修改,通过值语义比较相等性。
///
/// **业务约束**:
///
/// - 配置ID和数据源ID必须为正整数
///   - 生效时间(effectiveFrom)不可为空,失效时间(effectiveTo)为null表示永久有效
///   - 分页模式(paginationModeCode)不可为空白,支持PAGE_NUMBER/CURSOR/TOKEN/SCROLL
///   - 操作类型(operationType)为null时表示适用于所有操作(HARVEST/UPDATE/BACKFILL)
///   - 每个端点定义最多有一个当前有效配置,端点级配置优先于数据源级配置
///
/// **业务语义**:
///
/// - PAGE_NUMBER模式:基于页码和页大小的传统分页
///   - CURSOR模式:基于游标的流式分页,适用于大数据集
///   - TOKEN模式:基于令牌的分页,常见于OAuth保护的API
///   - SCROLL模式:基于滚动ID的深度分页,适用于Elasticsearch等
///   - 支持通过JSONPath/XPath提取响应中的分页控制信息
///
/// @param id 配置主键,唯一标识此分页配置,必须为正整数
/// @param provenanceId 数据源ID外键,引用`reg_provenance.id`,必须为正整数
/// @param operationType 操作类型,取值为`HARVEST/UPDATE/BACKFILL`,null表示适用于所有操作
/// @param effectiveFrom 配置生效时间(包含),标记此配置开始生效的时刻,不可为null
/// @param effectiveTo 配置失效时间(不包含),null表示永久有效
/// @param paginationModeCode 分页模式代码(字典值),取值为`PAGE_NUMBER/CURSOR/TOKEN/SCROLL`,不可为空白
/// @param pageSizeValue 页大小,用于PAGE_NUMBER/SCROLL模式,null时使用应用默认值
/// @param maxPagesPerExecution 单次执行最大页数,用于限制深度分页,可为null
/// @param sortFieldParamName 排序字段参数名称,可为null
/// @param sortingDirection 排序方向,0表示降序(DESC),1表示升序(ASC),可为null
/// @author Patra Team
/// @since 2.0
public record PaginationConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String paginationModeCode,
    Integer pageSizeValue,
    Integer maxPagesPerExecution,
    String sortFieldParamName,
    Integer sortingDirection) {
  /// 规范构造器,强制执行分页配置的业务约束。
  ///
  /// 验证规则:
  ///
  /// - 配置ID和数据源ID必须为正整数
  ///   - 生效时间不可为空
  ///   - 分页模式不可为空白
  ///   - 所有字符串字段自动trim去除首尾空白
  ///
  /// @throws DomainValidationException 如果验证失败
  public PaginationConfig(
      Long id,
      Long provenanceId,
      String operationType,
      Instant effectiveFrom,
      Instant effectiveTo,
      String paginationModeCode,
      Integer pageSizeValue,
      Integer maxPagesPerExecution,
      String sortFieldParamName,
      Integer sortingDirection) {
    DomainValidationException.positive(id, "Pagination config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    String modeTrimmed =
        DomainValidationException.notBlank(paginationModeCode, "Pagination mode code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.paginationModeCode = modeTrimmed;
    this.pageSizeValue = pageSizeValue;
    this.maxPagesPerExecution = maxPagesPerExecution;
    this.sortFieldParamName = sortFieldParamName != null ? sortFieldParamName.trim() : null;
    this.sortingDirection = sortingDirection;
  }
}
