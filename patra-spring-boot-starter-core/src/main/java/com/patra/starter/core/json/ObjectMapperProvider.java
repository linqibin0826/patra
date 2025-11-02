package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

/**
 * 提供对 {@link ObjectMapper} 的全局访问，连接 Spring 管理的配置和非 Spring 代码路径。
 *
 * <p>职责和使用指南：
 *
 * <ul>
 *   <li><b>在 Spring 上下文中</b>首选方法仍然是构造注入。本提供者通过在应用上下文就绪时将 Spring 管理的实例注册到 {@link JsonMapperHolder}，
 *       将其暴露给静态或非 Spring 代码。
 *   <li><b>在 Spring 外部</b>{@link JsonMapperHolder} 会延迟创建默认映射器。当应用稍后在 Spring 内启动时，
 *       本提供者替换默认实例，使两个环境共享相同的配置。
 * </ul>
 *
 * <h3>为什么不在任何地方都依赖这个提供者？</h3>
 *
 * 依赖注入提供生命周期管理、可配置性和改进的可测试性。本类纯粹作为 DI 不可行的场景的桥梁 （静态工具、共享库或非常早期的初始化代码）。
 *
 * <h3>生命周期和线程安全</h3>
 *
 * <ul>
 *   <li>{@link #setApplicationContext(ApplicationContext)} 在 bean 创建期间被调用一次。
 *       该方法缓存容器管理的映射器并将其注册到全局持有者。
 *   <li>注册是幂等的；后续注册仅替换前一个映射器。
 *   <li>{@link #getObjectMapper()} 在缓存的映射器不可用时回退到 {@link JsonMapperHolder}， 确保无论初始化顺序如何都保持一致的行为。
 * </ul>
 *
 * <h3>使用提示</h3>
 *
 * <ul>
 *   <li>在 Spring 组件内部优先使用构造注入。
 *   <li>仅当 DI 不可行时才使用 {@link JsonMapperHolder#getObjectMapper()}。
 *   <li>避免在运行时重复注册不同的映射器，防止配置漂移。
 * </ul>
 */
public class ObjectMapperProvider implements ApplicationContextAware {

  /** 来自 Spring 容器的缓存 {@link ObjectMapper}；非空值表示上下文已完全初始化。 */
  private static ObjectMapper objectMapper;

  /**
   * 在 Spring {@link ApplicationContext} 变为可用时被调用。管理的映射器被本地缓存并注册到 {@link JsonMapperHolder}， 以便非
   * Spring 调用者可以复用相同的配置。
   */
  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
    // Spring Boot 默认注册一个共享的 ObjectMapper bean。
    objectMapper = applicationContext.getBean(ObjectMapper.class);
    // 将容器管理的映射器桥接到公共层持有者，以确保行为一致。
    JsonMapperHolder.register(objectMapper);
  }

  /**
   * 返回要使用的 {@link ObjectMapper}。
   *
   * <ul>
   *   <li>如果 Spring 上下文已经暴露了映射器，则返回缓存的实例。
   *   <li>否则调用回退到 {@link JsonMapperHolder}，可能延迟创建默认映射器。
   * </ul>
   *
   * 在应用代码中优先使用构造注入；该方法仅作为无法依赖 DI 的代码的桥梁存在。
   */
  public static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      return JsonMapperHolder.getObjectMapper();
    }
    return objectMapper;
  }
}
