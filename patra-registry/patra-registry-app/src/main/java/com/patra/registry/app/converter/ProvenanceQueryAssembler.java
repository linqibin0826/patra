package com.patra.registry.app.converter;

import com.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import com.patra.registry.domain.model.read.provenance.BatchingConfigQuery;
import com.patra.registry.domain.model.read.provenance.HttpConfigQuery;
import com.patra.registry.domain.model.read.provenance.PaginationConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.read.provenance.RateLimitConfigQuery;
import com.patra.registry.domain.model.read.provenance.RetryConfigQuery;
import com.patra.registry.domain.model.read.provenance.WindowOffsetQuery;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// 数据源领域对象到查询 DTO 转换器。
/// 
/// 职责：
/// 
/// - 将领域值对象和聚合根转换为只读查询 DTO
///   - 支持 REST API 和 Feign 客户端的数据契约
///   - 隔离领域模型和外部表示层
/// 
/// 设计模式：MapStruct 自动生成转换代码,避免手写样板映射。
/// 
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceQueryAssembler {
  /// 转换数据源值对象为查询 DTO。
/// 
/// @param provenance 领域层的数据源值对象
/// @return 查询 DTO,包含数据源元数据
  ProvenanceQuery toQuery(Provenance provenance);

  /// 转换时间窗口偏移配置为查询 DTO。
/// 
/// @param config 领域层的窗口偏移配置
/// @return 查询 DTO,描述偏移窗口规则
  WindowOffsetQuery toQuery(WindowOffsetConfig config);

  /// 转换分页配置为查询 DTO。
/// 
/// @param config 领域层的分页配置
/// @return 查询 DTO,描述分页策略
  PaginationConfigQuery toQuery(PaginationConfig config);

  /// 转换 HTTP 配置为查询 DTO。
/// 
/// @param config 领域层的 HTTP 配置
/// @return 查询 DTO,包含 HTTP 交互设置
  HttpConfigQuery toQuery(HttpConfig config);

  /// 转换批处理配置为查询 DTO。
/// 
/// @param config 领域层的批处理配置
/// @return 查询 DTO,描述批处理行为
  BatchingConfigQuery toQuery(BatchingConfig config);

  /// 转换重试配置为查询 DTO。
/// 
/// @param config 领域层的重试配置
/// @return 查询 DTO,包含重试策略属性
  RetryConfigQuery toQuery(RetryConfig config);

  /// 转换速率限制配置为查询 DTO。
/// 
/// @param config 领域层的速率限制配置
/// @return 查询 DTO,描述节流规则
  RateLimitConfigQuery toQuery(RateLimitConfig config);

  // 凭证维度已移除

  /// 转换数据源配置聚合根为查询 DTO。
/// 
/// @param configuration 领域层的数据源配置聚合根
/// @return 查询 DTO,整合所有维度配置
  ProvenanceConfigQuery toQuery(ProvenanceConfiguration configuration);
}
