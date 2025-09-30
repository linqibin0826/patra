package com.patra.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    private static final class TestDomainException extends DomainException {

        private TestDomainException(String message) {
            super(message);
        }

        private TestDomainException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Test
    void constructor_shouldKeepMessage() {
        DomainException ex = new TestDomainException("领域错误");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("领域错误");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void constructor_withCause_shouldExposeCause() {
        Throwable cause = new IllegalArgumentException("bad");
        DomainException ex = new TestDomainException("出错", cause);
        assertThat(ex.getMessage()).isEqualTo("出错");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
