package com.patra.starter.feign.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feign 错误处理配置项。
 *
 * <p>用于控制错误解码行为、宽容模式以及 {@link org.springframework.http.ProblemDetail}
 * 响应的处理策略。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder
 * @see com.patra.starter.feign.error.config.FeignErrorAutoConfiguration
 */
@Data
@ConfigurationProperties(prefix = "patra.feign.problem")
public class FeignErrorProperties {
    
    /** 是否启用 Feign 错误处理 */
    private boolean enabled = true;
    
    /**
     * 是否启用宽容（tolerant）模式。
     *
     * <p>开启后，将优雅处理以下场景：
     * - 404 且无响应体
     * - 非 JSON 响应
     * - 非法/畸形的 ProblemDetail
     * 关闭则进入严格模式：非 ProblemDetail 直接回退为 {@link feign.FeignException}。
     */
    private boolean tolerant = true;
    
    /** 读取错误响应体的最大字节数 */
    private int maxErrorBodySize = 64 * 1024; // 64KB
    
    /** 是否在错误响应中包含堆栈信息（用于调试） */
    private boolean includeStackTrace = false;
    
    /** 监控与可观测性相关配置 */
    private MonitoringProperties monitoring = new MonitoringProperties();
    
    /**
     * Monitoring configuration properties for Feign error handling.
     */
    @Data
    public static class MonitoringProperties {
        /** 是否启用监控 */
        private boolean enabled = true;
        
        /** 是否记录解析耗时过慢的日志 */
        private boolean logSlowParsing = true;
        
        /** 解析慢日志阈值（毫秒） */
        private long slowParsingThresholdMs = 100;
        
        /** 是否记录响应体读取性能日志 */
        private boolean logResponseBodyReading = true;
        
        /** 响应体读取慢日志阈值（毫秒） */
        private long slowBodyReadingThresholdMs = 50;
        
        /** 解码成功率日志的记录间隔（按尝试次数） */
        private int decodingSuccessLogInterval = 10;
        
        /** TraceId 提取成功率日志的记录间隔（按尝试次数） */
        private int traceIdExtractionLogInterval = 25;
        
        /** Content-Type 分布日志记录间隔（按响应条数） */
        private int contentTypeDistributionLogInterval = 50;
    }
}
