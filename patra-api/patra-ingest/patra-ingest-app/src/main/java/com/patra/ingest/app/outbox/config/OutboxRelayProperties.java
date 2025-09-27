package com.patra.ingest.app.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Outbox Relay 配置。
 */
@Component
@ConfigurationProperties(prefix = "patra.ingest.outbox-relay")
public class OutboxRelayProperties {

    /** 是否启用 Relay */
    private boolean enabled = true;
    /** 是否启用定时兜底 */
    private boolean scheduledFallbackEnabled = false;
    /** 单批处理数量 */
    private int batchSize = 200;
    /** 租约维持时间 */
    private Duration leaseDuration = Duration.ofSeconds(30);
    /** 最大重试次数（含首发） */
    private int maxRetry = 5;
    /** 重试退避初值 */
    private Duration retryBackoff = Duration.ofSeconds(5);
    /** 定时兜底固定延迟 */
    private Duration scheduledFixedDelay = Duration.ofSeconds(60);
    /** 定时兜底关注的频道列表 */
    private List<String> scheduledChannels = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isScheduledFallbackEnabled() {
        return scheduledFallbackEnabled;
    }

    public void setScheduledFallbackEnabled(boolean scheduledFallbackEnabled) {
        this.scheduledFallbackEnabled = scheduledFallbackEnabled;
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

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Duration getScheduledFixedDelay() {
        return scheduledFixedDelay;
    }

    public void setScheduledFixedDelay(Duration scheduledFixedDelay) {
        this.scheduledFixedDelay = scheduledFixedDelay;
    }

    public List<String> getScheduledChannels() {
        return scheduledChannels;
    }

    public void setScheduledChannels(List<String> scheduledChannels) {
        this.scheduledChannels = scheduledChannels;
    }
}
