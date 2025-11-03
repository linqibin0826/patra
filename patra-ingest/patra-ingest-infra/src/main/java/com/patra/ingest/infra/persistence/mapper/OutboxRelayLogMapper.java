package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 发件箱中继日志 Mapper 接口 — 对发件箱中继日志表的数据访问操作。
 *
 * <p>提供中继执行审计跟踪、故障排查和监控的查询操作。
 *
 * <p>设计原则:
 *
 * <ul>
 *   <li><strong>仅追加</strong>: 无更新方法(日志创建后不可变)
 *   <li><strong>批量优化</strong>: 批量插入方法用于高吞吐量中继作业
 *   <li><strong>查询优化</strong>: 方法与常见故障排查模式对齐
 * </ul>
 *
 * <p>索引假设(用于 SQL 优化):
 *
 * <ul>
 *   <li>{@code idx_message_id(message_id, started_at DESC)}: 按消息查询日志
 *   <li>{@code idx_batch_id(relay_batch_id)}: 按批次查询日志
 *   <li>{@code idx_channel_time(channel, started_at)}: 按通道和时间范围查询日志
 *   <li>{@code idx_status(relay_status, started_at)}: 按状态查询日志(用于告警)
 *   <li>{@code idx_created_at(created_at)}: 按创建时间归档旧日志
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
public interface OutboxRelayLogMapper extends BaseMapper<OutboxRelayLogDO> {

  /**
   * 批量插入中继日志(单个 SQL 语句)。
   *
   * <p>性能说明: 使用 JDBC 批量 INSERT(单语句,多行)。
   *
   * <p>使用场景: 中继作业执行 100-500 条消息,一次性批量插入所有日志。
   *
   * @param logs 要插入的中继日志列表(建议批次大小 ≤ 500)
   * @return 插入的行数
   */
  int insertBatch(@Param("logs") List<OutboxRelayLogDO> logs);

  /**
   * 查询特定发件箱消息的所有中继日志(按 started_at 降序排列)。
   *
   * <p>使用场景: 故障排查 - "显示消息 X 的所有中继尝试"
   *
   * <p>使用索引: {@code idx_message_id(message_id, started_at DESC)}
   *
   * @param messageId 发件箱消息 ID
   * @return 中继日志列表(最新的在前)
   */
  List<OutboxRelayLogDO> findByMessageId(@Param("messageId") Long messageId);

  /**
   * 查询特定批次的所有中继日志(按 started_at 升序排列)。
   *
   * <p>使用场景: 批次级统计 - "批次 X 的表现如何?"
   *
   * <p>使用索引: {@code idx_batch_id(relay_batch_id)}
   *
   * @param batchId 中继批次标识符(格式: yyyyMMddHHmmss-xxxxxxxx)
   * @return 中继日志列表(最旧的在前)
   */
  List<OutboxRelayLogDO> findByBatchId(@Param("batchId") String batchId);

  /**
   * 统计匹配 channel、status 和时间范围的中继日志数量。
   *
   * <p>使用场景:
   *
   * <ul>
   *   <li>监控仪表盘: "INGEST 通道在最近 1 小时内的成功率"
   *   <li>告警查询: "最近 10 分钟内有多少 FAILED 中继?"
   * </ul>
   *
   * <p>使用索引: {@code idx_channel_time(channel, started_at)} 或 {@code idx_status(relay_status,
   * started_at)}
   *
   * @param channel 通道名称过滤器(NULL = 所有通道)
   * @param status 中继状态过滤器(NULL = 所有状态)
   * @param startTime 时间范围起始(含)
   * @param endTime 时间范围结束(不含)
   * @return 匹配的中继日志数量
   */
  long countByChannelAndStatus(
      @Param("channel") String channel,
      @Param("status") String status,
      @Param("startTime") Instant startTime,
      @Param("endTime") Instant endTime);

  /**
   * 查询最近失败的中继日志(用于告警)。
   *
   * <p>使用场景: 值班分流 - "显示最近 10 个失败"
   *
   * <p>使用索引: {@code idx_status(relay_status='FAILED', started_at DESC)}
   *
   * @param channel 通道名称过滤器(NULL = 所有通道)
   * @param limit 返回的最大日志数
   * @return 失败的中继日志列表(最新的在前)
   */
  List<OutboxRelayLogDO> findRecentFailed(
      @Param("channel") String channel, @Param("limit") int limit);

  /**
   * 查询通道在时间范围内的中继日志(用于分析)。
   *
   * <p>使用场景: 历史回顾 - "显示 INGEST 通道今天的所有中继尝试"
   *
   * <p>使用索引: {@code idx_channel_time(channel, started_at)}
   *
   * @param channel 通道名称过滤器(NULL = 所有通道)
   * @param startTime 时间范围起始(含)
   * @param endTime 时间范围结束(不含)
   * @param limit 返回的最大日志数
   * @return 中继日志列表(按 started_at 降序排列)
   */
  List<OutboxRelayLogDO> findByChannelAndTimeRange(
      @Param("channel") String channel,
      @Param("startTime") Instant startTime,
      @Param("endTime") Instant endTime,
      @Param("limit") int limit);
}
