package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.port.OutboxRelayLogRepository;
import com.patra.ingest.infra.persistence.converter.OutboxRelayLogConverter;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import com.patra.ingest.infra.persistence.mapper.OutboxRelayLogMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 发件箱中继日志持久化的 MyBatis-Plus 实现。
 *
 * <h3>职责</h3>
 *
 * <ul>
 *   <li>持久化中继执行日志(单条或批量)到数据库
 *   <li>按消息、批次、通道、状态或时间范围查询日志
 *   <li>支持故障排查、监控和分析用例
 * </ul>
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><strong>仅追加</strong>: 日志创建后永不更新(不可变审计轨迹)
 *   <li><strong>批量优化</strong>: 批量插入使用单条 SQL 语句以提高性能
 *   <li><strong>查询优化</strong>: 方法利用数据库索引实现高效检索
 *   <li><strong>无业务逻辑</strong>: 纯数据访问层,委托给领域层处理规则
 * </ul>
 *
 * <h3>性能考虑</h3>
 *
 * <ul>
 *   <li>批量插入: 使用 {@link #saveBatch(List)} 处理 100-500 条日志(单条 INSERT 语句)
 *   <li>索引覆盖: 所有查询方法使用适当索引(参见 Mapper JavaDoc)
 *   <li>分页: 查询方法接受 limit 参数以防止大结果集
 * </ul>
 *
 * <h3>日志策略</h3>
 *
 * <ul>
 *   <li>DEBUG: 批量插入操作(大小和受影响行数)
 *   <li>DEBUG: 查询操作(方法名和结果计数)
 *   <li>高频操作无 INFO 日志(避免日志噪音)
 * </ul>
 *
 * <h3>线程安全</h3>
 *
 * <p>无共享可变状态(Mapper/Converter 无状态或线程安全); 实例可跨线程重用。
 *
 * @author Patra Team
 * @since 2.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxRelayLogRepositoryMpImpl implements OutboxRelayLogRepository {

  private final OutboxRelayLogMapper mapper;
  private final OutboxRelayLogConverter converter;

  /**
   * 持久化单条中继日志到数据库。
   *
   * <p>使用场景: 单次中继执行(罕见,通常使用批量插入)。
   *
   * <p>性能注意: 对于多条日志,优先使用 {@link #saveBatch(List)} 以减少数据库往返。
   *
   * @param relayLog 要持久化的中继日志
   */
  @Override
  public void save(OutboxRelayLog relayLog) {
    if (relayLog == null) {
      throw new IllegalArgumentException("OutboxRelayLog must not be null");
    }

    OutboxRelayLogDO entity = converter.toEntity(relayLog);
    mapper.insert(entity);

    // No logging for single insert (use saveBatch for high-frequency operations)
  }

  /**
   * 在单条 SQL 语句中批量持久化多条中继日志。
   *
   * <p>使用场景: 中继作业完成 100-500 条消息,一次性插入所有日志。
   *
   * <p>性能: 单条 INSERT 语句包含多行(例如: INSERT INTO ... VALUES (row1), (row2), ...)。
   *
   * <p>推荐批次大小: 100-500 条日志(超过 500 可能触及 SQL 长度限制)。
   *
   * @param logs 要持久化的中继日志列表
   */
  @Override
  public void saveBatch(List<OutboxRelayLog> logs) {
    if (logs == null || logs.isEmpty()) {
      return;
    }

    List<OutboxRelayLogDO> entities = converter.toEntities(logs);
    int rows = mapper.insertBatch(entities);

    log.debug(
        "Batch saved relay logs: batchSize={}, affectedRows={}, batchIds={}",
        logs.size(),
        rows,
        logs.stream().map(OutboxRelayLog::getRelayBatchId).distinct().toList());
  }

  /**
   * 查询特定发件箱消息的所有中继日志(最新的优先)。
   *
   * <p>使用场景: 故障排查 - "显示消息 X 的所有中继尝试"。
   *
   * <p>使用的索引: <code>idx_message_id(message_id, started_at DESC)</code>。
   *
   * @param messageId 发件箱消息 ID
   * @return 中继日志列表(最新的优先)
   */
  @Override
  public List<OutboxRelayLog> findByOutboxMessageId(Long messageId) {
    if (messageId == null) {
      throw new IllegalArgumentException("messageId must not be null");
    }

    List<OutboxRelayLogDO> entities = mapper.findByMessageId(messageId);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug("Found {} relay logs for messageId={}", logs.size(), messageId);
    return logs;
  }

  /**
   * 查询特定批次的所有中继日志(最旧的优先)。
   *
   * <p>使用场景: 批次级统计 - "批次 X 的表现如何?"。
   *
   * <p>使用的索引: <code>idx_batch_id(relay_batch_id)</code>。
   *
   * @param batchId 中继批次标识符(格式: yyyyMMddHHmmss-xxxxxxxx)
   * @return 中继日志列表(最旧的优先)
   */
  @Override
  public List<OutboxRelayLog> findByBatchId(String batchId) {
    if (batchId == null || batchId.isBlank()) {
      throw new IllegalArgumentException("batchId must not be null or blank");
    }

    List<OutboxRelayLogDO> entities = mapper.findByBatchId(batchId);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug("Found {} relay logs for batchId={}", logs.size(), batchId);
    return logs;
  }

  /**
   * 统计匹配通道、状态和时间范围的中继日志数量。
   *
   * <p>使用场景:
   *
   * <ul>
   *   <li>监控仪表板: "最近 1 小时内 INGEST 通道的成功率"
   *   <li>告警查询: "最近 10 分钟内有多少 FAILED 中继?"
   * </ul>
   *
   * <p>使用的索引: <code>idx_channel_time</code> 或 <code>idx_status</code>。
   *
   * @param channel 通道名称过滤器(null = 所有通道)
   * @param status 中继状态过滤器(null = 所有状态)
   * @param startTime 时间范围起始(包含)
   * @param endTime 时间范围结束(不包含)
   * @return 匹配的中继日志数量
   */
  @Override
  public long countByChannelAndStatus(
      String channel, String status, Instant startTime, Instant endTime) {
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("startTime and endTime must not be null");
    }

    long count = mapper.countByChannelAndStatus(channel, status, startTime, endTime);

    log.debug(
        "Counted {} relay logs for channel={}, status={}, timeRange=[{}, {}]",
        count,
        channel,
        status,
        startTime,
        endTime);
    return count;
  }

  /**
   * 查询最近失败的中继日志(用于告警)。
   *
   * <p>使用场景: 值班分诊 - "显示最近 10 次失败"。
   *
   * <p>使用的索引: <code>idx_status(relay_status='FAILED', started_at DESC)</code>。
   *
   * @param channel 通道名称过滤器(null = 所有通道)
   * @param limit 返回的最大日志数
   * @return 失败的中继日志列表(最新的优先)
   */
  @Override
  public List<OutboxRelayLog> findRecentFailed(String channel, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }

    List<OutboxRelayLogDO> entities = mapper.findRecentFailed(channel, limit);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug("Found {} recent failed relay logs for channel={}", logs.size(), channel);
    return logs;
  }

  /**
   * 查询时间范围内某通道的中继日志。
   *
   * <p>使用场景: 历史审查 - "显示今天 INGEST 通道的所有中继尝试"。
   *
   * <p>使用的索引: <code>idx_channel_time(channel, started_at)</code>。
   *
   * @param channel 通道名称过滤器(null = 所有通道)
   * @param startTime 时间范围起始(包含)
   * @param endTime 时间范围结束(不包含)
   * @param limit 返回的最大日志数
   * @return 中继日志列表(最新的优先)
   */
  @Override
  public List<OutboxRelayLog> findByChannelAndTimeRange(
      String channel, Instant startTime, Instant endTime, int limit) {
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("startTime and endTime must not be null");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }

    List<OutboxRelayLogDO> entities =
        mapper.findByChannelAndTimeRange(channel, startTime, endTime, limit);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug(
        "Found {} relay logs for channel={}, timeRange=[{}, {}]",
        logs.size(),
        channel,
        startTime,
        endTime);
    return logs;
  }
}
