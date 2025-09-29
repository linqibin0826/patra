package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import com.patra.starter.core.error.spi.TraceProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TracingInterceptorTest {

    @Test
    void should_delegate_and_not_break_result() {
        TraceProvider provider = () -> Optional.of("trace-1");
        TracingInterceptor interceptor = new TracingInterceptor(provider);
        ErrorResolution r = interceptor.intercept(new RuntimeException("x"), ex -> new ErrorResolution(new com.patra.common.error.codes.ErrorCodeLike() {
            @Override public String code() { return "ING-0400"; }
            @Override public int httpStatus() { return 400; }
        }, 400));
        assertThat(r.httpStatus()).isEqualTo(400);
    }
}
