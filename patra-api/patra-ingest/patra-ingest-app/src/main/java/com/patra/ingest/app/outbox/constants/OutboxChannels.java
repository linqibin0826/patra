package com.patra.ingest.app.outbox.constants;

/**
 * Outbox Channel 枚举。
 *
 * <p>定义 Outbox 框架中使用的消息通道,用于:
 *
 * <ul>
 *   <li>消息路由和主题映射
 *   <li>去重键作用域(channel + dedupKey 唯一性)
 *   <li>基于通道的过滤和监控
 * </ul>
 *
 * <h3>命名约定</h3>
 *
 * <p>通道遵循层次化下划线结构: {@code MODULE_SEMANTIC_STATE}
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @Override
 * protected String getChannel() {
 *     return OutboxChannels.INGEST_TASK_READY.getCode();
 * }
 * }</pre>
 *
 * <h3>设计考虑</h3>
 *
 * <ul>
 *   <li>通道名称映射到 MQ 主题/交换器以进行路由
 *   <li>通道提供去重作用域(唯一约束: channel + dedupKey)
 *   <li>基于通道的指标支持细粒度监控
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum OutboxChannels {

  /** 采集任务就绪通道。 */
  INGEST_TASK_READY("INGEST_TASK_READY", "采集任务就绪 - 调度器已创建任务并排队等待执行"),

  /** 文献数据就绪通道。 */
  LITERATURE_DATA_READY("LITERATURE_DATA_READY", "文献数据就绪 - 采集批次已提交到对象存储"),

  /** 存储元数据内部重试通道。 */
  STORAGE_METADATA_INTERNAL("storage.metadata.internal", "存储元数据内部 - 失败元数据操作的技术重试通道");

  private final String code;
  private final String description;

  OutboxChannels(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 返回通道代码。
   *
   * <p>此值存储在 {@code ing_outbox_message.channel} 字段中。
   *
   * @return 通道代码(例如 "INGEST_TASK_READY")
   */
  public String getCode() {
    return code;
  }

  /**
   * 返回人类可读的描述。
   *
   * @return 此通道的描述
   */
  public String getDescription() {
    return description;
  }

  /**
   * 根据代码查找枚举。
   *
   * @param code 通道代码
   * @return 匹配的枚举值
   * @throws IllegalArgumentException 如果未找到代码
   */
  public static OutboxChannels fromCode(String code) {
    for (OutboxChannels channel : values()) {
      if (channel.code.equals(code)) {
        return channel;
      }
    }
    throw new IllegalArgumentException("未知的通道代码: " + code);
  }
}
