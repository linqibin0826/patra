package com.patra.starter.core.error.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.model.ErrorResolution;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

class ErrorResolutionPipelineTest {

  @Order(2)
  static class I1 implements ResolutionInterceptor {
    @Override
    public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
      ErrorResolution r = invocation.proceed(exception);
      // Keep the result unchanged; this interceptor only validates ordering
      return r;
    }
  }

  @Order(1)
  static class I0 implements ResolutionInterceptor {
    @Override
    public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
      return invocation.proceed(exception);
    }
  }

  @Test
  void should_build_chain_in_order_and_delegate_to_engine() {
    ErrorResolutionEngine engine =
        ex ->
            new ErrorResolution(
                new com.patra.common.error.codes.ErrorCodeLike() {
                  @Override
                  public String code() {
                    return "ING-0404";
                  }

                  @Override
                  public int httpStatus() {
                    return 404;
                  }
                },
                404);
    ErrorResolutionPipeline p = new ErrorResolutionPipeline(engine, List.of(new I1(), new I0()));
    // getInterceptors returns a sorted, immutable list
    assertThat(p.getInterceptors()).hasSize(2);
    assertThat(p.getInterceptors().get(0)).isInstanceOf(I0.class);

    ErrorResolution r = p.resolve(new RuntimeException("x"));
    assertThat(r.httpStatus()).isEqualTo(404);
  }
}
