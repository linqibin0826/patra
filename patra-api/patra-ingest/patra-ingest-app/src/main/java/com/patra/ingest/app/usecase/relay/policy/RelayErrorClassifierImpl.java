package com.patra.ingest.app.usecase.relay.policy;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import org.springframework.stereotype.Component;

/**
 * Default exception classification strategy: minimal yet practical rules for deciding between FATAL
 * and TRANSIENT.
 *
 * <p>Classification rules:
 *
 * <ul>
 *   <li>Configuration, illegal state, or parameter issues (IllegalArgument, IllegalState,
 *       OutboxRelayExecutionException) -> FATAL.
 *   <li>JSON serialization or deserialization failure -> FATAL (data typically unrecoverable).
 *   <li>All other cases -> TRANSIENT and delegated to retry policies (network hiccups, downstream
 *       outages, etc.).
 * </ul>
 *
 * Extensibility: replace this implementation with composed strategies to support finer-grained
 * matching (for example by error code).
 */
@Component
public class RelayErrorClassifierImpl implements RelayErrorClassifier {

  @Override
  public RelayErrorKind classify(Throwable cause) {
    Throwable publish = findPublishException(cause);
    if (publish instanceof OutboxPublishException publishException) {
      return publishException.getReason().isFatal()
          ? RelayErrorKind.FATAL
          : RelayErrorKind.TRANSIENT;
    }
    Throwable root = ExceptionUtil.getRootCause(cause);
    if (root instanceof OutboxRelayExecutionException
        || root instanceof IllegalArgumentException
        || root instanceof IllegalStateException
        || root instanceof JsonProcessingException) {
      return RelayErrorKind.FATAL;
    }
    return RelayErrorKind.TRANSIENT;
  }

  private Throwable findPublishException(Throwable cause) {
    Throwable current = cause;
    while (current != null) {
      if (current instanceof OutboxPublishException) {
        return current;
      }
      current = current.getCause();
    }
    return null;
  }
}
