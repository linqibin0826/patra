package com.patra.ingest.domain.policy;

/**
 * Classifies relay failures into retryable or fatal categories.
 */
public interface RelayErrorClassifier {

    RelayErrorKind classify(Throwable cause);

    enum RelayErrorKind {
        TRANSIENT,
        FATAL
    }
}
