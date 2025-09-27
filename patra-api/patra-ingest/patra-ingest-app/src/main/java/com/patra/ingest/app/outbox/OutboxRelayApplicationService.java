package com.patra.ingest.app.outbox;

import com.patra.ingest.app.outbox.command.OutboxRelayCommand;
import com.patra.ingest.app.outbox.config.OutboxRelayProperties;
import com.patra.ingest.app.outbox.dto.OutboxRelayResult;
import com.patra.ingest.app.outbox.model.TaskReadyMessage;
import com.patra.ingest.app.outbox.support.OutboxDestinationResolver;
import com.patra.ingest.app.outbox.support.TaskReadyMessageMapper;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.enums.OutboxStatus;
import com.patra.ingest.domain.port.OutboxRelayRepository;
import com.patra.starter.rocketmq.model.PatraMessage;
import com.patra.starter.rocketmq.publisher.PatraMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay 应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayApplicationService implements OutboxRelayUseCase {

    private final OutboxRelayRepository relayRepository;
    private final PatraMessagePublisher messagePublisher;
    private final OutboxRelayProperties relayProperties;
    private final OutboxDestinationResolver destinationResolver;
    private final TaskReadyMessageMapper messageMapper;

    @Override
    @Transactional
    public OutboxRelayResult relay(OutboxRelayCommand command) {
        if (!relayProperties.isEnabled()) {
            log.debug("Outbox Relay 已禁用，跳过 channel={}", command.channel());
            return new OutboxRelayResult(0, 0, 0, 0, 0);
        }
        int batchSize = normalizeBatchSize(command.batchSize());
        Duration leaseDuration = normalizeDuration(command.leaseDuration(), relayProperties.getLeaseDuration());
        int maxRetry = normalizePositive(command.maxRetry(), relayProperties.getMaxRetry());
        Duration retryBackoff = normalizeDuration(command.retryBackoff(), relayProperties.getRetryBackoff());
        List<OutboxMessage> messages = relayRepository.lockPending(
                command.channel(),
                command.executeAt(),
                batchSize
        );
        if (messages.isEmpty()) {
            return new OutboxRelayResult(0, 0, 0, 0, 0);
        }
        String destination = destinationResolver.resolve(command.channel());
        int succeeded = 0;
        int retried = 0;
        int dead = 0;
        int skipped = 0;
        for (OutboxMessage message : messages) {
            long currentVersion = message.getVersion() == null ? 0L : message.getVersion();
            Instant leaseExpireAt = command.executeAt().plus(leaseDuration);
            boolean leasing = relayRepository.markPublishing(
                    message.getId(),
                    OutboxStatus.PENDING.name(),
                    currentVersion,
                    command.leaseOwner(),
                    leaseExpireAt
            );
            if (!leasing) {
                skipped++;
                continue;
            }
            long publishingVersion = currentVersion + 1;
            try {
                TaskReadyMessage body = messageMapper.map(message);
                PatraMessage<TaskReadyMessage> patraMessage = buildPatraMessage(message, body, command.executeAt());
                messagePublisher.send(destination, patraMessage);
                relayRepository.markPublished(message.getId(), publishingVersion, null);
                succeeded++;
            } catch (Exception ex) {
                FailureHandling handling = handleFailure(message, publishingVersion, command.executeAt(), maxRetry, retryBackoff, ex);
                if (handling == FailureHandling.DEAD) {
                    dead++;
                } else if (handling == FailureHandling.RETRY) {
                    retried++;
                } else {
                    skipped++;
                }
            }
        }
        return new OutboxRelayResult(messages.size(), succeeded, retried, dead, skipped);
    }

    private PatraMessage<TaskReadyMessage> buildPatraMessage(OutboxMessage message,
                                                             TaskReadyMessage body,
                                                             Instant occurredAtFallback) {
        String traceId = body.header() != null && body.header().scheduleInstanceId() != null
                ? String.valueOf(body.header().scheduleInstanceId())
                : message.getPartitionKey();
        Instant occurredAt = body.header() != null && body.header().occurredAt() != null
                ? body.header().occurredAt()
                : occurredAtFallback;
        return PatraMessage.<TaskReadyMessage>builder()
                .eventId(message.getDedupKey())
                .traceId(traceId)
                .occurredAt(occurredAt)
                .payload(body)
                .build();
    }

    private FailureHandling handleFailure(OutboxMessage message,
                                          long publishingVersion,
                                          Instant executeAt,
                                          int maxRetry,
                                          Duration retryBackoff,
                                          Exception ex) {
        int currentRetry = message.getRetryCount() == null ? 0 : message.getRetryCount();
        int nextRetry = currentRetry + 1;
        String errorCode = ex.getClass().getSimpleName();
        String errorMsg = truncate(ex.getMessage(), 512);
        if (bodyParseFailure(ex)) {
            relayRepository.markDead(message.getId(), publishingVersion, nextRetry, errorCode, errorMsg);
            log.error("Outbox 消息解析失败，标记为 DEAD，id={} channel={}", message.getId(), message.getChannel(), ex);
            return FailureHandling.DEAD;
        }
        if (nextRetry >= maxRetry) {
            relayRepository.markDead(message.getId(), publishingVersion, nextRetry, errorCode, errorMsg);
            log.error("Outbox 消息重试达到上限，标记为 DEAD，id={} channel={} retry={}", message.getId(), message.getChannel(), nextRetry, ex);
            return FailureHandling.DEAD;
        }
        Instant nextRetryAt = executeAt.plus(resolveBackoff(retryBackoff, nextRetry));
        relayRepository.markRetry(message.getId(), publishingVersion, nextRetry, nextRetryAt, errorCode, errorMsg);
        log.warn("Outbox 消息发布失败，将重试，id={} channel={} retry={} nextRetryAt={}", message.getId(), message.getChannel(), nextRetry, nextRetryAt, ex);
        return FailureHandling.RETRY;
    }

    private boolean bodyParseFailure(Exception ex) {
        return ex instanceof IllegalStateException;
    }

    private int normalizeBatchSize(int candidate) {
        int fallback = relayProperties.getBatchSize();
        if (candidate <= 0) {
            return fallback;
        }
        return Math.min(candidate, fallback);
    }

    private int normalizePositive(int candidate, int fallback) {
        if (candidate <= 0) {
            return fallback;
        }
        return candidate;
    }

    private Duration normalizeDuration(Duration candidate, Duration fallback) {
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return fallback;
        }
        return candidate;
    }

    private Duration resolveBackoff(Duration base, int attempt) {
        if (attempt <= 1) {
            return base;
        }
        int exponent = Math.min(attempt - 1, 10);
        long multiplier = 1L << exponent;
        return base.multipliedBy(multiplier);
    }

    private String truncate(String msg, int max) {
        if (msg == null) {
            return null;
        }
        if (msg.length() <= max) {
            return msg;
        }
        return msg.substring(0, max);
    }

    private enum FailureHandling {
        RETRY,
        DEAD,
        SKIPPED
    }
}
