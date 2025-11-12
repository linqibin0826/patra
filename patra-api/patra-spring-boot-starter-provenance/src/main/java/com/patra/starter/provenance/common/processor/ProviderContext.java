package com.patra.starter.provenance.common.processor;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.provider.BatchMetadata;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Provider上下文
 *
 * <p>封装Processor处理数据时所需的上下文信息。
 *
 * <p><strong>包含信息</strong>：
 * <ul>
 *   <li>配置信息（超时、重试、限流等）</li>
 *   <li>客户端实例（如PubMedClient、DoajClient）</li>
 *   <li>批次元数据（批次号、游标）</li>
 *   <li>扩展属性（自定义上下文信息）</li>
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
@Value
@Builder
public class ProviderContext {

    /**
     * 配置信息
     */
    ProvenanceConfig config;

    /**
     * 客户端实例（如PubMedClient、DoajClient）
     */
    Object client;

    /**
     * 批次元数据
     */
    BatchMetadata batchMetadata;

    /**
     * 扩展属性
     */
    Map<String, Object> attributes;

    /**
     * 获取类型安全的客户端实例
     *
     * @param clientClass 客户端类型
     * @param <T> 客户端类型
     * @return 客户端实例
     * @throws IllegalStateException 如果客户端类型不匹配
     */
    @SuppressWarnings("unchecked")
    public <T> T getClient(Class<T> clientClass) {
        if (clientClass.isInstance(client)) {
            return (T) client;
        }
        throw new IllegalStateException("Client type mismatch");
    }
}
