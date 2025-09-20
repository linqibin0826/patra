package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.Credential;
import com.patra.registry.domain.model.vo.provenance.EndpointDefinition;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;

import java.util.List;

/**
 * Provenance 配置聚合：封装来源、端点及各维度配置的组合视图。
 */
public record ProvenanceConfiguration(
        Provenance provenance,
        EndpointDefinition endpoint,
        WindowOffsetConfig windowOffset,
        PaginationConfig pagination,
        HttpConfig http,
        BatchingConfig batching,
        RetryConfig retry,
        RateLimitConfig rateLimit,
        List<Credential> credentials
) {
    public ProvenanceConfiguration {
        if (provenance == null) {
            throw new IllegalArgumentException("Provenance must not be null");
        }
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
    }

    /** 是否存在端点定义。 */
    public boolean hasEndpoint() {
        return endpoint != null;
    }

    /** 是否配置了窗口策略。 */
    public boolean hasWindowOffset() {
        return windowOffset != null;
    }
}
