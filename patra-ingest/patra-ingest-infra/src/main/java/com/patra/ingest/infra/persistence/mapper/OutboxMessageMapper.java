package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 消息 Mapper 接口。
 * <p>包含针对 Outbox 状态推进、租约获取、重试/失败标记的精确条件更新方法。</p>
 * <p>并发控制：所有写操作依赖 <code>expectedVersion</code> 实现乐观锁；调用方需根据返回影响行数判定是否重试。</p>
 * <p>索引假设（供 SQL 优化参考）：
 * <ul>
 *   <li><code>uk_outbox_channel_dedup(channel, dedup_key)</code> 幂等唯一约束。</li>
 *   <li><code>idx_outbox_status_time(status_code, not_before, id)</code> 批量扫描待发布。</li>
 *   <li><code>idx_outbox_lease(status_code, pub_leased_until)</code> 租约过期过滤。</li>
 *   <li><code>idx_outbox_partition(channel, partition_key, status_code)</code> 分区/保序。</li>
 * </ul>
 * </p>
 */
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageDO> {

    /**
     * 幂等查询：按 (channel, dedupKey) 精确定位记录。
     * @param channel 通道
     * @param dedupKey 去重键
     * @return 匹配记录或 null
     */
    OutboxMessageDO findByChannelAndDedup(@Param("channel") String channel,
                                          @Param("dedupKey") String dedupKey);

    /**
     * 拉取可发布（PENDING 且到达可用时间 & 未被有效租约占用）的消息列表。
     * @param channel 通道
     * @param available 可用时间上限（通常为当前时间）
     * @param limit 限制条数
     * @return 消息集合（可能为空）
     */
    List<OutboxMessageDO> fetchPending(@Param("channel") String channel,
                                       @Param("available") Instant available,
                                       @Param("limit") int limit);

    /**
     * 通过乐观锁获取租约并自增版本。
     * 条件（示例）：id=? AND version=:expectedVersion AND (pub_lease_owner IS NULL OR pub_leased_until < NOW).
     * @param id 主键
     * @param expectedVersion 期望版本
     * @param leaseOwner 租约持有者（实例 ID）
     * @param leaseExpireAt 过期时间
     * @return 影响行数（1=成功,0=失败）
     */
    int acquireLease(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("leaseOwner") String leaseOwner,
                     @Param("leaseExpireAt") Instant leaseExpireAt);

    /**
     * 标记发布成功：更新状态、消息 ID、发布时间并自增版本。
     * @param id 主键
     * @param expectedVersion 期望版本
     * @param msgId Broker 消息 ID
     * @return 影响行数
     */
    int markPublished(@Param("id") Long id,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("msgId") String msgId);

    /**
     * 标记延迟重试：写入重试计数、下一次时间、错误信息并自增版本。
     * @param id 主键
     * @param expectedVersion 期望版本
     * @param retryCount 新的重试次数
     * @param nextRetryAt 下次重试时间
     * @param errorCode 错误码
     * @param errorMsg 错误消息
     * @return 影响行数
     */
    int markDeferred(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("retryCount") int retryCount,
                     @Param("nextRetryAt") Instant nextRetryAt,
                     @Param("errorCode") String errorCode,
                     @Param("errorMsg") String errorMsg);

    /**
     * 标记终态失败（FAILED/DEAD）：写入最终重试次数与错误信息并自增版本。
     * @param id 主键
     * @param expectedVersion 期望版本
     * @param retryCount 最终重试次数
     * @param errorCode 错误码
     * @param errorMsg 错误消息
     * @return 影响行数
     */
    int markFailed(@Param("id") Long id,
                   @Param("expectedVersion") Long expectedVersion,
                   @Param("retryCount") int retryCount,
                   @Param("errorCode") String errorCode,
                   @Param("errorMsg") String errorMsg);
}
