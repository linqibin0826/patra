package com.patra.starter.rocketmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Channel 统一配置。
 */
@ConfigurationProperties(prefix = "patra.messaging.channels")
public class PatraChannelProperties {

    /** 是否强制校验 channel 必须在注册表内（默认 true） */
    private boolean enforce = true;

    /** 可选：要求 channel 第一段必须等于该 domain（如 ingest/registry），为空则不校验 */
    private String domain;

    public boolean isEnforce() {
        return enforce;
    }

    public void setEnforce(boolean enforce) {
        this.enforce = enforce;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}

