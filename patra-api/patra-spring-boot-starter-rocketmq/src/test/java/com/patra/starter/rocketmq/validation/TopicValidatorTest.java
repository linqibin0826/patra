package com.patra.starter.rocketmq.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicValidatorTest {

    private final TopicValidator validator = new TopicValidator("[A-Z_]+", "prod");

    @Test
    void validate_shouldPassWhenNamespaceMatches() {
        assertThatNoException().isThrownBy(() -> validator.validate("PROD_INGEST_TASK"));
    }

    @Test
    void validate_shouldRejectWhenNamespaceMissing() {
        assertThatThrownBy(() -> validator.validate("INGEST_TASK"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("命名空间");
    }

    @Test
    void validate_shouldRejectInvalidPattern() {
        assertThatThrownBy(() -> validator.validate("prod_ingest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("命名规范");
    }
}
