package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

/**
 * Provides global access to an {@link ObjectMapper}, bridging Spring-managed configuration and
 * non-Spring code paths.
 *
 * <p>Responsibilities and usage guidelines:
 *
 * <ul>
 *   <li><b>Within a Spring context</b> the preferred approach remains constructor injection. This
 *       provider simply exposes the Spring-managed instance to static or non-Spring code by
 *       registering it with {@link JsonMapperHolder} once the application context is ready.
 *   <li><b>Outside Spring</b> the {@link JsonMapperHolder} lazily creates a default mapper. When
 *       the application later starts inside Spring, this provider replaces the default instance so
 *       both environments share the same configuration.
 * </ul>
 *
 * <h3>Why not rely on this provider everywhere?</h3>
 *
 * Dependency injection offers lifecycle management, configurability, and improved testability. This
 * class acts purely as a bridge for scenarios where DI is impossible (static utilities, shared
 * libraries, or very early initialization code).
 *
 * <h3>Lifecycle and thread safety</h3>
 *
 * <ul>
 *   <li>{@link #setApplicationContext(ApplicationContext)} is invoked once during bean creation.
 *       The method caches the container-managed mapper and registers it with the global holder.
 *   <li>Registration is idempotent; a later registration simply replaces the previous mapper.
 *   <li>{@link #getObjectMapper()} falls back to {@link JsonMapperHolder} when the cached mapper is
 *       unavailable, ensuring consistent behavior regardless of initialization order.
 * </ul>
 *
 * <h3>Usage tips</h3>
 *
 * <ul>
 *   <li>Favor constructor injection inside Spring components.
 *   <li>Use {@link JsonMapperHolder#getObjectMapper()} only when DI is not an option.
 *   <li>Avoid repeatedly registering different mappers at runtime to prevent configuration drift.
 * </ul>
 */
public class ObjectMapperProvider implements ApplicationContextAware {

  /**
   * Cached {@link ObjectMapper} from the Spring container; a non-null value indicates that the
   * context is fully initialized.
   */
  private static ObjectMapper objectMapper;

  /**
   * Invoked when the Spring {@link ApplicationContext} becomes available. The managed mapper is
   * cached locally and registered with {@link JsonMapperHolder} so non-Spring callers can reuse the
   * same configuration.
   */
  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
    // Spring Boot registers a shared ObjectMapper bean by default.
    objectMapper = applicationContext.getBean(ObjectMapper.class);
    // Bridge the container-managed mapper to the common-layer holder for consistent behavior.
    JsonMapperHolder.register(objectMapper);
  }

  /**
   * Returns the {@link ObjectMapper} to use.
   *
   * <ul>
   *   <li>If the Spring context already exposed the mapper, the cached instance is returned.
   *   <li>Otherwise the call falls back to {@link JsonMapperHolder}, which may lazily create a
   *       default mapper.
   * </ul>
   *
   * Prefer constructor injection in application code; this method exists solely as a bridge for
   * code that cannot rely on DI.
   */
  public static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      return JsonMapperHolder.getObjectMapper();
    }
    return objectMapper;
  }
}
