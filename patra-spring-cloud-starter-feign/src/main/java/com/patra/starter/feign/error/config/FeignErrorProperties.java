package com.patra.starter.feign.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feign 错误处理配置项。
 *
 * <p>用于控制错误解码行为、宽容策略以及观测指标。</p>
 */
@Data
@ConfigurationProperties(prefix = "patra.feign.problem")
public class FeignErrorProperties {

    /** 是否启用 Feign 错误处理 */
    private boolean enabled = true;

    /** 是否启用宽容（tolerant）模式 */
    private boolean tolerant = true;

    /** 读取错误响应体的最大字节数 */
    private int maxErrorBodySize = 64 * 1024;

    /** 是否在错误响应中包含堆栈信息（调试用途） */
    private boolean includeStackTrace = false;

    /** 观测与指标配置 */
    private ObservationProperties observation = new ObservationProperties();

    /** 观测配置 */
    @Data
    public static class ObservationProperties {
        /** 是否启用观测 */
        private boolean enabled = true;
        /** 解析 ProblemDetail 的慢阈值（毫秒） */
        private long slowParsingThresholdMs = 150;
        /** 是否输出解析慢日志 */
        private boolean logSlowParsing = true;
        /** 读取响应体的慢阈值（毫秒） */
        private long slowBodyReadingThresholdMs = 80;
        /** 是否输出响应体读取慢日志 */
        private boolean logSlowBodyReading = true;
        /** 是否在使用宽容模式时记录 INFO 日志 */
        private boolean logTolerantUsage = true;
    }
}
