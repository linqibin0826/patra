package com.patra.starter.provenance.common.provider;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import lombok.Builder;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

/// 传递给 {@link ProvenanceDataProvider} 实现的不可变请求对象
///
/// **设计理念:**
///
/// - `executionParams`: 构建上游 API 请求所需的全部信息(查询条件 + 完整参数包括分页)
///   - `config`: HTTP、重试、限流等运行时配置
///
/// @param config 应用于本次执行的合并配置
/// @param executionParams 批次执行参数(查询 + 完整参数)
@Builder
@JsonDeserialize(builder = ProviderRequest.ProviderRequestBuilder.class)
public record ProviderRequest(ProvenanceConfig config, BatchExecutionParams executionParams) {

  /// 创建记录时验证不变量
  ///
  /// @param config 运行时配置
  /// @param executionParams 批次执行参数
  public ProviderRequest {
    if (executionParams == null) {
      throw new IllegalArgumentException("必须提供 executionParams");
    }
  }

  /// Jackson 3.x Builder 配置。
  @JsonPOJOBuilder(withPrefix = "")
  public static class ProviderRequestBuilder {}
}
