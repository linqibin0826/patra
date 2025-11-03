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
 * <p>职责:
 *
 * <ul>
 *   <li>通过强类型枚举定义所有出站通道
 *   <li>提供查找辅助方法(如 {@link #fromChannel(String)})
 *   <li>关联每个通道与其有效载荷类型以供验证
 * </ul>
 *
 * <p><b>使用方式</b>:
 *
 * <ul>
 *   <li><b>内部发布者</b>:调用 {@code IngestPublishingChannels.TASK_READY.channel()}
 *   <li><b>外部消费者</b>:引用 API 契约 {@code IngestPublishedChannels.TASK_READY}
 * </ul>
 *
 * <p>通道命名规范: {@code DOMAIN_RESOURCE_EVENT} (如 {@code INGEST_TASK_READY})
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum IngestPublishingChannels implements ChannelKey {

  /** 任务调度就绪事件。 */
  TASK_READY("INGEST", "TASK", "READY", TaskReadyMessage.class),

  /** 文献数据就绪事件,目标为 Catalog 摄取。 */
  LITERATURE_DATA_READY("INGEST", "LITERATURE", "DATA_READY", LiteratureReadyMessage.class);

  private final String domain;
  private final String resource;
  private final String event;
  private final Class<?> payloadType;

  IngestPublishingChannels(String domain, String resource, String event, Class<?> payloadType) {
    this.domain = domain;
    this.resource = resource;
    this.event = event;
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

  @Override
  public String event() {
    return event;
  }

  /**
   * 声明的有效载荷类型,用于编译时或运行时验证。
   *
   * @return 有效载荷类型
   */
  public Class<?> payloadType() {
    return payloadType;
  }

  /**
   * 将规范化的通道字符串(如 {@code INGEST_TASK_READY})解析为枚举值。
   *
   * @param channel 大写蛇形命名风格的通道字符串
   * @return 匹配的枚举实例,如果无匹配则返回 {@link Optional#empty()}
   */
  public static Optional<IngestPublishingChannels> fromChannel(String channel) {
    if (channel == null || channel.isBlank()) return Optional.empty();
    String ch = channel.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(it -> it.channel().equals(ch)).findFirst();
  }
}
