package com.patra.ingest.app.usecase.relay.executor;

import com.patra.ingest.domain.event.OutboxLeaseMissedEvent;
import com.patra.ingest.domain.event.OutboxMessageDeferredEvent;
import com.patra.ingest.domain.event.OutboxMessageFailedEvent;
import com.patra.ingest.domain.event.OutboxMessagePublishedEvent;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayBatchResult;
import com.patra.ingest.domain.model.vo.RelayPlan;
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
 * Outbox Relay 执行器：负责在单次触发周期内完成 Outbox 消息的拉取、租约校验、发布与状态回写。
 * <p>幂等保障：依赖租约获取 + 版本号自增；失败回写持久化错误信息防止重复处理；延期重试通过 nextRetryAt 控制。</p>
 * <p>异常分类：使用 {@link RelayErrorClassifier} 区分 FATAL / TRANSIENT，指导 markFailed 与 markDeferred 的分支选择。</p>
 * <p>日志策略：DEBUG 精准诊断（可选开启）；WARN 记录可重试失败；ERROR 记录永久失败。</p>
 *
 * @author linqibin
 * @since 0.1.0
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

    /**
     * 执行单批次发布。
     * <ol>
     *   <li>fetchPending 按计划条件拉取候选消息（支持单 channel 或全部 channel）</li>
     *   <li>逐条 acquireLease（乐观并发控制）</li>
     *   <li>publish → markPublished / classify exception → markFailed / markDeferred</li>
     *   <li>累计事件与统计信息</li>
     * </ol>
     *
     * @param plan 发布计划
     * @return 批次结果统计
     */
    public RelayBatchResult execute(RelayPlan plan) {
        // 按计划批量拉取待发送消息，受租约与批大小约束
        // 如果 plan.channel() 为 null，则获取所有 channel 的消息
        List<OutboxMessage> messages = fetchMessages(plan);
        if (messages.isEmpty()) {
            if (log.isDebugEnabled()) {
                String channelDesc = plan.channel() != null ? plan.channel().channel() : "ALL_CHANNELS";
                log.debug("[INGEST][APP] relay executor no-pending channel={} triggeredAt={}", channelDesc, plan.triggeredAt());
            }
            return RelayBatchResult.empty(plan.channel());
        }
        RelayContext context = new RelayContext(plan);
        for (OutboxMessage message : messages) {
            processMessage(message, context);
        }
        return context.toBatchResult(messages.size());
    }

    /**
     * 根据计划拉取消息：如果指定了 channel 则仅拉取该 channel，否则拉取所有 channel。
     *
     * @param plan 发布计划
     * @return 待处理消息列表
     */
    private List<OutboxMessage> fetchMessages(RelayPlan plan) {
        if (plan.channel() != null) {
            // 指定了 channel，仅拉取该 channel 的消息
            return relayStore.fetchPending(plan.channel().channel(), plan.triggeredAt(), plan.batchSize());
        } else {
            // 未指定 channel，拉取所有 channel 的消息
            return relayStore.fetchPendingAllChannels(plan.triggeredAt(), plan.batchSize());
        }
    }

    /**
     * 处理单条消息的完整转发表达：先尝试获取租约，成功后进行发布并依据结果写回状态。
     *
     * @param message 待处理的 Outbox 消息
     * @param context 批次上下文，负责累计统计与事件
     */
    private void processMessage(OutboxMessage message, RelayContext context) {
        if (!tryAcquireLease(message, context)) {
            return;
        }
        long publishingVersion = nextVersionOf(message);
        try {
            OutboxPublisherPort.PublishResult publishResult = publisherPort.publish(message, context.plan());
            relayStore.markPublished(message.getId(), publishingVersion, publishResult.messageId());
            context.onPublished(message, publishResult);
        } catch (Exception ex) {
            // 将异常统一交由失败决策模块评估（重试 / 失败）
            handleFailure(message, context, publishingVersion, ex);
        }
    }

    /**
     * 尝试为消息抢占租约；若失败则直接记录租约失效事件并跳过后续处理。
     *
     * @param message 待抢占租约的消息
     * @param context 批次上下文
     * @return true 表示获取成功
     */
    private boolean tryAcquireLease(OutboxMessage message, RelayContext context) {
        RelayPlan plan = context.plan();
        boolean leased = relayStore.acquireLease(
                message.getId(),
                message.getVersion(),
                plan.leaseOwner(),
                plan.leaseExpireAt()
        );
        if (!leased) {
            context.onLeaseMissed(message);
        }
        return leased;
    }

    /**
     * 根据异常决定失败处理路径：永久失败直接落盘，暂时性失败按策略推算重试时间。
     *
     * @param message           当前处理的消息
     * @param context           批次上下文
     * @param publishingVersion 发布时预期写入的版本号
     * @param exception         发布阶段抛出的异常
     */
    private void handleFailure(OutboxMessage message,
                               RelayContext context,
                               long publishingVersion,
                               Exception exception) {
        RelayPlan plan = context.plan();
        FailureDecision decision = decideFailure(message, plan, exception);
        if (decision.handling() == FailureHandling.FAIL) {
            relayStore.markFailed(
                    message.getId(),
                    publishingVersion,
                    decision.nextRetry(),
                    decision.errorCode(),
                    decision.errorMessage()
            );
            context.onFailed(message, decision.nextRetry(), decision.errorCode(), decision.errorMessage(), exception);
            return;
        }
        Duration delay = retryPolicy.computeDelay(decision.nextRetry());
        Instant nextRetryAt = plan.triggeredAt().plus(delay);
        relayStore.markDeferred(
                message.getId(),
                publishingVersion,
                decision.nextRetry(),
                nextRetryAt,
                decision.errorCode(),
                decision.errorMessage()
        );
        context.onDeferred(message, decision.nextRetry(), nextRetryAt, decision.errorCode(), decision.errorMessage(), exception);
    }

    /**
     * 汇总异常相关属性，判断最终处理策略并封装返回，避免在主流程中散落多处局部变量。
     */
    private FailureDecision decideFailure(OutboxMessage message, RelayPlan plan, Exception exception) {
        RelayErrorKind kind = errorClassifier.classify(exception);
        int nextRetry = nextRetryCount(message);
        FailureHandling handling = determineFailureHandling(plan, kind, nextRetry);
        String errorCode = errorCode(exception);
        String errorMessage = errorMessage(exception);
        return new FailureDecision(handling, nextRetry, errorCode, errorMessage);
    }

    /**
     * 根据异常类型及下一次重试计数，判断应当立即失败还是进入重试流程。
     */
    private FailureHandling determineFailureHandling(RelayPlan plan, RelayErrorKind kind, int nextRetry) {
        if (kind == RelayErrorKind.FATAL) {
            return FailureHandling.FAIL;
        }
        if (nextRetry >= plan.maxAttempts()) {
            return FailureHandling.FAIL;
        }
        return FailureHandling.RETRY;
    }

    /**
     * 计算下一次写库时的版本号（null 视为 0 处理）。
     */
    private long nextVersionOf(OutboxMessage message) {
        long currentVersion = message.getVersion() == null ? 0L : message.getVersion();
        return currentVersion + 1;
    }

    /**
     * 计算下一次重试计数，后续会写回数据库作为最新 retryCount。
     */
    private int nextRetryCount(OutboxMessage message) {
        int currentRetry = message.getRetryCount() == null ? 0 : message.getRetryCount();
        return currentRetry + 1;
    }

    /**
     * 提取异常类型名称，用作错误码字段。
     */
    private String errorCode(Exception exception) {
        return exception.getClass().getSimpleName();
    }

    /**
     * 截断异常消息，避免过长内容撑爆存储字段。
     */
    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return null;
        }
        return StrUtil.maxLength(message, ERROR_MSG_LIMIT);
    }

    /**
     * 失败决策快照：承载异常分类后的处理方式与关键元数据。
     */
    private record FailureDecision(FailureHandling handling, int nextRetry, String errorCode, String errorMessage) {
    }

    private enum FailureHandling {
        RETRY, FAIL
    }

    /**
     * 批次上下文：封装统计计数、事件列表与日志输出，保证循环体语义清晰。
     */
    private static final class RelayContext {
        private final RelayPlan plan;
        private final List<OutboxRelayDomainEvent> events = new ArrayList<>();
        private int published;
        private int retried;
        private int failed;
        private int leaseMissed;

        private RelayContext(RelayPlan plan) {
            this.plan = plan;
        }

        /**
         * 供外层读取当前批次计划。
         */
        private RelayPlan plan() {
            return plan;
        }

        /**
         * 租约抢占失败：记录日志并积累业务事件。
         */
        private void onLeaseMissed(OutboxMessage message) {
            leaseMissed++;
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][APP] relay lease-missed messageId={} channel={} existingLeaseOwner={}",
                        message.getId(), message.getChannel(), message.getLeaseOwner());
            }
            events.add(new OutboxLeaseMissedEvent(
                    message.getId(),
                    message.getChannel(),
                    plan.leaseOwner(),
                    message.getLeaseOwner(),
                    plan.triggeredAt()
            ));
        }

        /**
         * 发布成功：记录外部消息 ID 并累计成功计数。
         */
        private void onPublished(OutboxMessage message, OutboxPublisherPort.PublishResult publishResult) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][APP] relay published messageId={} channel={} externalMsgId={}",
                        message.getId(), message.getChannel(), publishResult.messageId());
            }
            published++;
            events.add(new OutboxMessagePublishedEvent(
                    message.getId(),
                    message.getChannel(),
                    message.getPartitionKey(),
                    publishResult.messageId(),
                    plan.triggeredAt()
            ));
        }

        /**
         * 永久失败：输出错误日志并记录领域事件。
         */
        private void onFailed(OutboxMessage message,
                              int nextRetry,
                              String errorCode,
                              String errorMessage,
                              Exception exception) {
            failed++;
            log.error("[INGEST][APP] Relay publish failed permanently, messageId={} channel={} retryCount={} errorCode={}",
                    message.getId(), message.getChannel(), nextRetry, errorCode, exception);
            events.add(new OutboxMessageFailedEvent(
                    message.getId(),
                    message.getChannel(),
                    nextRetry,
                    errorCode,
                    errorMessage,
                    plan.triggeredAt()
            ));
        }

        /**
         * 延迟重试：以 WARN 级别输出并记录重试计划。
         */
        private void onDeferred(OutboxMessage message,
                                int nextRetry,
                                Instant nextRetryAt,
                                String errorCode,
                                String errorMessage,
                                Exception exception) {
            retried++;
            log.warn("[INGEST][APP] Relay publish deferred, messageId={} channel={} retryCount={} nextRetryAt={} errorCode={}",
                    message.getId(), message.getChannel(), nextRetry, nextRetryAt, errorCode, exception);
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

        /**
         * 汇总最终批次结果，为调用方返回统计数据与事件列表。
         */
        private RelayBatchResult toBatchResult(int totalMessages) {
            return new RelayBatchResult(
                    plan.channel(),
                    totalMessages,
                    published,
                    retried,
                    failed,
                    leaseMissed,
                    events
            );
        }
    }
}
