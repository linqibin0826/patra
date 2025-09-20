package com.patra.starter.core.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 核心错误处理配置项。
 *
 * <p>用于控制错误处理行为与状态映射策略。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.error")
public class ErrorProperties {
    
    /** 是否启用错误处理 */
    private boolean enabled = true;
    
    /** 错误码上下文前缀（如：REG、ORD、INV），必填 */
    private String contextPrefix;
    
    /** 状态映射配置 */
    private MapStatusProperties mapStatus = new MapStatusProperties();
    
    /** 监控与可观测性配置 */
    private MonitoringProperties monitoring = new MonitoringProperties();
    
    /** 状态映射相关配置。 */
    @Data
    public static class MapStatusProperties {
        /** 状态映射策略名 */
        private String strategy = "suffix-heuristic";
    }
    
    /** 监控与可观测性相关配置。 */
    @Data
    public static class MonitoringProperties {
        /** 是否启用监控 */
        private boolean enabled = true;
        
        /** 熔断器配置 */
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
        
        /** 指标采集配置 */
        private MetricsProperties metrics = new MetricsProperties();
    }
    
    /** 熔断器相关配置。 */
    @Data
    public static class CircuitBreakerProperties {
        /** 是否对映射贡献者启用熔断保护 */
        private boolean enabled = true;
        
        /** 连续失败次数阈值（超过则打开熔断） */
        private int failureThreshold = 5;
        
        /** 失败率阈值（0.0~1.0，超过则打开熔断） */
        private double failureRateThreshold = 0.5;
        
        /** 从 OPEN 到 HALF_OPEN 之前的超时时间（毫秒） */
        private long timeoutMs = 60000; // 1 minute
        
        /** 滑动窗口大小（用于统计调用次数） */
        private int slidingWindowSize = 100;
    }
    
    /** 指标采集相关配置。 */
    @Data
    public static class MetricsProperties {
        /** 是否启用指标采集 */
        private boolean enabled = true;
        
        /** 是否记录解析耗时过慢日志 */
        private boolean logSlowResolution = true;
        
        /** 解析慢日志阈值（毫秒） */
        private long slowResolutionThresholdMs = 100;
        
        /** 是否记录缓存命中统计日志 */
        private boolean logCachePerformance = true;
        
        /** 缓存命中日志记录间隔（按请求次数） */
        private int cachePerformanceLogInterval = 50;
    }
}
