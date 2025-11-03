package com.patra.ingest.app.outbox.constants;

/**
 * Outbox 业务语义标签枚举(op_type 字段值)。
 *
 * <p>定义存储在 {@code ing_outbox_message.op_type} 字段中的业务事件语义。
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><b>面向业务</b>: 标签描述特定的业务事件,而不是通用的 CRUD 操作
 *   <li><b>领域特定</b>: 每个标签在其领域上下文中具有明确的业务含义
 *   <li><b>事件语义</b>: 标签从业务角度表示"发生了什么"
 * </ul>
 *
 * <h3>SQL 字段参考</h3>
 *
 * <pre>
 * `op_type` VARCHAR(32) NOT NULL COMMENT '业务语义标签,例如 TASK_READY / EVENT_PUBLISHED'
 * </pre>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 在 TaskOutboxPublisher 中
 * @Override
 * protected String getOperationType(TaskQueuedEvent event) {
 *     return OutboxBusinessTags.TASK_READY.getCode();
 * }
 * }</pre>
 *
 * <h3>标签命名约定</h3>
 *
 * <p>格式: {@code <DOMAIN>_<EVENT_SEMANTIC>}
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum OutboxBusinessTags {

  // ==================== Task 领域 ====================

  /** 任务准备执行。 */
  TASK_READY("TASK_READY", "任务就绪 - 调度器已创建任务并排队等待执行"),

  // ==================== Literature 领域 ====================

  /** 文献数据准备目录采集。 */
  LITERATURE_DATA_READY("LITERATURE_DATA_READY", "文献数据就绪 - 聚合对象存储负载可用"),

  // ==================== 技术操作 ====================

  /** 存储元数据重试失败的记录操作。 */
  STORAGE_METADATA_RETRY("STORAGE_METADATA_RETRY", "存储元数据重试 - 失败的元数据记录请求等待重试");

  private final String code;
  private final String description;

  OutboxBusinessTags(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 返回业务标签代码。
   *
   * <p>此值存储在 {@code ing_outbox_message.op_type} 字段中。
   *
   * @return 业务标签代码(例如 "TASK_READY", "PLAN_CREATED")
   */
  public String getCode() {
    return code;
  }

  /**
   * 返回人类可读的描述。
   *
   * @return 此业务标签的描述
   */
  public String getDescription() {
    return description;
  }

  /**
   * 根据代码查找枚举。
   *
   * @param code 业务标签代码
   * @return 匹配的枚举值
   * @throws IllegalArgumentException 如果未找到代码
   */
  public static OutboxBusinessTags fromCode(String code) {
    for (OutboxBusinessTags tag : values()) {
      if (tag.code.equals(code)) {
        return tag;
      }
    }
    throw new IllegalArgumentException("未知的业务标签代码: " + code);
  }
}
