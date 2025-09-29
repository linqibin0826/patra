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
 * Outbox Relay 调度作业：负责周期性扫描 Outbox 表，获取可投递消息并尝试发布。
 * <p>工作流：参数解析 → 构造指令（含租约/重试配置）→ 调用应用用例 → 上报结果。</p>
 * <p>幂等性：租约拥有者标识包含 host + jobId + threadId + uuid，保障并发实例区分。</p>
 * <p>失败模式：业务失败封装为 {@link OutboxRelayExecutionException} 抛出，XXL 标记失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final ObjectMapper objectMapper;
    private final OutboxRelayUseCase relayUseCase;
    private final OutboxRelayProperties relayProperties;
    private final Clock clock;

    /**
     * XXL-Job 入口。解析参数执行 relay，并将统计结果写入调度日志。
     */
    @XxlJob("ingestOutboxRelayJob")
    public void execute() {
        Instant now = Instant.now(clock);
        try {
            OutboxRelayJobParam jobParam = parseParam(XxlJobHelper.getJobParam());
            OutboxRelayInstruction instruction = buildInstruction(jobParam, now);

            RelayReport report = relayUseCase.relay(instruction);

            XxlJobHelper.handleSuccess("Relay finished channel=%s fetched=%d published=%d retried=%d failed=%d leaseMissed=%d"
                    .formatted(report.channel(), report.fetched(), report.published(), report.retried(), report.failed(), report.leaseMissed()));

            log.info("Outbox relay done, channel={} fetched={} published={} retried={} failed={} leaseMissed={}",
                    report.channel(), report.fetched(), report.published(), report.retried(), report.failed(), report.leaseMissed());
        } catch (OutboxRelayExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Outbox relay execution failed", ex);
            XxlJobHelper.handleFail("Relay failed: " + ex.getMessage());
            throw new OutboxRelayExecutionException("Outbox relay execution failed", ex);
        }
    }

    /**
     * 构建 relay 指令：封装目标通道、时间基准、批大小、租约配置与重试策略。
     *
     * @param param 任务参数（可能部分字段为空）
     * @param now   当前时间（注入 Clock 便于测试）
     * @return OutboxRelayInstruction
     */
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

    /**
     * 解析通道：为空时回退配置默认值。
     */
    private String resolveChannel(String channel) {
        return CharSequenceUtil.blankToDefault(channel, relayProperties.getDefaultChannel());
    }

    /**
     * 解析持续时间：支持 ISO-8601（以 PT 开头）或纯秒数字串。
     *
     * @param value 持续时间字符串
     * @return Duration 或 null
     * @throws IngestScheduleParameterException 非法格式
     */
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

    /**
     * 解析 JSON 参数，失败抛出调度参数异常。
     */
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

    /**
     * 构造租约 owner 标识：host + jobId + threadId + uuid，避免冲突并便于追踪。
     */
    private String buildLeaseOwner() {
        String host = CharSequenceUtil.blankToDefault(NetUtil.getLocalHostName(), "unknown");
        return host + '-' + XxlJobHelper.getJobId() + '-' + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
    }
}
