package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.CursorEvent;

/**
 * 游标推进事件仓储端口。
 * <p>用于以追加写的方式记录游标演进轨迹，支撑事后审计、状态回放与监控分析。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorEventRepository {

    /**
     * 持久化一条游标推进事件。
     *
     * @param event 游标事件实体，需包含游标标识、时间窗口、血缘等信息
     * @return 保存后的事件实体，通常携带数据库生成的主键
     */
    CursorEvent save(CursorEvent event);
}
