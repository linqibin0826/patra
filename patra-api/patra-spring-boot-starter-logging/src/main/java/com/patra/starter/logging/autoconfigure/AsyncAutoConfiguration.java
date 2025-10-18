package com.patra.starter.logging.autoconfigure;

import com.patra.starter.logging.async.MdcTaskDecorator;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ReflectionUtils;

/**
 * Auto-configuration that decorates existing async executors with {@link MdcTaskDecorator}.
 *
 * <p>This configuration is intentionally <strong>opt-in</strong>: it does not enable asynchronous
 * execution on behalf of the host application. Instead, it detects user-provided {@link
 * AsyncConfigurer} or {@link ThreadPoolTaskExecutor} beans and attaches the MDC propagating task
 * decorator when no decorator has been configured yet.
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>NO {@code @EnableAsync} declaration – the host application remains in control of enabling
 *       asynchronous execution.
 *   <li>If a {@link ThreadPoolTaskExecutor} bean is present, the decorator is applied before the
 *       executor is initialized.
 *   <li>If an {@link AsyncConfigurer} bean supplies the executor, the decorator is applied to the
 *       returned {@link ThreadPoolTaskExecutor}, preserving existing decorators when present.
 * </ul>
 *
 * <p>This preserves backward compatibility for services that never opted into async execution,
 * while still fulfilling FR-004's requirement to propagate trace context across async boundaries
 * when async is enabled.
 *
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor")
public class AsyncAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AsyncAutoConfiguration.class);

  public AsyncAutoConfiguration() {
    log.info("Initializing Async MDC decoration support (opt-in)");
  }

  /**
   * Registers a {@link BeanPostProcessor} that decorates {@link ThreadPoolTaskExecutor} instances
   * with {@link MdcTaskDecorator} when they do not already expose a custom {@link TaskDecorator}.
   *
   * <p>The processor handles both direct {@link ThreadPoolTaskExecutor} beans and executors
   * provided through {@link AsyncConfigurer#getAsyncExecutor()}.
   */
  @Bean
  public BeanPostProcessor mdcTaskDecoratorBeanPostProcessor() {
    return new BeanPostProcessor() {

      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName)
          throws BeansException {
        if (bean instanceof ThreadPoolTaskExecutor executor) {
          applyDecoratorIfNecessary(executor, beanName);
        }

        if (bean instanceof AsyncConfigurer configurer) {
          Executor executor = configurer.getAsyncExecutor();
          if (executor instanceof ThreadPoolTaskExecutor threadPoolTaskExecutor) {
            applyDecoratorIfNecessary(threadPoolTaskExecutor, beanName + ".asyncConfigurer");
          } else if (executor instanceof ExecutorService) {
            log.debug(
                "Detected AsyncConfigurer '{}' providing ExecutorService '{}'; MDC decoration is"
                    + " skipped because the executor type does not support TaskDecorator.",
                beanName,
                executor.getClass().getName());
          }
        }

        return bean;
      }

      private void applyDecoratorIfNecessary(ThreadPoolTaskExecutor executor, String beanName) {
        if (hasExistingTaskDecorator(executor)) {
          log.debug(
              "Async executor '{}' already defines TaskDecorator '{}'; MDC propagation left"
                  + " unchanged.",
              beanName,
              getExistingTaskDecoratorClassName(executor));
          return;
        }

        log.info(
            "Applying MdcTaskDecorator to async executor '{}' to propagate trace context across async"
                + " boundaries.",
            beanName);
        executor.setTaskDecorator(new MdcTaskDecorator());
      }
    };
  }

  private boolean hasExistingTaskDecorator(ThreadPoolTaskExecutor executor) {
    try {
      var field = ReflectionUtils.findField(ThreadPoolTaskExecutor.class, "taskDecorator");
      if (field == null) {
        return false;
      }
      ReflectionUtils.makeAccessible(field);
      return ReflectionUtils.getField(field, executor) != null;
    } catch (Exception ex) {
      log.warn(
          "Unable to inspect TaskDecorator on executor '{}'; assuming none is configured ({}).",
          executor,
          ex.getMessage());
      return false;
    }
  }

  private String getExistingTaskDecoratorClassName(ThreadPoolTaskExecutor executor) {
    try {
      var field = ReflectionUtils.findField(ThreadPoolTaskExecutor.class, "taskDecorator");
      if (field != null) {
        ReflectionUtils.makeAccessible(field);
        Object decorator = ReflectionUtils.getField(field, executor);
        return decorator != null ? decorator.getClass().getName() : "null";
      }
    } catch (Exception ex) {
      log.debug("Failed to resolve existing TaskDecorator class name: {}", ex.getMessage());
    }
    return "unknown";
  }
}
