package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorResolutionTest {

    private static final ErrorCodeLike CODE = new ErrorCodeLike() {
        @Override public String code() { return "ING-0404"; }
        @Override public int httpStatus() { return 404; }
    };

    @Test
    void should_validate_arguments() {
        assertThatThrownBy(() -> new ErrorResolution(null, 200))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ErrorResolution(CODE, 99))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ErrorResolution(CODE, 600))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

