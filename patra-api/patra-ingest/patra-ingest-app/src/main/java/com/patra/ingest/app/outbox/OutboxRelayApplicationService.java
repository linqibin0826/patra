package com.patra.ingest.app.outbox;

import com.patra.ingest.app.outbox.command.OutboxRelayInstruction;
import com.patra.ingest.app.outbox.config.OutboxRelayProperties;
import com.patra.ingest.app.outbox.dto.RelayReport;
import com.patra.ingest.app.outbox.event.OutboxRelayEventPublisher;
import com.patra.ingest.app.outbox.executor.OutboxRelayExecutor;
import com.patra.ingest.app.outbox.plan.RelayPlanFactory;
import com.patra.ingest.domain.model.value.RelayBatchResult;
import com.patra.ingest.domain.model.value.RelayPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox Relay 应用服务，负责编排领域执行与事件发布。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayApplicationService implements OutboxRelayUseCase {

    private final OutboxRelayProperties properties;
    private final RelayPlanFactory relayPlanFactory;
    private final OutboxRelayExecutor relayExecutor;
    private final OutboxRelayEventPublisher eventPublisher;

    @Override
    @Transactional
    public RelayReport relay(OutboxRelayInstruction instruction) {
        if (!properties.isEnabled()) {
            String channel = instruction.channel() != null ? instruction.channel() : properties.getDefaultChannel();
            log.info("Outbox relay disabled, skip channel={}", channel);
            return RelayReport.empty(channel);
        }
        RelayPlan plan = relayPlanFactory.create(instruction);
        RelayBatchResult result = relayExecutor.execute(plan);
        eventPublisher.publish(result.events());
        return new RelayReport(
                result.channel(),
                result.fetched(),
                result.published(),
                result.retried(),
                result.failed(),
                result.leaseMissed()
        );
    }
}
