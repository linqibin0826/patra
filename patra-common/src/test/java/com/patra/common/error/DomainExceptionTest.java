package com.patra.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
    DomainException ex = new TestDomainException("Domain failure");
    assertThat(ex).isInstanceOf(RuntimeException.class);
    assertThat(ex.getMessage()).isEqualTo("Domain failure");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void constructor_withCause_shouldExposeCause() {
    Throwable cause = new IllegalArgumentException("bad");
    DomainException ex = new TestDomainException("Something went wrong", cause);
    assertThat(ex.getMessage()).isEqualTo("Something went wrong");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
