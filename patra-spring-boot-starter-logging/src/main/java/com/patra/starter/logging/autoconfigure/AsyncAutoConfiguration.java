package com.patra.starter.logging.autoconfigure;

import com.patra.starter.logging.async.MdcTaskDecorator;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Auto-configuration for async task execution with MDC propagation.
 *
 * <p>Configures Spring's {@code @Async} support to propagate MDC (and trace context) to worker
 * threads.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Automatic MDC propagation via {@link MdcTaskDecorator}
 *   <li>Thread pool with sensible defaults (core=10, max=50, queue=100)
 *   <li>Exception handler that logs async failures with trace context
 * </ul>
 *
 * @see MdcTaskDecorator
 * @see AsyncConfigurer
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.scheduling.annotation.EnableAsync")
public class AsyncAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AsyncAutoConfiguration.class);

  public AsyncAutoConfiguration() {
    log.info("Initializing Async MDC Propagation (Phase 3 - US1)");
  }

  /**
   * Configures async task executor with MDC propagation.
   *
   * <p>This configuration is activated when {@code @EnableAsync} is present on any application
   * configuration class.
   */
  @Configuration
  @EnableAsync
  public static class AsyncTaskExecutorConfiguration implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskExecutorConfiguration.class);

    @Override
    public Executor getAsyncExecutor() {
      log.debug("Configuring async task executor with MDC propagation");

      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(10);
      executor.setMaxPoolSize(50);
      executor.setQueueCapacity(100);
      executor.setThreadNamePrefix("async-mdc-");
      executor.setTaskDecorator(new MdcTaskDecorator()); // Enable MDC propagation
      executor.setWaitForTasksToCompleteOnShutdown(true);
      executor.setAwaitTerminationSeconds(60);
      executor.initialize();

      return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
      return (throwable, method, params) -> {
        // Log async exceptions with trace context (MDC is available here)
        log.error(
            "Async method execution failed: {}.{} with params {}",
            method.getDeclaringClass().getSimpleName(),
            method.getName(),
            params,
            throwable);
      };
    }
  }
}
