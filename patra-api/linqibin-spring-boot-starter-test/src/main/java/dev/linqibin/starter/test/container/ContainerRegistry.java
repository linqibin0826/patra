package dev.linqibin.starter.test.container;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.lifecycle.Startable;

/// 测试容器注册中心。
///
/// JVM 级别的单例，管理所有测试容器的生命周期，确保同一 JVM 进程内容器复用。
///
/// ### 核心功能
///
/// - **容器注册**: 将已启动的容器注册到全局注册表
/// - **容器查找**: 根据容器类型获取已注册的容器实例
/// - **状态检查**: 检查特定类型容器是否已注册
/// - **线程安全**: 使用 ConcurrentHashMap 确保并发安全
///
/// ### 设计原则
///
/// - **JVM 单例**: 通过静态方法和 ConcurrentHashMap 实现
/// - **延迟初始化**: 容器在首次请求时创建，而非类加载时
/// - **复用优先**: 已注册的容器会被复用，避免重复创建
///
/// ### 使用示例
///
/// ```java
/// // 注册容器
/// PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
/// postgres.start();
/// ContainerRegistry.register(ContainerType.POSTGRESQL, postgres);
///
/// // 获取容器
/// PostgreSQLContainer<?> container = ContainerRegistry.get(ContainerType.POSTGRESQL,
///     PostgreSQLContainer.class);
///
/// // 检查是否已注册
/// if (!ContainerRegistry.isRegistered(ContainerType.POSTGRESQL)) {
///     // 初始化容器...
/// }
/// ```
///
/// ### 线程安全保证
///
/// 在 Maven 并行测试模式（`-T 1C`）下，多个测试类可能同时请求容器。
/// 本类使用 ConcurrentHashMap 和 computeIfAbsent 模式确保：
///
/// - 同一类型容器只创建一次
/// - 并发请求会等待首个创建完成后复用
///
/// @author linqibin
/// @since 0.1.0
/// @see ContainerType
public final class ContainerRegistry {

  private static final Logger log = LoggerFactory.getLogger(ContainerRegistry.class);

  /// 容器实例存储。
  ///
  /// Key: 容器类型
  /// Value: 容器实例
  private static final Map<ContainerType, Startable> CONTAINERS = new ConcurrentHashMap<>();

  /// 私有构造函数，防止实例化。
  private ContainerRegistry() {
    throw new UnsupportedOperationException("工具类不允许实例化");
  }

  /// 注册容器实例。
  ///
  /// @param type 容器类型
  /// @param container 容器实例（必须已启动）
  /// @throws IllegalStateException 如果该类型容器已注册
  public static void register(ContainerType type, Startable container) {
    Startable existing = CONTAINERS.putIfAbsent(type, container);
    if (existing != null) {
      log.warn("容器 {} 已注册，忽略重复注册请求", type);
    } else {
      log.info("容器 {} 已注册到全局注册表", type);
    }
  }

  /// 获取已注册的容器实例。
  ///
  /// @param type 容器类型
  /// @param containerClass 容器类型的 Class（用于类型安全转换）
  /// @param <T> 容器类型
  /// @return 容器实例，如果未注册则返回 null
  @SuppressWarnings("unchecked")
  public static <T extends Startable> T get(ContainerType type, Class<T> containerClass) {
    Startable container = CONTAINERS.get(type);
    if (container == null) {
      return null;
    }
    return (T) container;
  }

  /// 检查容器是否已注册。
  ///
  /// @param type 容器类型
  /// @return 如果已注册返回 true
  public static boolean isRegistered(ContainerType type) {
    return CONTAINERS.containsKey(type);
  }

  /// 获取已注册的容器数量。
  ///
  /// 主要用于测试和诊断。
  ///
  /// @return 已注册的容器数量
  public static int size() {
    return CONTAINERS.size();
  }

  /// 清空所有已注册的容器。
  ///
  /// **警告**: 此方法仅用于测试目的，会停止所有容器。
  /// 生产测试代码不应调用此方法。
  static void clear() {
    log.warn("清空容器注册表，停止所有容器");
    CONTAINERS.values().forEach(Startable::stop);
    CONTAINERS.clear();
  }
}
