package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.OutboxRelayLogEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 发件箱中继日志 JPA Repository。
///
/// **职责**：
///
/// - 提供 OutboxRelayLogEntity 的 CRUD 操作
/// - 支持中继执行审计跟踪、故障排查和监控的查询
///
/// **设计原则**：
///
/// - **仅追加**：无更新方法（日志创建后不可变）
/// - **批量优化**：批量插入方法用于高吞吐量中继作业
/// - **查询优化**：方法与常见故障排查模式对齐
///
/// @author linqibin
/// @since 0.1.0
public interface OutboxRelayLogDao extends JpaRepository<OutboxRelayLogEntity, Long> {

  /// 查询特定发件箱消息的所有中继日志（按 started_at 降序排列）。
  ///
  /// 使用场景：故障排查 - "显示消息 X 的所有中继尝试"
  ///
  /// @param messageId 发件箱消息 ID
  /// @return 中继日志列表（最新的在前）
  List<OutboxRelayLogEntity> findByMessageIdOrderByStartedAtDesc(Long messageId);

  /// 查询特定批次的所有中继日志（按 started_at 升序排列）。
  ///
  /// 使用场景：批次级统计 - "批次 X 的表现如何?"
  ///
  /// @param relayBatchId 中继批次标识符
  /// @return 中继日志列表（最旧的在前）
  List<OutboxRelayLogEntity> findByRelayBatchIdOrderByStartedAtAsc(String relayBatchId);

  /// 统计匹配 channel、status 和时间范围的中继日志数量。
  ///
  /// 使用场景：监控仪表盘、告警查询
  ///
  /// @param channel 通道名称过滤器（NULL = 所有通道）
  /// @param status 中继状态过滤器（NULL = 所有状态）
  /// @param startTime 时间范围起始（含）
  /// @param endTime 时间范围结束（不含）
  /// @return 匹配的中继日志数量
  @Query(
      """
      SELECT COUNT(l) FROM OutboxRelayLogEntity l
      WHERE (:channel IS NULL OR l.channel = :channel)
        AND (:status IS NULL OR l.relayStatus = :status)
        AND l.startedAt >= :startTime
        AND l.startedAt < :endTime
      """)
  long countByChannelAndStatus(
      @Param("channel") String channel,
      @Param("status") String status,
      @Param("startTime") Instant startTime,
      @Param("endTime") Instant endTime);

  /// 查询最近失败的中继日志（用于告警）。
  ///
  /// 使用场景：值班分流 - "显示最近 N 个失败"
  ///
  /// @param channel 通道名称过滤器（NULL = 所有通道）
  /// @param pageable 分页参数
  /// @return 失败的中继日志列表（最新的在前）
  @Query(
      """
      SELECT l FROM OutboxRelayLogEntity l
      WHERE l.relayStatus = 'FAILED'
        AND (:channel IS NULL OR l.channel = :channel)
      ORDER BY l.startedAt DESC
      """)
  List<OutboxRelayLogEntity> findRecentFailed(@Param("channel") String channel, Pageable pageable);

  /// 查询通道在时间范围内的中继日志（用于分析）。
  ///
  /// 使用场景：历史回顾 - "显示通道今天的所有中继尝试"
  ///
  /// @param channel 通道名称过滤器（NULL = 所有通道）
  /// @param startTime 时间范围起始（含）
  /// @param endTime 时间范围结束（不含）
  /// @param pageable 分页参数
  /// @return 中继日志列表（按 started_at 降序排列）
  @Query(
      """
      SELECT l FROM OutboxRelayLogEntity l
      WHERE (:channel IS NULL OR l.channel = :channel)
        AND l.startedAt >= :startTime
        AND l.startedAt < :endTime
      ORDER BY l.startedAt DESC
      """)
  List<OutboxRelayLogEntity> findByChannelAndTimeRange(
      @Param("channel") String channel,
      @Param("startTime") Instant startTime,
      @Param("endTime") Instant endTime,
      Pageable pageable);
}
