package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.Cursor;

import java.time.Instant;
import java.util.Optional;

/**
 * 游标仓储端口。
 * <p>负责游标的查询与持久化，用于记录采集/清洗流程的推进水位，防止重复处理与窗口错位。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorRepository {

    /**
     * 根据游标的业务唯一键查询当前状态。
     *
     * @param provenanceCode 来源编码
     * @param operationCode  操作编码，可为空表示不过滤
     * @param cursorKey      游标键，标识某类游标（如数据域、资源类型）
     * @param namespaceScope 命名空间范围代码
     * @param namespaceKey   命名空间业务键
     * @return 匹配的游标实体，不存在返回 empty
     */
    Optional<Cursor> find(String provenanceCode,
                          String operationCode,
                          String cursorKey,
                          String namespaceScope,
                          String namespaceKey);

    /**
     * 保存或更新游标状态。
     *
     * @param cursor 游标实体，包含当前水位、命名空间与版本信息
     * @return 持久化后的游标实体
     */
    Cursor save(Cursor cursor);

    /**
     * 查询 GLOBAL 命名空间、时间类型游标的最新标准化时间水位。
     *
     * @param provenanceCode 来源编码
     * @param operationCode  操作编码，可为空表示不过滤
     * @return 最新的 GLOBAL 时间水位，不存在则返回 empty
     */
    Optional<Instant> findLatestGlobalTimeWatermark(String provenanceCode, String operationCode);
}
