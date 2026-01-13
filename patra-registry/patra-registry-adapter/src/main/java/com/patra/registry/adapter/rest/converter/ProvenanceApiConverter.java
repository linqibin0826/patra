package com.patra.registry.adapter.rest.converter;

import com.patra.registry.api.dto.provenance.BatchingConfigResp;
import com.patra.registry.api.dto.provenance.HttpConfigResp;
import com.patra.registry.api.dto.provenance.PaginationConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.api.dto.provenance.RateLimitConfigResp;
import com.patra.registry.api.dto.provenance.RetryConfigResp;
import com.patra.registry.api.dto.provenance.WindowOffsetResp;
import com.patra.registry.domain.model.read.provenance.BatchingConfigQuery;
import com.patra.registry.domain.model.read.provenance.HttpConfigQuery;
import com.patra.registry.domain.model.read.provenance.PaginationConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.read.provenance.RateLimitConfigQuery;
import com.patra.registry.domain.model.read.provenance.RetryConfigQuery;
import com.patra.registry.domain.model.read.provenance.WindowOffsetQuery;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// Provenance 查询 DTO 到 API 响应 DTO 的转换器。
///
/// 使用 MapStruct 自动生成转换代码,将读侧领域查询对象转换为外部 API 契约 DTO,供其他微服务的 HTTP Interface 客户端消费。
///
/// 转换方法:
///
/// - {@link #toResp(ProvenanceQuery)} - 转换单个数据源查询对象
///   - {@link #toResp(List)} - 转换数据源查询对象列表
///   - {@link #toResp(ProvenanceConfigQuery)} - 转换配置聚合查询对象
///   - {@link #toResp(WindowOffsetQuery)} - 转换时间窗口偏移查询对象
///   - {@link #toResp(PaginationConfigQuery)} - 转换分页配置查询对象
///   - {@link #toResp(HttpConfigQuery)} - 转换 HTTP 配置查询对象
///   - {@link #toResp(BatchingConfigQuery)} - 转换批处理配置查询对象
///   - {@link #toResp(RetryConfigQuery)} - 转换重试配置查询对象
///   - {@link #toResp(RateLimitConfigQuery)} - 转换速率限制配置查询对象
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceApiConverter {

  /// 转换单个数据源查询对象为 API 响应 DTO。
  ///
  /// @param query 应用层产生的数据源查询对象
  /// @return RPC 契约暴露的数据源响应 DTO
  ProvenanceResp toResp(ProvenanceQuery query);

  /// 转换数据源查询对象列表为 API 响应 DTO 列表。
  ///
  /// @param queries 数据源查询对象集合
  /// @return 响应 DTO 列表,保持迭代顺序
  List<ProvenanceResp> toResp(List<ProvenanceQuery> queries);

  /// 转换时间窗口偏移查询对象为 API 响应 DTO。
  ///
  /// @param query 时间窗口偏移查询对象
  /// @return 时间窗口偏移响应 DTO
  WindowOffsetResp toResp(WindowOffsetQuery query);

  /// 转换分页配置查询对象为 API 响应 DTO。
  ///
  /// @param query 分页配置查询对象
  /// @return 分页配置响应 DTO
  PaginationConfigResp toResp(PaginationConfigQuery query);

  /// 转换 HTTP 配置查询对象为 API 响应 DTO。
  ///
  /// @param query HTTP 配置查询对象
  /// @return HTTP 配置响应 DTO
  HttpConfigResp toResp(HttpConfigQuery query);

  /// 转换批处理配置查询对象为 API 响应 DTO。
  ///
  /// @param query 批处理配置查询对象
  /// @return 批处理配置响应 DTO
  BatchingConfigResp toResp(BatchingConfigQuery query);

  /// 转换重试配置查询对象为 API 响应 DTO。
  ///
  /// @param query 重试配置查询对象
  /// @return 重试配置响应 DTO
  RetryConfigResp toResp(RetryConfigQuery query);

  /// 转换速率限制配置查询对象为 API 响应 DTO。
  ///
  /// @param query 速率限制配置查询对象
  /// @return 速率限制配置响应 DTO
  RateLimitConfigResp toResp(RateLimitConfigQuery query);

  // Credential dimension removed

  /// 转换聚合数据源配置查询对象为 API 响应 DTO。
  ///
  /// @param query 聚合数据源配置查询对象
  /// @return 配置响应 DTO,整合所有配置维度
  ProvenanceConfigResp toResp(ProvenanceConfigQuery query);
}
