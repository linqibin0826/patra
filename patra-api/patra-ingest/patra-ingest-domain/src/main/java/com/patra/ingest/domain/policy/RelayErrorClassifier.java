package com.patra.ingest.domain.policy;

/**
 * 失败类型分类器，用于区分可重试与不可重试异常。
 */
public interface RelayErrorClassifier {

    RelayErrorKind classify(Throwable cause);

    enum RelayErrorKind {
        TRANSIENT,
        FATAL
    }
}
