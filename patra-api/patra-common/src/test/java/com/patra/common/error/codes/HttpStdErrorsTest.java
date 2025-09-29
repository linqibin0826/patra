package com.patra.common.error.codes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpStdErrorsTest {

    @Test
    void of_should_cache_and_build_prefixed_codes() {
        HttpStdErrors.Group g1 = HttpStdErrors.of("ING");
        HttpStdErrors.Group g2 = HttpStdErrors.of("ING");
        assertThat(g1).isSameAs(g2);

        assertThat(g1.NOT_FOUND().code()).isEqualTo("ING-0404");
        assertThat(g1.NOT_FOUND().httpStatus()).isEqualTo(404);

        // null/blank 回退 UNKNOWN
        assertThat(HttpStdErrors.of(null).BAD_REQUEST().code()).isEqualTo("UNKNOWN-0400");
        assertThat(HttpStdErrors.of(" ").INTERNAL_ERROR().httpStatus()).isEqualTo(500);
    }
}

