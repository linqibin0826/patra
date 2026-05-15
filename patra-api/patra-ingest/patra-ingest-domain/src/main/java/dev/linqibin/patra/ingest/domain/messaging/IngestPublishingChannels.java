package dev.linqibin.patra.ingest.domain.messaging;

import dev.linqibin.patra.ingest.domain.model.vo.execution.TaskReadyMessage;
import dev.linqibin.patra.ingest.domain.model.vo.relay.PublicationReadyMessage;
import dev.linqibin.commons.messaging.ChannelKey;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/// 采集领域发布的消息通道目录。
///
/// **设计理念**：
///
/// - **资源级别路由**：每个枚举值代表一个业务资源（如 TASK、PUBLICATION）
///   - **粗粒度 Topic**：一个资源对应一个 RocketMQ Topic
///   - **操作类型分离**：具体的操作类型（READY、FAILED 等）由 `OperationType` 定义
///
/// **职责**:
///
/// - 通过强类型枚举定义所有出站通道（资源级别）
///   - 提供查找辅助方法（如 {@link #fromChannel(String)}）
///   - 关联每个通道与其有效载荷类型以供验证
///
/// **使用方式**:
///
/// - **内部发布者**：调用 `IngestPublishingChannels.TASK.channel()` 获取 "INGEST_TASK"
///   - **外部消费者**：引用 API 契约 `IngestPublishedChannels.TASK`
///   - **操作类型**：组合使用 `TaskOperations.READY` 等枚举
///
/// **通道命名规范**：`DOMAIN_RESOURCE`（如 `INGEST_TASK`、`INGEST_PUBLICATION`）
///
/// **示例**：
///
/// ```java
/// // 发布消息
/// String channel = IngestPublishingChannels.TASK.channel();  // "INGEST_TASK"
/// String opType = TaskOperations.READY.getCode();            // "READY"
/// // RocketMQ Destination = "INGEST_TASK:READY"
///
/// // 查询通道
/// Optional<IngestPublishingChannels> ch = IngestPublishingChannels.fromChannel("INGEST_TASK");
/// ```
///
/// @author linqibin
/// @since 0.1.0
public enum IngestPublishingChannels implements ChannelKey {

  /// 任务相关消息通道（支持 READY、FAILED、COMPLETED 等操作）。
  TASK("INGEST", "TASK", TaskReadyMessage.class),

  /// 出版物相关消息通道（支持 DATA_READY、VALIDATED、INDEXED 等操作）。
  PUBLICATION("INGEST", "PUBLICATION", PublicationReadyMessage.class);

  private final String domain;
  private final String resource;
  private final Class<?> payloadType;

  IngestPublishingChannels(String domain, String resource, Class<?> payloadType) {
    this.domain = domain;
    this.resource = resource;
    this.payloadType = payloadType;
  }

  @Override
  public String domain() {
    return domain;
  }

  @Override
  public String resource() {
    return resource;
  }

  /// 声明的有效载荷类型，用于编译时或运行时验证。
  ///
  /// 注意：不同的操作类型可能使用相同的 payload 类型，因为它们针对同一资源。
  ///
  /// @return 有效载荷类型
  public Class<?> payloadType() {
    return payloadType;
  }

  /// 将规范化的通道字符串（如 `INGEST_TASK`）解析为枚举值。
  ///
  /// @param channel 大写蛇形命名风格的通道字符串
  /// @return 匹配的枚举实例，如果无匹配则返回 {@link Optional#empty()}
  public static Optional<IngestPublishingChannels> fromChannel(String channel) {
    if (channel == null || channel.isBlank()) return Optional.empty();
    String ch = channel.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(it -> it.channel().equals(ch)).findFirst();
  }
}
