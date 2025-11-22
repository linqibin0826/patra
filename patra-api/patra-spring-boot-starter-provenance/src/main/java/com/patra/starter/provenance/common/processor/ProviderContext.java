package com.patra.starter.provenance.common.processor;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/// Provider上下文
///
/// 封装Processor处理数据时所需的上下文信息。
///
/// **包含信息**：
///
/// - 配置信息（超时、重试、限流等）
///   - 客户端实例（如PubMedClient、DoajClient）
///   - 扩展属性（自定义上下文信息）
///
/// @author linqibin
/// @since 0.1.0
@Value
@Builder
public class ProviderContext {

  /// 配置信息
  ProvenanceConfig config;

  /// 客户端实例（如PubMedClient、DoajClient）
  Object client;

  /// 扩展属性
  Map<String, Object> attributes;

  /// 获取类型安全的客户端实例
  ///
  /// @param clientClass 客户端类型
  /// @param <T> 客户端类型
  /// @return 客户端实例
  /// @throws IllegalStateException 如果客户端类型不匹配
  @SuppressWarnings("unchecked")
  public <T> T getClient(Class<T> clientClass) {
    if (clientClass.isInstance(client)) {
      return (T) client;
    }
    throw new IllegalStateException("Client type mismatch");
  }
}
