package com.patra.starter.logging.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.logging.context.DistributedTraceContext;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultTraceContextHolderTest {

  @Test
  @DisplayName("Trace context holder should use SkyWalking spanId for span identifier")
  void shouldUseSkyWalkingSpanId() {
    DefaultTraceContextHolder.TraceContextExtractor extractor = new StubExtractor("trace-123", "7");

    DefaultTraceContextHolder holder = new DefaultTraceContextHolder(extractor);

    Optional<DistributedTraceContext> context = holder.getContext();
    assertThat(context).isPresent();
    assertThat(context.get().spanId()).isEqualTo("7");
  }

  @Test
  @DisplayName("Trace context holder should surface distinct spanIds for nested SkyWalking spans")
  void shouldExposeDistinctSpanIdsForNestedCalls() {
    DefaultTraceContextHolder.TraceContextExtractor extractor =
        new StubExtractor("trace-xyz", "1", "2");

    DefaultTraceContextHolder holder = new DefaultTraceContextHolder(extractor);

    String outerSpanId = holder.getContext().map(DistributedTraceContext::spanId).orElseThrow();

    holder.clearContext();

    String innerSpanId = holder.getContext().map(DistributedTraceContext::spanId).orElseThrow();

    assertThat(outerSpanId).isEqualTo("1");
    assertThat(innerSpanId)
        .as("Nested SkyWalking spans must surface unique span identifiers")
        .isEqualTo("2");
  }

  private static final class StubExtractor
      implements DefaultTraceContextHolder.TraceContextExtractor {

    private final String traceId;
    private final String[] spanIds;
    private int index = 0;

    private StubExtractor(String traceId, String... spanIds) {
      this.traceId = traceId;
      this.spanIds = spanIds;
    }

    @Override
    public String currentTraceId() {
      return traceId;
    }

    @Override
    public String currentSpanId() {
      int currentIndex = Math.min(index, spanIds.length - 1);
      String spanId = spanIds[currentIndex];
      index++;
      return spanId;
    }
  }
}
