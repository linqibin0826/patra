package com.patra.starter.core.error.trace;

import com.patra.starter.core.error.config.TracingProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderBasedTraceProviderTest {

    @Test
    void should_read_from_mdc_by_configured_names() {
        TracingProperties props = new TracingProperties();
        props.setHeaderNames(List.of("traceId", "X-B3-TraceId"));
        HeaderBasedTraceProvider provider = new HeaderBasedTraceProvider(props);

        MDC.put("X-B3-TraceId", "abc");
        assertThat(provider.getCurrentTraceId()).contains("abc");
        MDC.clear();
        assertThat(provider.getCurrentTraceId()).isEmpty();
    }
}

