package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageMapper extends BaseMapper<OutboxMessageDO> {

    List<OutboxMessageDO> lockPending(@Param("channel") String channel,
                                      @Param("status") String status,
                                      @Param("available") Instant available,
                                      @Param("limit") int limit);

    int markPublishing(@Param("id") Long id,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("expectedVersion") Long expectedVersion,
                       @Param("leaseOwner") String leaseOwner,
                       @Param("leaseExpireAt") Instant leaseExpireAt);

    int markPublished(@Param("id") Long id,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("msgId") String msgId);

    int markRetry(@Param("id") Long id,
                  @Param("expectedVersion") Long expectedVersion,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryAt") Instant nextRetryAt,
                  @Param("errorCode") String errorCode,
                  @Param("errorMsg") String errorMsg);

    int markDead(@Param("id") Long id,
                 @Param("expectedVersion") Long expectedVersion,
                 @Param("retryCount") int retryCount,
                 @Param("errorCode") String errorCode,
                 @Param("errorMsg") String errorMsg);
}
