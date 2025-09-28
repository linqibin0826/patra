package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox Relay 执行过程中产生的异常。
 */
public class OutboxRelayExecutionException extends IngestException implements HasErrorTraits {

    public OutboxRelayExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
