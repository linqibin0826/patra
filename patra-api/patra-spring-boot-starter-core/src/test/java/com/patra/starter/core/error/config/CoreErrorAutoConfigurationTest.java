package com.patra.starter.core.error.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CoreErrorAutoConfigurationTest {

  private final CoreErrorAutoConfiguration conf = new CoreErrorAutoConfiguration();

  @Test
  void errorObservationRecorder_branches() {
    ErrorProperties props = new ErrorProperties();

    // disabled → NO_OP
    props.getObservation().setEnabled(false);
    assertThat(conf.errorObservationRecorder(props, emptyProvider()))
        .isSameAs(ErrorObservationRecorder.NO_OP);

    // enabled but registry missing → NO_OP
    props.getObservation().setEnabled(true);
    assertThat(conf.errorObservationRecorder(props, emptyProvider()))
        .isSameAs(ErrorObservationRecorder.NO_OP);

    // enabled and registry present → Micrometer-backed implementation
    ErrorObservationRecorder rec =
        conf.errorObservationRecorder(props, of(new SimpleMeterRegistry()));
    assertThat(rec).isNotSameAs(ErrorObservationRecorder.NO_OP);
  }

  @Test
  void httpStdErrorsGroup_prefix_fallback() {
    ErrorProperties p = new ErrorProperties();
    p.setContextPrefix(null);
    HttpStdErrors.Group g = conf.httpStdErrorsGroup(p);
    assertThat(g.BAD_REQUEST().code()).startsWith("UNKNOWN-");

    p.setContextPrefix("ING");
    assertThat(conf.httpStdErrorsGroup(p).BAD_REQUEST().code()).startsWith("ING-");
  }

  @Test
  void other_factory_methods_minimum() {
    ErrorProperties p = new ErrorProperties();
    p.setContextPrefix("ING");

    // engine & pipeline
    ErrorResolutionEngine engine = conf.errorResolutionEngine(p, List.of());
    assertThat(engine).isNotNull();
    assertThat(conf.errorResolutionPipeline(engine, ofList())).isNotNull();

    // circuit breaker & interceptor
    CircuitBreaker cb = conf.errorResolutionCircuitBreaker(p);
    assertThat(cb).isNotNull();
    assertThat(conf.circuitBreakerInterceptor(cb, ErrorObservationRecorder.NO_OP, p)).isNotNull();

    // tracing
    assertThat(conf.tracingInterceptor(() -> java.util.Optional.empty())).isNotNull();
  }

  // Minimal ObjectProvider utilities used in tests
  private static <T> ObjectProvider<T> of(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return value;
      }

      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public T getIfUnique() {
        return value;
      }

      @Override
      public T getObject() {
        return value;
      }

      @Override
      public void forEach(java.util.function.Consumer<? super T> action) {
        action.accept(value);
      }

      @Override
      public java.util.stream.Stream<T> stream() {
        return java.util.stream.Stream.of(value);
      }

      @Override
      public java.util.stream.Stream<T> orderedStream() {
        return java.util.stream.Stream.of(value);
      }
    };
  }

  private static <T> ObjectProvider<T> emptyProvider() {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return null;
      }

      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }

      @Override
      public T getObject() {
        return null;
      }

      @Override
      public void forEach(java.util.function.Consumer<? super T> action) {}

      @Override
      public java.util.stream.Stream<T> stream() {
        return java.util.stream.Stream.empty();
      }

      @Override
      public java.util.stream.Stream<T> orderedStream() {
        return java.util.stream.Stream.empty();
      }
    };
  }

  private static ObjectProvider<ResolutionInterceptor> ofList() {
    ResolutionInterceptor i = (ex, inv) -> inv.proceed(ex);
    return of(i);
  }
}
