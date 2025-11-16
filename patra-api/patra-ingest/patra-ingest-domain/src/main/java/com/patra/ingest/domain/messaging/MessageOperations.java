package com.patra.ingest.domain.messaging;

/**
 * 业务消息操作类型常量定义。
 *
 * <p>定义采集服务中不同业务操作的标识,用于消息分类和过滤。
 *
 * <p><strong>设计原则</strong>:
 *
 * <ul>
 *   <li>操作类型反映业务动作,与技术实现无关
 *   <li>用于消息的业务分类,便于消费端按需过滤
 *   <li>与具体 MQ 的 Tags 映射由基础设施层负责
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
public final class MessageOperations {

  private MessageOperations() {
    throw new UnsupportedOperationException("常量类不允许实例化");
  }

  /** 任务就绪操作 - 表示任务已创建并等待执行。 */
  public static final String TASK_READY = "INGEST_TASK_READY";

  /** 任务完成操作 - 表示任务已成功执行完毕。 */
  public static final String TASK_COMPLETED = "TASK_COMPLETED";

  /** 出版物就绪操作 - 表示出版物数据已准备就绪。 */
  public static final String PUBLICATION_READY = "INGEST_PUBLICATION_READY";
}
