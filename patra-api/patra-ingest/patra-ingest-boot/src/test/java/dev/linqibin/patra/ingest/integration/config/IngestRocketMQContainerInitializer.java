package dev.linqibin.patra.ingest.integration.config;

import dev.linqibin.starter.test.container.initializer.RocketMQContainerInitializer;

/// Ingest 服务专用 RocketMQ 容器初始化器。
///
/// 继承 starter-test 的 {@link RocketMQContainerInitializer}，指定 ingest 服务需要的 Topics。
///
/// ### 使用方式
///
/// ```java
/// @SpringBootTest
/// @ContextConfiguration(initializers = IngestRocketMQContainerInitializer.class)
/// class SomeMessageListenerIT {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see RocketMQContainerInitializer
public class IngestRocketMQContainerInitializer extends RocketMQContainerInitializer {

  /// 返回 ingest 服务需要的预创建 Topics。
  ///
  /// @return Topics 数组
  @Override
  protected String[] getTopicsToCreate() {
    return new String[] {"INGEST_TASK_READY", "INGEST_PUBLICATION_READY"};
  }
}
