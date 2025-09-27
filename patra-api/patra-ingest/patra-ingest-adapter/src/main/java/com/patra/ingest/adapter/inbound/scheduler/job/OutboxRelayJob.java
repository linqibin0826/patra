package com.patra.ingest.adapter.inbound.scheduler.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.adapter.inbound.scheduler.param.OutboxRelayJobParam;
import com.patra.ingest.app.outbox.OutboxRelayUseCase;
import com.patra.ingest.app.outbox.command.OutboxRelayCommand;
import com.patra.ingest.app.outbox.config.OutboxRelayProperties;
import com.patra.ingest.app.outbox.dto.OutboxRelayResult;
import com.patra.ingest.app.outbox.support.OutboxChannels;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;

/**
 * Outbox Relay XXL-Job 任务处理器，负责拉取 Outbox 并驱动消息发布。
 * <p>结合 XXL 参数与默认配置生成指令，调用应用层 OutboxRelayUseCase。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;
    /** Outbox Relay 应用用例 */
    private final OutboxRelayUseCase relayUseCase;
    /** Relay 运行配置 */
    private final OutboxRelayProperties relayProperties;

    /**
     * XXL-Job 执行入口，解析调度参数并发起 Relay。
     */
    @XxlJob("ingestOutboxRelayJob")
    public void execute() {
        Instant now = Instant.now();
        try {
            OutboxRelayJobParam jobParam = parseParam(XxlJobHelper.getJobParam());
            String channel = StringUtils.hasText(jobParam.channel())
                    ? jobParam.channel()
                    : OutboxChannels.INGEST_TASK_READY;
            int batchSize = resolvePositive(jobParam.batchSize(), relayProperties.getBatchSize());
            Duration leaseDuration = resolveDuration(jobParam.leaseDuration(), relayProperties.getLeaseDuration());
            int maxRetry = resolvePositive(jobParam.maxRetry(), relayProperties.getMaxRetry());
            Duration retryBackoff = resolveDuration(jobParam.retryBackoff(), relayProperties.getRetryBackoff());
            String leaseOwner = buildLeaseOwner();

            OutboxRelayCommand command = new OutboxRelayCommand(
                    channel,
                    now,
                    batchSize,
                    leaseDuration,
                    maxRetry,
                    retryBackoff,
                    leaseOwner
            );
            OutboxRelayResult result = relayUseCase.relay(command);
            log.info("Outbox relay completed, channel={}, fetched={}, success={}, retry={}, dead={}, skipped={}",
                    channel,
                    result.fetched(),
                    result.succeeded(),
                    result.retried(),
                    result.dead(),
                    result.skipped());
            XxlJobHelper.handleSuccess("Relay finished channel=%s fetched=%d success=%d retry=%d dead=%d skipped=%d".formatted(
                    channel, result.fetched(), result.succeeded(), result.retried(), result.dead(), result.skipped()));
        } catch (Exception ex) {
            log.error("Outbox relay execution failed", ex);
            XxlJobHelper.handleFail("Relay failed: " + ex.getMessage());
            throw new IllegalStateException("Outbox relay execution failed", ex);
        }
    }

    /**
     * 解析调度参数 JSON。
     */
    private OutboxRelayJobParam parseParam(String param) {
        if (!StringUtils.hasText(param)) {
            return new OutboxRelayJobParam(null, null, null, null, null);
        }
        try {
            return objectMapper.readValue(param, OutboxRelayJobParam.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse relay param: " + e.getMessage(), e);
        }
    }

    /**
     * 解析正整数配置，若为空或非法则使用兜底值。
     */
    private int resolvePositive(Integer candidate, int fallback) {
        if (candidate == null || candidate <= 0) {
            return fallback;
        }
        return candidate;
    }

    /**
     * 解析持续时间，可接受 ISO8601 或秒数格式。
     */
    private Duration resolveDuration(String value, Duration fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            if (value.startsWith("PT")) {
                return Duration.parse(value);
            }
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("Illegal duration value: " + value, e);
        }
    }

    /**
     * 构建租约持有者标识，便于多实例并发调度。
     */
    private String buildLeaseOwner() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + "-" + XxlJobHelper.getJobId() + "-" + Thread.currentThread().threadId();
        } catch (Exception e) {
            return "unknown-" + XxlJobHelper.getJobId();
        }
    }
}
