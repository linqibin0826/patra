package com.patra.common.error;

import com.patra.common.error.codes.ErrorCodeLike;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationExceptionTest {

    private static final ErrorCodeLike DUMMY = new ErrorCodeLike() {
        @Override public String code() { return "ING-0404"; }
        @Override public int httpStatus() { return 404; }
    };

    @Test
    void constructor_and_getter() {
        ApplicationException ex = new ApplicationException(DUMMY, "not found");
        assertThat(ex.getErrorCode()).isSameAs(DUMMY);

        ApplicationException ex2 = new ApplicationException(DUMMY, "msg", new RuntimeException("cause"));
        assertThat(ex2.getErrorCode()).isSameAs(DUMMY);
        assertThat(ex2.getCause()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_should_validate_errorCode() {
        assertThatThrownBy(() -> new ApplicationException(null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationException(null, "x", new RuntimeException()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

