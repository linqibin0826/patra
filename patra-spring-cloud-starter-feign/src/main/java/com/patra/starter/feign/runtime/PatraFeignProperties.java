package com.patra.starter.feign.runtime;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Papertrace Feign starter.
 *
 * <p>Controls the starter switch, error-body reading limits, header propagation, and key redaction
 * rules applied by the {@link com.patra.starter.feign.runtime.PatraFeignRequestInterceptor}.
 */
@Data
@ConfigurationProperties(prefix = "patra.feign")
public class PatraFeignProperties {

  /** Master toggle for the starter (default: enabled). */
  private boolean enabled = true;

  /** Maximum number of bytes to read from an error response body (default: 64 KiB). */
  private int maxErrorBodySize = 64 * 1024;

  /** Header name used to forward the caller service identity. */
  private String serviceHeader = "X-Service-Name";

  /** Case-insensitive list of request header keys that should be redacted in logs and traces. */
  private List<String> redactKeys = List.of("token", "password", "secret", "apiKey");
}
