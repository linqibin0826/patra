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
import java.util.Optional;

/**
 * Provenance 配置聚合：封装来源、端点及各维度配置的组合视图。
 *
 * <p>只读聚合，用于 CQRS 查询侧。封装特定来源在特定时间点的完整配置状态，
 * 包括端点定义、HTTP配置、重试策略、限流配置等各个维度。</p>
 *
 * <p>支持SOURCE级别和TASK级别的配置组合，遵循"TASK优先，SOURCE回退"的策略。</p>
 *
 * @author linqibin
 * @since 0.1.0
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

    /** 是否配置了分页策略。 */
    public boolean hasPagination() {
        return pagination != null;
    }

    /** 是否配置了HTTP策略。 */
    public boolean hasHttpConfig() {
        return http != null;
    }

    /** 是否配置了批处理策略。 */
    public boolean hasBatching() {
        return batching != null;
    }

    /** 是否配置了重试策略。 */
    public boolean hasRetry() {
        return retry != null;
    }

    /** 是否配置了限流策略。 */
    public boolean hasRateLimit() {
        return rateLimit != null;
    }

    /** 是否有可用凭证。 */
    public boolean hasCredentials() {
        return !credentials.isEmpty();
    }

    /** 查找指定名称的凭证。 */
    public Optional<Credential> findCredentialByName(String credentialName) {
        if (credentialName == null || credentialName.trim().isEmpty()) {
            return Optional.empty();
        }
        return credentials.stream()
                .filter(cred -> credentialName.trim().equals(cred.credentialName()))
                .findFirst();
    }

    /** 获取默认凭证（优先选择标记为默认的）。 */
    public Optional<Credential> getDefaultCredential() {
        // 先找标记为默认的
        Optional<Credential> defaultCred = credentials.stream()
                .filter(Credential::defaultPreferred)
                .findFirst();
        if (defaultCred.isPresent()) {
            return defaultCred;
        }
        // 如果没有默认的，返回第一个
        return credentials.isEmpty() ? Optional.empty() : Optional.of(credentials.get(0));
    }

    /** 是否为完整配置（包含所有必要的配置维度）。 */
    public boolean isComplete() {
        return provenance != null && provenance.isActive();
    }
}
