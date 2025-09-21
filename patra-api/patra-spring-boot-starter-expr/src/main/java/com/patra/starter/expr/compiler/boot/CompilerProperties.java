package com.patra.starter.expr.compiler.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patra.expr.compiler")
public record CompilerProperties(
        boolean enabled,
        RegistryApi registryApi
) {
    public CompilerProperties {
        if (registryApi == null) registryApi = new RegistryApi(false, "search");
    }
    public record RegistryApi(boolean enabled, String operationDefault) {}
}
