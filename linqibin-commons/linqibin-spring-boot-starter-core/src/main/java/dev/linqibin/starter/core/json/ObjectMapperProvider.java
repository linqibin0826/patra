package dev.linqibin.starter.core.json;

import dev.linqibin.commons.json.JsonMapperHolder;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.ObjectMapper;

/// 提供对 {@link ObjectMapper} 的全局访问，连接 Spring 管理的配置和非 Spring 代码路径。
///
/// 职责和使用指南：
///
/// - **在 Spring 上下文中**首选方法仍然是构造注入。本提供者通过构造时将 Spring 管理的实例注册到 {@link JsonMapperHolder}，
///       将其暴露给静态或非 Spring 代码。
/// - **在 Spring 外部**{@link JsonMapperHolder} 会延迟创建默认映射器。当应用稍后在 Spring 内启动时，
///       本提供者替换默认实例，使两个环境共享相同的配置。
///
/// ### 为什么不在任何地方都依赖这个提供者？
///
/// 依赖注入提供生命周期管理、可配置性和改进的可测试性。本类纯粹作为 DI 不可行的场景的桥梁（静态工具、共享库或非常早期的初始化代码）。
///
/// ### 生命周期和线程安全
///
/// - 通过构造函数接收 ObjectMapper 并注册到全局持有者
/// - 注册是幂等的；后续注册仅替换前一个映射器
/// - {@link #getObjectMapper()} 在缓存的映射器不可用时回退到 {@link JsonMapperHolder}，确保无论初始化顺序如何都保持一致的行为
///
/// ### 使用提示
///
/// - 在 Spring 组件内部优先使用构造注入。
/// - 仅当 DI 不可行时才使用 {@link JsonMapperHolder#getObjectMapper()}。
/// - 避免在运行时重复注册不同的映射器，防止配置漂移。
///
public class ObjectMapperProvider {

  /// 来自 Spring 容器的缓存 {@link ObjectMapper}。
  ///
  /// 使用 {@link AtomicReference} 保证多线程环境下的安全发布（safe publication）。
  /// 虽然 Spring Bean 初始化通常是单线程的，但静态字段的发布需要考虑：
  ///
  /// - 其他线程可能在 Bean 初始化完成前调用 {@link #getObjectMapper()}
  /// - 普通赋值在 JMM 下不保证对其他线程立即可见
  /// - AtomicReference 提供 volatile 语义，确保写入后立即对其他线程可见
  private static final AtomicReference<ObjectMapper> OBJECT_MAPPER = new AtomicReference<>();

  /// 使用 Spring 管理的 ObjectMapper 创建 Provider。
  ///
  /// 构造函数会将传入的 ObjectMapper 注册到 {@link JsonMapperHolder}，以便非 Spring 代码可以复用相同的配置。
  ///
  /// @param objectMapper Spring 容器中的 ObjectMapper bean
  public ObjectMapperProvider(ObjectMapper objectMapper) {
    OBJECT_MAPPER.set(objectMapper);
    // 将容器管理的映射器桥接到公共层持有者，以确保行为一致。
    JsonMapperHolder.register(objectMapper);
  }

  /// 返回要使用的 {@link ObjectMapper}。
  ///
  /// - 如果 Spring 上下文已经暴露了映射器，则返回缓存的实例。
  /// - 否则回退到 {@link JsonMapperHolder}，可能延迟创建默认映射器。
  ///
  /// 在应用代码中优先使用构造注入；该方法仅作为无法依赖 DI 的代码的桥梁存在。
  public static ObjectMapper getObjectMapper() {
    ObjectMapper mapper = OBJECT_MAPPER.get();
    if (mapper == null) {
      return JsonMapperHolder.getObjectMapper();
    }
    return mapper;
  }
}
