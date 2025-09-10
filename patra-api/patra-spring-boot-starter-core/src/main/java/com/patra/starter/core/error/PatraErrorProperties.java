package com.patra.starter.core.error;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "patra.error")
public class PatraErrorProperties {
    private boolean enabled = true;
    private boolean failFast = true;
    private boolean logSummary = true;
    private List<String> redactedKeys = List.of("token", "password", "secret");
}
