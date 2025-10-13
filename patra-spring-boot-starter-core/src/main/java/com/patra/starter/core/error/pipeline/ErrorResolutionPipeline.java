package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.model.ErrorResolution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * Executes the configured {@link ResolutionInterceptor} chain and delegates to the {@link
 * ErrorResolutionEngine} to obtain a normalized error representation.
 */
public class ErrorResolutionPipeline {

  private final ErrorResolutionEngine engine;
  private final List<ResolutionInterceptor> interceptors;

  public ErrorResolutionPipeline(
      ErrorResolutionEngine engine, List<ResolutionInterceptor> interceptors) {
    this.engine = engine;
    if (interceptors == null || interceptors.isEmpty()) {
      this.interceptors = Collections.emptyList();
    } else {
      List<ResolutionInterceptor> ordered = new ArrayList<>(interceptors);
      AnnotationAwareOrderComparator.sort(ordered);
      this.interceptors = Collections.unmodifiableList(ordered);
    }
  }

  /**
   * Resolves the supplied exception through the interceptor pipeline.
   *
   * @param exception the exception to resolve
   * @return the normalized error representation
   */
  public ErrorResolution resolve(Throwable exception) {
    ResolutionInvocation invocation = buildInvocationChain();
    return invocation.proceed(exception);
  }

  private ResolutionInvocation buildInvocationChain() {
    ResolutionInvocation tail = engine::resolve;
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      ResolutionInterceptor interceptor = interceptors.get(i);
      ResolutionInvocation next = tail;
      tail = ex -> interceptor.intercept(ex, next);
    }
    return tail;
  }

  /**
   * Returns the interceptor list in the order in which it will be applied.
   *
   * @return ordered and immutable interceptor list
   */
  public List<ResolutionInterceptor> getInterceptors() {
    return interceptors;
  }
}
