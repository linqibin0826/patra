package com.patra.starter.provenance.common.provider;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * 传递给 {@link DataSourceProvider} 实现的不可变请求对象
 *
 * <p><strong>设计理念:</strong>
 *
 * <ul>
 *   <li>{@code executionParams}: 构建上游 API 请求所需的全部信息(查询条件 + 完整参数包括分页)
 *   <li>{@code metadata}: 批次标识和游标状态,用于日志记录和断点续传
 *   <li>{@code config}: HTTP、重试、限流等运行时配置
 * </ul>
 *
 * @param config 应用于本次执行的合并配置
 * @param executionParams 批次执行参数(查询 + 完整参数)
 * @param metadata 批次元数据(批次号、游标)
 */
@Builder
@Jacksonized
public record ProviderRequest(
    ProvenanceConfig config,
    BatchExecutionParams executionParams,
    BatchMetadata metadata) {

  /**
   * 创建记录时验证不变量
   *
   * @param config 运行时配置
   * @param executionParams 批次执行参数
   * @param metadata 批次元数据
   */
  public ProviderRequest {
    if (executionParams == null) {
      throw new IllegalArgumentException("必须提供 executionParams");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("必须提供 metadata");
    }
  }
}
