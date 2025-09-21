package com.patra.starter.expr.compiler.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "patra.expr.compiler")
public class CompilerProperties {

    private boolean enabled = true;
    private final RegistryApi registryApi = new RegistryApi();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RegistryApi getRegistryApi() {
        return registryApi;
    }

    public static class RegistryApi {
        private boolean enabled = true;
        private String operationDefault = "SEARCH";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getOperationDefault() {
            return operationDefault;
        }

        public void setOperationDefault(String operationDefault) {
            this.operationDefault = operationDefault;
        }
    }
}
