package com.patra.ingest.adapter.inbound.scheduler.job;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.adapter.inbound.scheduler.param.OutboxRelayJobParam;
import com.patra.ingest.app.relay.OutboxRelayUseCase;
import com.patra.ingest.app.relay.command.OutboxRelayInstruction;
import com.patra.ingest.app.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.relay.dto.RelayReport;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
    private final Clock clock;

    @XxlJob("ingestOutboxRelayJob")
    public void execute() {
        Instant now = Instant.now(clock);
        try {
            OutboxRelayJobParam jobParam = parseParam(XxlJobHelper.getJobParam());
            OutboxRelayInstruction instruction = buildInstruction(jobParam, now);
            RelayReport report = relayUseCase.relay(instruction);
            XxlJobHelper.handleSuccess("Relay finished channel=%s fetched=%d published=%d retried=%d failed=%d leaseMissed=%d"
                    .formatted(report.channel(), report.fetched(), report.published(), report.retried(), report.failed(), report.leaseMissed()));
            log.info("Outbox relay done, channel={} fetched={} published={} retried={} failed={} leaseMissed={}"
                    , report.channel(), report.fetched(), report.published(), report.retried(), report.failed(), report.leaseMissed());
        } catch (OutboxRelayExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Outbox relay execution failed", ex);
            XxlJobHelper.handleFail("Relay failed: " + ex.getMessage());
            throw new OutboxRelayExecutionException("Outbox relay execution failed", ex);
        }
    }

    private OutboxRelayInstruction buildInstruction(OutboxRelayJobParam param, Instant now) {
        return new OutboxRelayInstruction(
                resolveChannel(param.channel()),
                now,
                param.batchSize(),
                parseDuration(param.leaseDuration()),
                param.maxAttempts(),
                parseDuration(param.initialBackoff()),
                buildLeaseOwner()
        );
    }

    private String resolveChannel(String channel) {
        return CharSequenceUtil.blankToDefault(channel, relayProperties.getDefaultChannel());
    }

    private Duration parseDuration(String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return null;
        }
        String trimmed = CharSequenceUtil.trim(value);
        try {
            if (trimmed.startsWith("PT")) {
                return Duration.parse(trimmed);
            }
            return Duration.ofSeconds(Long.parseLong(trimmed));
        } catch (Exception ex) {
            throw new IngestScheduleParameterException("Illegal duration value: " + value, ex);
        }
    }

    private OutboxRelayJobParam parseParam(String param) {
        if (CharSequenceUtil.isBlank(param)) {
            return new OutboxRelayJobParam(null, null, null, null, null);
        }
        try {
            return objectMapper.readValue(param, OutboxRelayJobParam.class);
        } catch (Exception ex) {
            throw new IngestScheduleParameterException("Failed to parse relay param: " + ex.getMessage(), ex);
        }
    }

    private String buildLeaseOwner() {
        String host = CharSequenceUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
        return host + '-' + XxlJobHelper.getJobId() + '-' + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
    }
}
