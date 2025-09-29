package com.patra.ingest.app.relay.config;

import com.patra.ingest.app.relay.support.OutboxChannels;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Outbox Relay 默认配置项（可被外部配置文件覆盖）。
 * <p>前缀：patra.ingest.outbox-relay.*</p>
 * <ul>
 *   <li>enabled：是否开启 Relay 功能；关闭时所有调度直接返回空报告。</li>
 *   <li>defaultChannel：默认通道；指令未显式传入 channel 时使用。</li>
 *   <li>batchSize：单次抓取最大消息数；<=0 将在使用侧回退。</li>
 *   <li>leaseDuration：单条消息租约持续时长；需覆盖处理 + 发布 + 状态写入。</li>
 *   <li>maxAttempts：允许的最大发布尝试次数（含首次）。</li>
 *   <li>initialBackoff：首次重试退避时长。</li>
 *   <li>backoffMultiplier：指数退避乘数；>1。</li>
 *   <li>maxBackoff：退避上限，防止无限指数增长。</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "patra.ingest.outbox-relay")
public class OutboxRelayProperties {

    /** 是否启用 Relay */
    private boolean enabled = true;
    /** 默认频道（字符串，支持 "ingest.task.ready" 或别名 "TASK_READY"），为空时使用内置默认 */
    private String defaultChannel;
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

    public String getDefaultChannel() { return defaultChannel; }
    public void setDefaultChannel(String defaultChannel) { this.defaultChannel = defaultChannel; }

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
