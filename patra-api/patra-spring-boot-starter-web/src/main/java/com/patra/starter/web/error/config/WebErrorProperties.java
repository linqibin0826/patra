package com.patra.starter.web.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 端错误处理配置项。
 *
 * <p>用于控制 Web 层错误输出（ProblemDetail 等）的格式与行为。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.web.problem")
public class WebErrorProperties {
    
    /** 是否启用 Web 错误处理 */
    private boolean enabled = true;
    
    /** 构造 ProblemDetail#type 字段的基础 URL */
    private String typeBaseUrl = "https://errors.example.com/";
    
    /** 错误响应是否包含堆栈（调试用途） */
    private boolean includeStack = false;
}
