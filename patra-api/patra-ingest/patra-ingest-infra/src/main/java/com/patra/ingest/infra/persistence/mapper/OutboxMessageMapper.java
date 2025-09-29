package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageMapper extends BaseMapper<OutboxMessageDO> {

    OutboxMessageDO findByChannelAndDedup(@Param("channel") String channel,
                                          @Param("dedupKey") String dedupKey);

    List<OutboxMessageDO> fetchPending(@Param("channel") String channel,
                                       @Param("available") Instant available,
                                       @Param("limit") int limit);

    int acquireLease(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("leaseOwner") String leaseOwner,
                     @Param("leaseExpireAt") Instant leaseExpireAt);

    int markPublished(@Param("id") Long id,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("msgId") String msgId);

    int markDeferred(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("retryCount") int retryCount,
                     @Param("nextRetryAt") Instant nextRetryAt,
                     @Param("errorCode") String errorCode,
                     @Param("errorMsg") String errorMsg);

    int markFailed(@Param("id") Long id,
                   @Param("expectedVersion") Long expectedVersion,
                   @Param("retryCount") int retryCount,
                   @Param("errorCode") String errorCode,
                   @Param("errorMsg") String errorMsg);
}
