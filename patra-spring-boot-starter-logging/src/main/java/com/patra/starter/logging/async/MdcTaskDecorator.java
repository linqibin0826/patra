package com.patra.starter.logging.async;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Task decorator for propagating MDC (Mapped Diagnostic Context) to async tasks.
 *
 * <p>When using Spring's {@code @Async} or manual async execution with thread pools, MDC is not
 * automatically propagated to worker threads. This decorator captures MDC from the calling thread
 * and restores it in the worker thread.
 *
 * <p>Critical for maintaining trace context (trace ID, span ID, correlation ID) across async
 * boundaries.
 *
 * <p>Usage with Spring async configuration:
 *
 * <pre>{@code
 * @Configuration
 * @EnableAsync
 * public class AsyncConfiguration {
 *
 *     @Bean
 *     public Executor taskExecutor() {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         executor.setCorePoolSize(10);
 *         executor.setMaxPoolSize(20);
 *         executor.setTaskDecorator(new MdcTaskDecorator()); // Enable MDC propagation
 *         executor.initialize();
 *         return executor;
 *     }
 * }
 * }</pre>
 *
 * <p>Usage with @Async methods:
 *
 * <pre>{@code
 * @Service
 * public class MyService {
 *
 *     @Async
 *     public void processAsync() {
 *         // MDC trace context is available here
 *         log.info("Async processing"); // [traceId=xxx][spanId=yyy]
 *     }
 * }
 * }</pre>
 *
 * @see TaskDecorator
 * @see MDC
 * @since 0.1.0
 */
public class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    // Capture MDC from calling thread
    Map<String, String> contextMap = MDC.getCopyOfContextMap();

    return () -> {
      try {
        // Restore MDC in worker thread
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }

        // Execute the original task
        runnable.run();
      } finally {
        // Clear MDC after task completion to prevent leakage
        MDC.clear();
      }
    };
  }
}
