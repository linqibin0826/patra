package com.patra.starter.feign.runtime;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Patra Feign Starter 配置属性
 *
 * <p>控制 Starter 开关、错误响应体读取限制、请求头传播以及由 {@link
 * com.patra.starter.feign.runtime.PatraFeignRequestInterceptor} 应用的密钥脱敏规则。
 */
@Data
@ConfigurationProperties(prefix = "patra.feign")
public class PatraFeignProperties {

  /** Starter 主开关(默认:启用) */
  private boolean enabled = true;

  /** 从错误响应体读取的最大字节数(默认:64 KiB) */
  private int maxErrorBodySize = 64 * 1024;

  /** 用于转发调用者服务标识的请求头名称 */
  private String serviceHeader = "X-Service-Name";

  /** 应在日志和跟踪中脱敏的请求头键列表(不区分大小写) */
  private List<String> redactKeys = List.of("token", "password", "secret", "apiKey");
}
