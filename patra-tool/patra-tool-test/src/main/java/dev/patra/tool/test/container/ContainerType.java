package dev.patra.tool.test.container;

/// 测试容器类型枚举。
///
/// 定义 starter-test 支持的容器类型，用于容器注册和查找。
///
/// ### 支持的容器类型
///
/// - **POSTGRESQL**: PostgreSQL 数据库容器
/// - **REDIS**: Redis 缓存容器
///
/// ### 使用示例
///
/// ```java
/// // 获取已注册的 PostgreSQL 容器
/// PostgreSQLContainer<?> postgres = ContainerRegistry.get(ContainerType.POSTGRESQL);
///
/// // 检查容器是否已注册
/// if (ContainerRegistry.isRegistered(ContainerType.REDIS)) {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see ContainerRegistry
public enum ContainerType {

  /// PostgreSQL 数据库容器。
  ///
  /// 使用 postgres:17 镜像，支持配置化数据库名。
  POSTGRESQL,

  /// Redis 缓存容器。
  ///
  /// 使用 redis:7 镜像，支持 Redisson 集成测试。
  REDIS,

  /// MinIO 对象存储容器。
  ///
  /// 使用 minio/minio:RELEASE.2024-01-18T22-51-28Z 镜像，支持文件缓存集成测试。
  MINIO,

  /// RabbitMQ 消息容器。
  ///
  /// 使用 rabbitmq:4.4.0-management 镜像，与 compose 一致，支持事件外送与消费的集成测试。
  RABBITMQ
}
