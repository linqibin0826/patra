package com.patra.ingest.app.usecase.relay;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher;
import com.patra.ingest.app.usecase.relay.executor.OutboxRelayExecutor;
import com.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import com.patra.ingest.domain.model.value.RelayBatchResult;
import com.patra.ingest.domain.model.value.RelayPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox Relay 应用服务：对外暴露单次批量发布编排入口。
 * <p>流程：
 * <ol>
 *   <li>特性开关校验（disabled 直接返回空报告）</li>
 *   <li>构建 {@link RelayPlan}</li>
 *   <li>委派执行器发布并收集 {@link RelayBatchResult}</li>
 *   <li>发布领域事件（用于审计/监控）</li>
 *   <li>组装并返回 {@link RelayReport}</li>
 * </ol>
 * 事务：方法标注 @Transactional（默认单库写入原子性），执行器内部按消息更新状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator implements OutboxRelayUseCase {

    private final OutboxRelayProperties properties;
    private final RelayPlanBuilder planBuilder;
    private final OutboxRelayExecutor relayExecutor;
    private final RelayEventPublisher eventPublisher;

    /**
     * 执行一次 Relay。关闭状态下返回空统计，避免调度报错。
     *
     * @param instruction 指令（可空字段）
     * @return 执行报告
     */
    @Override
    @Transactional
    public RelayReport relay(OutboxRelayCommand instruction) {
        if (!properties.isEnabled()) {
            var channelKey = instruction.channel() != null
                    ? instruction.channel()
                    : resolveDefaultChannelKey();
            log.info("[INGEST][APP] Outbox relay disabled, skip channel={}", channelKey.channel());
            return RelayReport.empty(channelKey);
        }
        // 记录执行起始时间，用于生成耗时指标
        long start = System.currentTimeMillis();

        RelayPlan plan = planBuilder.build(instruction);
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][APP] relay plan built channel={} batchSize={} leaseOwner={} leaseExpireAt={}",
                    plan.channel().channel(), plan.batchSize(), plan.leaseOwner(), plan.leaseExpireAt());
        }

        RelayBatchResult result = relayExecutor.execute(plan);
        eventPublisher.publish(result.events());

        long elapsed = System.currentTimeMillis() - start;
        log.info("[INGEST][APP] relay completed channel={} fetched={} published={} retried={} failed={} leaseMissed={} costMs={}",
                result.channel().channel(), result.fetched(), result.published(), result.retried(), result.failed(), result.leaseMissed(), elapsed);
        return new RelayReport(
                result.channel(),
                result.fetched(),
                result.published(),
                result.retried(),
                result.failed(),
                result.leaseMissed()
        );
    }

    private com.patra.ingest.domain.messaging.ChannelKey resolveDefaultChannelKey() {
        String cfg = properties.getDefaultChannel();
        if (CharSequenceUtil.isNotBlank(cfg)) {
            var byChannel = com.patra.ingest.domain.messaging.IngestChannels.fromChannel(cfg);
            if (byChannel.isPresent()) return byChannel.get();
            try {
                return com.patra.ingest.domain.messaging.IngestChannels.valueOf(cfg.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) { }
        }
        return com.patra.ingest.domain.messaging.IngestChannels.TASK_READY;
    }
}
