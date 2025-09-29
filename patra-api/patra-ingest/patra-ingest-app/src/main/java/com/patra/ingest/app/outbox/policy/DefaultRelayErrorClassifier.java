package com.patra.ingest.app.outbox.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import org.springframework.stereotype.Component;

/**
 * 默认的异常分类策略：
 * <ul>
 *     <li>非法参数、负载解析异常 → FATAL</li>
 *     <li>其余归类为 TRANSIENT，交由重试策略处理</li>
 * </ul>
 */
@Component
public class DefaultRelayErrorClassifier implements RelayErrorClassifier {

    @Override
    public RelayErrorKind classify(Throwable cause) {
        Throwable root = rootCause(cause);
        if (root instanceof IllegalArgumentException || root instanceof IllegalStateException || root instanceof JsonProcessingException) {
            return RelayErrorKind.FATAL;
        }
        return RelayErrorKind.TRANSIENT;
    }

    private Throwable rootCause(Throwable cause) {
        Throwable current = cause;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
