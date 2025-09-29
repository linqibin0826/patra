package com.patra.ingest.app.relay.executor;

import com.patra.ingest.domain.event.OutboxLeaseMissedEvent;
import com.patra.ingest.domain.event.OutboxMessageDeferredEvent;
import com.patra.ingest.domain.event.OutboxMessageFailedEvent;
import com.patra.ingest.domain.event.OutboxMessagePublishedEvent;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.value.RelayBatchResult;
import com.patra.ingest.domain.model.value.RelayPlan;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import com.patra.ingest.domain.policy.RelayErrorClassifier.RelayErrorKind;
import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.policy.RelayRetryPolicy;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.domain.port.OutboxRelayStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox Relay 执行器，负责编排一次批量发布流程。
 */
@Slf4j
@Component
public class OutboxRelayExecutor {

    private static final int ERROR_MSG_LIMIT = 512;

    private final OutboxRelayStore relayStore;
    private final OutboxPublisherPort publisherPort;
    private final RelayRetryPolicy retryPolicy;
    private final RelayErrorClassifier errorClassifier;

    public OutboxRelayExecutor(OutboxRelayStore relayStore,
                               OutboxPublisherPort publisherPort,
                               RelayRetryPolicy retryPolicy,
                               RelayErrorClassifier errorClassifier) {
        this.relayStore = relayStore;
        this.publisherPort = publisherPort;
        this.retryPolicy = retryPolicy;
        this.errorClassifier = errorClassifier;
    }

    public RelayBatchResult execute(RelayPlan plan) {
        List<OutboxMessage> messages = relayStore.fetchPending(plan.channel(), plan.triggeredAt(), plan.batchSize());
        if (messages.isEmpty()) {
            return RelayBatchResult.empty(plan.channel());
        }
        List<OutboxRelayDomainEvent> events = new ArrayList<>();
        int published = 0;
        int retried = 0;
        int failed = 0;
        int leaseMissed = 0;
        for (OutboxMessage message : messages) {
            long currentVersion = message.getVersion() == null ? 0L : message.getVersion();
            boolean leased = relayStore.acquireLease(
                    message.getId(),
                    message.getVersion(),
                    plan.leaseOwner(),
                    plan.leaseExpireAt()
            );
            if (!leased) {
                leaseMissed++;
                events.add(new OutboxLeaseMissedEvent(
                        message.getId(),
                        message.getChannel(),
                        plan.leaseOwner(),
                        message.getLeaseOwner(),
                        plan.triggeredAt()
                ));
                continue;
            }
            long publishingVersion = currentVersion + 1;
            try {
                OutboxPublisherPort.PublishResult publishResult = publisherPort.publish(message, plan);
                relayStore.markPublished(message.getId(), publishingVersion, publishResult.messageId());
                published++;
                events.add(new OutboxMessagePublishedEvent(
                        message.getId(),
                        message.getChannel(),
                        message.getPartitionKey(),
                        publishResult.messageId(),
                        plan.triggeredAt()
                ));
            } catch (Exception ex) {
                RelayErrorKind kind = errorClassifier.classify(ex);
                FailureHandling handling = determineFailureHandling(message, plan, kind);
                String errorCode = errorCode(ex);
                String errorMessage = errorMessage(ex);
                int nextRetry = nextRetryCount(message);
                if (handling == FailureHandling.FAIL) {
                    relayStore.markFailed(
                            message.getId(),
                            publishingVersion,
                            nextRetry,
                            errorCode,
                            errorMessage
                    );
                    failed++;
                    log.error("Relay publish failed permanently, messageId={} channel={} retryCount={} errorCode={}",
                            message.getId(), message.getChannel(), nextRetry, errorCode, ex);
                    events.add(new OutboxMessageFailedEvent(
                            message.getId(),
                            message.getChannel(),
                            nextRetry,
                            errorCode,
                            errorMessage,
                            plan.triggeredAt()
                    ));
                } else {
                    Duration delay = retryPolicy.computeDelay(nextRetry);
                    Instant nextRetryAt = plan.triggeredAt().plus(delay);
                    relayStore.markDeferred(
                            message.getId(),
                            publishingVersion,
                            nextRetry,
                            nextRetryAt,
                            errorCode,
                            errorMessage
                    );
                    retried++;
                    log.warn("Relay publish deferred, messageId={} channel={} retryCount={} nextRetryAt={} errorCode={}",
                            message.getId(), message.getChannel(), nextRetry, nextRetryAt, errorCode, ex);
                    events.add(new OutboxMessageDeferredEvent(
                            message.getId(),
                            message.getChannel(),
                            nextRetry,
                            nextRetryAt,
                            errorCode,
                            errorMessage,
                            plan.triggeredAt()
                    ));
                }
            }
        }
        return new RelayBatchResult(plan.channel(), messages.size(), published, retried, failed, leaseMissed, events);
    }

    private FailureHandling determineFailureHandling(OutboxMessage message, RelayPlan plan, RelayErrorKind kind) {
        if (kind == RelayErrorKind.FATAL) {
            return FailureHandling.FAIL;
        }
        int nextRetry = nextRetryCount(message);
        if (nextRetry >= plan.maxAttempts()) {
            return FailureHandling.FAIL;
        }
        return FailureHandling.RETRY;
    }

    private int nextRetryCount(OutboxMessage message) {
        int currentRetry = message.getRetryCount() == null ? 0 : message.getRetryCount();
        return currentRetry + 1;
    }

    private String errorCode(Exception exception) {
        return exception.getClass().getSimpleName();
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return null;
        }
        return StrUtil.maxLength(message, ERROR_MSG_LIMIT);
    }

    private enum FailureHandling {
        RETRY,
        FAIL
    }
}
