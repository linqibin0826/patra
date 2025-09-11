package com.patra.starter.feign.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Feign Starter 配置项。
 *
 * <p>
 * - 控制是否强制设置 Accept: application/problem+json
 * - 最大错误体读取大小
 * - 透传与脱敏配置
 * - 可选 Web Advice 开关
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.feign")
public class PatraFeignProperties {

    /** 模块开关 */
    private boolean enabled = true;


    /** 读取错误响应体的最大字节数（防止过大体积），默认 64KB */
    private int maxErrorBodySize = 64 * 1024;


    /** Service 请求头名（调用方服务名） */
    private String serviceHeader = "X-Service-Name";

    /** 需要脱敏的 keys（大小写不敏感，包含匹配） */
    private List<String> redactKeys = List.of("token", "password", "secret", "apiKey");

}

