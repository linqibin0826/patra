package com.patra.ingest.domain.messaging;

import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.domain.model.vo.execution.TaskReadyMessage;
import com.patra.ingest.domain.model.vo.relay.LiteratureReadyMessage;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 采集领域发布的消息通道目录。
 *
 * <p><b>设计理念</b>：
 *
 * <ul>
 *   <li><b>资源级别路由</b>：每个枚举值代表一个业务资源（如 TASK、LITERATURE）
 *   <li><b>粗粒度 Topic</b>：一个资源对应一个 RocketMQ Topic
 *   <li><b>操作类型分离</b>：具体的操作类型（READY、FAILED 等）由 {@code OperationType} 定义
 * </ul>
 *
 * <p><b>职责</b>:
 *
 * <ul>
 *   <li>通过强类型枚举定义所有出站通道（资源级别）
 *   <li>提供查找辅助方法（如 {@link #fromChannel(String)}）
 *   <li>关联每个通道与其有效载荷类型以供验证
 * </ul>
 *
 * <p><b>使用方式</b>:
 *
 * <ul>
 *   <li><b>内部发布者</b>：调用 {@code IngestPublishingChannels.TASK.channel()} 获取 "INGEST_TASK"
 *   <li><b>外部消费者</b>：引用 API 契约 {@code IngestPublishedChannels.TASK}
 *   <li><b>操作类型</b>：组合使用 {@code TaskOperations.READY} 等枚举
 * </ul>
 *
 * <p><b>通道命名规范</b>：{@code DOMAIN_RESOURCE}（如 {@code INGEST_TASK}、{@code INGEST_LITERATURE}）
 *
 * <p><b>示例</b>：
 *
 * <pre>{@code
 * // 发布消息
 * String channel = IngestPublishingChannels.TASK.channel();  // "INGEST_TASK"
 * String opType = TaskOperations.READY.getCode();            // "READY"
 * // RocketMQ Destination = "INGEST_TASK:READY"
 *
 * // 查询通道
 * Optional<IngestPublishingChannels> ch = IngestPublishingChannels.fromChannel("INGEST_TASK");
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public enum IngestPublishingChannels implements ChannelKey {

  /** 任务相关消息通道（支持 READY、FAILED、COMPLETED 等操作）。 */
  TASK("INGEST", "TASK", TaskReadyMessage.class),

  /** 文献相关消息通道（支持 DATA_READY、VALIDATED、INDEXED 等操作）。 */
  LITERATURE("INGEST", "LITERATURE", LiteratureReadyMessage.class);

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

  /**
   * 声明的有效载荷类型，用于编译时或运行时验证。
   *
   * <p>注意：不同的操作类型可能使用相同的 payload 类型，因为它们针对同一资源。
   *
   * @return 有效载荷类型
   */
  public Class<?> payloadType() {
    return payloadType;
  }

  /**
   * 将规范化的通道字符串（如 {@code INGEST_TASK}）解析为枚举值。
   *
   * @param channel 大写蛇形命名风格的通道字符串
   * @return 匹配的枚举实例，如果无匹配则返回 {@link Optional#empty()}
   */
  public static Optional<IngestPublishingChannels> fromChannel(String channel) {
    if (channel == null || channel.isBlank()) return Optional.empty();
    String ch = channel.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(it -> it.channel().equals(ch)).findFirst();
  }
}
