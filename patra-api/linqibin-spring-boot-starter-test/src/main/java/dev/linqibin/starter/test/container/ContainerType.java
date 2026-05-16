package dev.linqibin.starter.test.container;

/// 测试容器类型枚举。
///
/// 定义 starter-test 支持的容器类型，用于容器注册和查找。
///
/// ### 支持的容器类型
///
/// - **MYSQL**: MySQL 数据库容器
/// - **ROCKETMQ**: RocketMQ 消息队列容器
/// - **REDIS**: Redis 缓存容器
///
/// ### 使用示例
///
/// ```java
/// // 获取已注册的 MySQL 容器
/// MySQLContainer<?> mysql = ContainerRegistry.get(ContainerType.MYSQL);
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

  /// MySQL 数据库容器。
  ///
  /// 使用 mysql:8.0.36 镜像，支持配置化数据库名。
  MYSQL,

  /// RocketMQ 消息队列容器。
  ///
  /// 使用 apache/rocketmq:5.3.1 镜像，包含 NameServer 和 Broker。
  ROCKETMQ,

  /// Redis 缓存容器。
  ///
  /// 使用 redis:7 镜像，支持 Redisson 集成测试。
  REDIS,

  /// MinIO 对象存储容器。
  ///
  /// 使用 minio/minio:RELEASE.2024-01-18T22-51-28Z 镜像，支持文件缓存集成测试。
  MINIO
}
