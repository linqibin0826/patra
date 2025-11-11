package com.patra.ingest.domain.messaging;

/**
 * 业务消息通道常量定义。
 *
 * <p>定义采集服务的业务消息通道,与具体消息中间件技术无关。
 *
 * <p><strong>设计原则</strong>:
 *
 * <ul>
 *   <li>Domain 层使用纯业务语言,不包含技术实现细节
 *   <li>通道名称表达业务意图,与 MQ Topic 的映射由基础设施层负责
 *   <li>符合 DDD 和六边形架构原则,保持领域纯净性
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
public final class MessageChannels {

  private MessageChannels() {
    throw new UnsupportedOperationException("常量类不允许实例化");
  }

  /**
   * 任务就绪通道。
   *
   * <p>当采集任务被创建并准备执行时,通过此通道发送任务就绪消息。
   */
  public static final String INGEST_TASK_READY = "INGEST_TASK_READY";

  /**
   * 文献数据就绪通道。
   *
   * <p>当任务执行完成,文献数据准备好写入 Catalog 时,通过此通道发送数据就绪消息。
   */
  public static final String INGEST_LITERATURE_READY = "INGEST_LITERATURE_READY";
}
