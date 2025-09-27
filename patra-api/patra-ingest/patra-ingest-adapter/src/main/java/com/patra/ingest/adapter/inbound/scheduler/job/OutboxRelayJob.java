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
 * Outbox Relay XXL-Job 任务处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final ObjectMapper objectMapper;
    private final OutboxRelayUseCase relayUseCase;
    private final OutboxRelayProperties relayProperties;

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
            log.info("Outbox Relay 完成 channel={} fetched={} success={} retried={} dead={} skipped={}",
                    channel,
                    result.fetched(),
                    result.succeeded(),
                    result.retried(),
                    result.dead(),
                    result.skipped());
            XxlJobHelper.handleSuccess("Relay 完成 channel=%s fetched=%d success=%d retry=%d dead=%d skipped=%d".formatted(
                    channel, result.fetched(), result.succeeded(), result.retried(), result.dead(), result.skipped()));
        } catch (Exception ex) {
            log.error("Outbox Relay 任务执行失败", ex);
            XxlJobHelper.handleFail("执行失败: " + ex.getMessage());
            throw new IllegalStateException("Outbox Relay 执行失败", ex);
        }
    }

    private OutboxRelayJobParam parseParam(String param) {
        if (!StringUtils.hasText(param)) {
            return new OutboxRelayJobParam(null, null, null, null, null);
        }
        try {
            return objectMapper.readValue(param, OutboxRelayJobParam.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析参数失败: " + e.getMessage(), e);
        }
    }

    private int resolvePositive(Integer candidate, int fallback) {
        if (candidate == null || candidate <= 0) {
            return fallback;
        }
        return candidate;
    }

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
            throw new IllegalArgumentException("非法的持续时间: " + value, e);
        }
    }

    private String buildLeaseOwner() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + "-" + XxlJobHelper.getJobId() + "-" + Thread.currentThread().threadId();
        } catch (Exception e) {
            return "unknown-" + XxlJobHelper.getJobId();
        }
    }
}
