package com.patra.starter.core.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 平台级错误处理配置项。
 *
 * <p>用于定义错误码上下文、解析行为以及熔断与观测策略。</p>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@ConfigurationProperties(prefix = "patra.error")
public class ErrorProperties {

    /** 是否启用平台级错误解析 */
    private boolean enabled = true;

    /** 错误码上下文前缀（必填，例如：REG、INGEST 等） */
    private String contextPrefix;

    /** 核心解析相关配置 */
    private EngineProperties engine = new EngineProperties();

    /** 观测与指标相关配置 */
    private ObservationProperties observation = new ObservationProperties();

    /** 熔断保护相关配置 */
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    /** 引擎行为配置 */
    @Data
    public static class EngineProperties {
        /** 因果链最大回溯深度，防止递归过深 */
        private int maxCauseDepth = 10;
        /** 是否启用基于 Trait 的语义解析 */
        private boolean enableTraitMapping = true;
        /** 是否启用命名启发式 */
        private boolean enableNamingHeuristic = true;
    }

    /** 观测配置 */
    @Data
    public static class ObservationProperties {
        /** 是否启用 Micrometer 观测与结构化日志 */
        private boolean enabled = true;
        /** 解析耗时慢指标阈值（毫秒） */
        private long slowThresholdMs = 200L;
        /** 是否在慢调用时输出 WARN 日志 */
        private boolean logSlowResolution = true;
    }

    /** 熔断配置 */
    @Data
    public static class CircuitBreakerProperties {
        /** 是否启用熔断保护 */
        private boolean enabled = true;
        /** 熔断失败率阈值（百分比 0-100） */
        private float failureRateThreshold = 50.0f;
        /** 熔断窗口内的最小调用次数 */
        private int minimumNumberOfCalls = 20;
        /** 滑动窗口大小 */
        private int slidingWindowSize = 50;
        /** 半开状态允许的探测调用次数 */
        private int permittedCallsInHalfOpenState = 5;
        /** 开启状态等待时间 */
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
    }
}
