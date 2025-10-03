package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;

import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox 发布阶段的异常封装。
 * <p>携带 {@link Reason} 区分可重试与不可重试场景，供上层错误分类器做细粒度判定。</p>
 */
public class OutboxPublishException extends OutboxRelayExecutionException {

    private final Reason reason;

    public OutboxPublishException(Reason reason, String message) {
        super(message, null);
        this.reason = reason;
    }

    public OutboxPublishException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        if (reason == Reason.CHANNEL_NOT_ALLOWED || reason == Reason.HEADERS_INVALID) {
            return EnumSet.of(ErrorTrait.RULE_VIOLATION);
        }
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }

    /** 发布异常类型。 */
    public enum Reason {
        CHANNEL_NOT_ALLOWED(true),
        HEADERS_INVALID(true),
        SEND_FAILED(false);

        private final boolean fatal;

        Reason(boolean fatal) {
            this.fatal = fatal;
        }

        public boolean isFatal() {
            return fatal;
        }
    }
}
