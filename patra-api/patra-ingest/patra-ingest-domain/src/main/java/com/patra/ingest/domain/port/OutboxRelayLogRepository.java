package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 转发日志仓储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>:
 *
 * <ul>
 *   <li>持久化转发执行日志(单条或批量)
 *   <li>按消息、批次或时间范围查询日志
 *   <li>支持监控和故障排查查询
 * </ul>
 *
 * <p><b>设计原则</b>:
 *
 * <ul>
 *   <li>端口定义在 Domain 层(无基础设施依赖)
 *   <li>实现在 Infrastructure 层(MyBatis-Plus 仓储)
 *   <li>领域中心签名(使用领域实体,而非 DO)
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
 *
 * @author Papertrace Team
 * @since 2.0
 */
public interface OutboxRelayLogRepository {

  /**
   * 持久化单条转发日志。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>单次转发执行结果
   *   <li>批量作业外的临时转发尝试
   * </ul>
   *
   * @param log 待持久化的转发日志
   */
  void save(OutboxRelayLog log);

  /**
   * 批量持久化多条转发日志(单次批量操作)。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>批量转发作业结果(典型场景:100-500 条日志)
   *   <li>性能优化:单次 INSERT 语句
   * </ul>
   *
   * <p><b>实现要求</b>: 必须使用批量 INSERT(不能是 N 次单独 INSERT)。
   *
   * @param logs 待持久化的转发日志列表
   */
  void saveBatch(List<OutboxRelayLog> logs);

  /**
   * 查询指定 Outbox 消息的所有转发日志(按时间降序)。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>故障排查:"这条消息为什么失败?"
   *   <li>审计跟踪:"这条消息重试了多少次?"
   * </ul>
   *
   * @param messageId Outbox 消息 ID
   * @return 转发日志列表(最新的在前)
   */
  List<OutboxRelayLog> findByOutboxMessageId(Long messageId);

  /**
   * 查询指定批次的所有转发日志(按时间升序)。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>批次级统计:"批次 X 的执行情况如何?"
   *   <li>性能分析:"这个批次的成功率是多少?"
   * </ul>
   *
   * @param batchId 转发批次标识符(格式: yyyyMMddHHmmss-xxxxxxxx)
   * @return 转发日志列表(最旧的在前)
   */
  List<OutboxRelayLog> findByBatchId(String batchId);

  /**
   * 统计匹配 channel、状态和时间范围的转发日志数量。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>监控面板:"INGEST 频道最近 1 小时的成功率"
   *   <li>告警查询:"最近 10 分钟有多少失败?"
   * </ul>
   *
   * @param channel 频道名称(例如 INGEST),null = 所有频道
   * @param status 转发状态(例如 PUBLISHED),null = 所有状态
   * @param startTime 时间范围起始(包含)
   * @param endTime 时间范围结束(不包含)
   * @return 匹配的转发日志数量
   */
  long countByChannelAndStatus(String channel, String status, Instant startTime, Instant endTime);

  /**
   * 查询最近的失败转发日志(用于告警)。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>告警通知:"展示最近 10 条失败记录"
   *   <li>值班分诊:"现在什么在失败?"
   * </ul>
   *
   * @param channel 频道名称过滤条件(null = 所有频道)
   * @param limit 返回的最大日志数量
   * @return 失败的转发日志列表(最新的在前)
   */
  List<OutboxRelayLog> findRecentFailed(String channel, int limit);

  /**
   * 查询指定频道在时间范围内的转发日志(用于分析)。
   *
   * <p><b>使用场景</b>:
   *
   * <ul>
   *   <li>性能分析:"展示今天所有转发尝试"
   *   <li>历史回顾:"昨天的转发表现如何?"
   * </ul>
   *
   * @param channel 频道名称过滤条件(null = 所有频道)
   * @param startTime 时间范围起始(包含)
   * @param endTime 时间范围结束(不包含)
   * @param limit 返回的最大日志数量
   * @return 转发日志列表(按 startedAt 降序)
   */
  List<OutboxRelayLog> findByChannelAndTimeRange(
      String channel, Instant startTime, Instant endTime, int limit);
}
