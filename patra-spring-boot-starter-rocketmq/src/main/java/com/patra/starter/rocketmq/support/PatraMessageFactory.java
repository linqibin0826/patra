package com.patra.starter.rocketmq.support;

import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.rocketmq.model.PatraMessage;

import java.time.Instant;
import java.util.Optional;

/**
 * PatraMessage 构建工厂：负责从 TraceProvider 注入 traceId，并统一默认 occurredAt。
 * <p>
 * 该工厂为可选依赖：若未引入 core-starter 或未配置 TraceProvider，则 traceId 为空。
 * </p>
 */
public class PatraMessageFactory {

    private final TraceProvider traceProvider; // 允许为 null

    public PatraMessageFactory(TraceProvider traceProvider) {
        this.traceProvider = traceProvider;
    }

    public PatraMessageFactory() {
        this.traceProvider = null;
    }

    /**
     * 使用可用的 TraceId 创建消息。
     */
    public <T> PatraMessage<T> wrap(T payload) {
        Optional<String> traceId = traceProvider == null ? Optional.empty() : traceProvider.getCurrentTraceId();
        return PatraMessage.<T>builder()
                .traceId(traceId.orElse(null))
                .occurredAt(Instant.now())
                .payload(payload)
                .build();
    }
}

