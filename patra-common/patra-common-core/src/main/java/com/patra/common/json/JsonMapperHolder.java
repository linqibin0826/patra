package com.patra.common.json;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// 共享 {@link ObjectMapper} 的全局(非 Spring)持有者。
///
/// 目标:
///
/// - 为在 Spring 之外运行的基础设施和工具代码提供一致的 JSON 配置。
///   - 避免重复实例化 `ObjectMapper`,这会导致配置分散和不必要的开销。
///   - 通过允许启动器将容器实例桥接到此持有者,与 Spring 管理的 mapper 保持一致。
///
/// ### 与 Spring 依赖注入的关系
///
/// 在 Spring 中,优先使用构造函数注入:
///
/// ```java
/// @Autowired
/// private ObjectMapper objectMapper;
/// ```
///
/// 容器管理生命周期、配置和作用域,保持代码可测试和可替换。
///
/// 此类是在 DI 不可用的上下文中使用的静态持有者。关键区别:
///
/// - **来源**:DI 提供 mapper;持有者依赖于显式注册或延迟回退。
///   - **生命周期**:DI 由容器管理;持有者在首次访问或调用 {@link #register(ObjectMapper)} 时初始化。
///   - **一致性**:回退 mapper 使用 {@link JsonMapper#builder()} 构建,可能与自定义应用程序设置不一致; 如果需要保持一致,请尽早注册。
///   - **用例**:适用于无法依赖 DI 的静态辅助方法、SDK 或驱动代码。 避免在业务服务内使用以保持可测试性。
///
/// ### 从 Spring 桥接
///
/// 当 `patra-spring-boot-starter-core` 在类路径上时,其 `JacksonProvider` 会在 `ApplicationContext` 就绪后注册容器管理的
// mapper。在 Spring 之外, {@link #getObjectMapper()} 延迟创建默认 mapper。
///
/// ### 线程安全
///
/// {@link AtomicReference} 提供安全的发布和延迟初始化。首次访问通过 CAS 创建默认 mapper; 显式注册会替换当前实例。
///
/// ### 使用指南
///
/// - 在 Spring 管理的代码中优先使用 DI;仅在注入不可能的地方回退到此持有者。
///   - 在应用程序启动早期注册容器管理的 mapper,以避免稍后从回退实例切换。
///   - 不提供重置方法以防止意外的运行时切换。对于测试,在套件设置时注册测试 mapper 并在之后恢复。
///
/// ### 示例
///
/// ```java
/// // 非 Spring 用法
/// ObjectMapper om = JsonMapperHolder.getObjectMapper();
/// String json = om.writeValueAsString(payload);
///
/// // Spring 用法(注册发生在 JacksonProvider 中)
/// @Service
/// public class FooService {
///   private final ObjectMapper om; // 仍然推荐通过 DI
///   public FooService(ObjectMapper om) { this.om = om;
/// ```
public final class JsonMapperHolder {

  /// 全局 {@link ObjectMapper} 引用。`null` 表示尚未注册任何实例。
  private static final AtomicReference<ObjectMapper> HOLDER = new AtomicReference<>();

  private JsonMapperHolder() {}

  /// 返回共享的 {@link ObjectMapper}。
  ///
  /// 已注册的实例优先。如果未注册,则使用 {@link JsonMapper.Builder#findAndAddModules()} 延迟创建默认 {@link JsonMapper}。
  public static ObjectMapper getObjectMapper() {
    ObjectMapper mapper = HOLDER.get();
    if (mapper != null) {
      return mapper;
    }
    ObjectMapper created = createDefault();
    if (HOLDER.compareAndSet(null, created)) {
      return created;
    }
    return HOLDER.get();
  }

  /// 注册(或替换)全局 {@link ObjectMapper}。
  ///
  /// 典型调用者:
  ///
  /// - Spring 的 `JacksonProvider` 在上下文就绪后。
  ///   - 非 Spring 环境中的引导代码。
  ///
  /// 避免在运行时频繁更改以防止配置偏移。
  public static void register(ObjectMapper objectMapper) {
    HOLDER.set(Objects.requireNonNull(objectMapper, "objectMapper 不能为 null"));
  }

  /// 创建未注册时使用的回退 {@link ObjectMapper}。
  private static ObjectMapper createDefault() {
    return JsonMapper.builder().findAndAddModules().build();
  }
}
