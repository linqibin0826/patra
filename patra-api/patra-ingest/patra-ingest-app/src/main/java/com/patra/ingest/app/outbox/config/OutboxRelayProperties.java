package com.patra.ingest.app.outbox.config;

import com.patra.ingest.app.outbox.support.OutboxChannels;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Outbox Relay 默认配置。
 */
@Component
@ConfigurationProperties(prefix = "patra.ingest.outbox-relay")
public class OutboxRelayProperties {

    /** 是否启用 Relay */
    private boolean enabled = true;
    /** 默认频道 */
    private String defaultChannel = OutboxChannels.INGEST_TASK_READY;
    /** 默认批次大小 */
    private int batchSize = 200;
    /** 默认租约持续时长 */
    private Duration leaseDuration = Duration.ofSeconds(30);
    /** 最大尝试次数（包含第一次） */
    private int maxAttempts = 5;
    /** 首次退避时长 */
    private Duration initialBackoff = Duration.ofSeconds(5);
    /** 退避倍数 */
    private double backoffMultiplier = 2.0d;
    /** 退避上限 */
    private Duration maxBackoff = Duration.ofMinutes(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultChannel() {
        return defaultChannel;
    }

    public void setDefaultChannel(String defaultChannel) {
        this.defaultChannel = defaultChannel;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }
}
