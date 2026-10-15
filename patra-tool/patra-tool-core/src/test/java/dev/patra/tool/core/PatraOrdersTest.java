package dev.patra.tool.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatraOrdersTest {

  @Test
  void security_rethrow_runs_before_global_handler() {
    assertThat(PatraOrders.ERROR_ADVICE_SECURITY_RETHROW)
        .isLessThan(PatraOrders.ERROR_ADVICE_GLOBAL);
  }

  @Test
  void command_interceptors_run_tracing_then_logging_then_metrics() {
    assertThat(PatraOrders.CQRS_INTERCEPTOR_TRACING)
        .isLessThan(PatraOrders.CQRS_INTERCEPTOR_LOGGING)
        .isLessThan(PatraOrders.CQRS_INTERCEPTOR_METRICS);
  }

  @Test
  void high_cardinality_filter_runs_before_common_tags() {
    assertThat(PatraOrders.METER_FILTER_HIGH_CARDINALITY)
        .isLessThan(PatraOrders.METER_FILTER_COMMON_TAGS);
  }
}
