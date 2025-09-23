package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 时间窗口与增量指针配置（Window & Offset）响应 DTO。<br>
 * <p>对应表：reg_prov_window_offset_cfg。用于驱动增量抓取的时间切片生成策略、回溯补偿、重叠冗余与偏移字段格式解析。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 所属来源。</li>
 *   <li>{@code scopeCode} 配置作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 任务子键。</li>
 *   <li>{@code effectiveFrom} 生效起（含）。</li>
 *   <li>{@code effectiveTo} 生效止（不含）。</li>
 *   <li>{@code windowModeCode} 窗口模式：例如 FIXED / SLIDING / NONE。影响窗口的生成算法。</li>
 *   <li>{@code windowSizeValue} 窗口长度数值（与 {@code windowSizeUnitCode} 组合）。</li>
 *   <li>{@code windowSizeUnitCode} 窗口长度单位（SECOND/MINUTE/HOUR/DAY）。</li>
 *   <li>{@code calendarAlignTo} 日历对齐锚点（如 HOUR_START / DAY_START）使窗口边界贴齐自然时间块。</li>
 *   <li>{@code lookbackValue} 启动或补偿时向后回溯量（历史追补）。</li>
 *   <li>{@code lookbackUnitCode} 回溯量时间单位。</li>
 *   <li>{@code overlapValue} 窗口重叠值（处理边界 & 数据迟到）。</li>
 *   <li>{@code overlapUnitCode} 重叠单位。</li>
 *   <li>{@code watermarkLagSeconds} 允许事件迟到的最大秒数（用于校正 offset/watermark）。</li>
 *   <li>{@code offsetTypeCode} 偏移类型：如 TIMESTAMP_FIELD / ID_FIELD / EXTERNAL_STATE。</li>
 *   <li>{@code offsetFieldName} 响应/记录中表示增量指针的字段名。</li>
 *   <li>{@code offsetDateFormat} 当偏移为日期字符串时对应解析格式（例如 yyyy-MM-dd'T'HH:mm:ss'Z'）。</li>
 *   <li>{@code defaultDateFieldName} 记录缺少特定字段时使用的备用日期字段。</li>
 *   <li>{@code maxIdsPerWindow} 一个窗口内允许聚合的最大 ID 数（限制单批量任务压力）。</li>
 *   <li>{@code maxWindowSpanSeconds} 窗口跨度的安全上限（防止意外放大导致全量抓取）。</li>
 * </ul>
 * 调度器需结合上次成功 offset + 当前时间生成一组待处理窗口；重叠与 lookback 在回放或补偿阶段尤为重要。
 */
public record WindowOffsetResp(
        /* 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 作用域编码 */
        String scopeCode,
        /** 任务类型 */
        String taskType,
        /** 任务子键 */
        String taskTypeKey,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 窗口模式编码 */
        String windowModeCode,
        /** 窗口大小数值 */
        Integer windowSizeValue,
        /** 窗口大小单位编码 */
        String windowSizeUnitCode,
        /** 日历对齐锚点 */
        String calendarAlignTo,
        /** 回溯量数值 */
        Integer lookbackValue,
        /** 回溯量单位 */
        String lookbackUnitCode,
        /** 窗口重叠量数值 */
        Integer overlapValue,
        /** 重叠量单位 */
        String overlapUnitCode,
        /** Watermark 延迟秒数 */
        Integer watermarkLagSeconds,
        /** 偏移类型编码 */
        String offsetTypeCode,
        /** 偏移字段名 */
        String offsetFieldName,
        /** 偏移日期格式（如存在） */
        String offsetDateFormat,
        /** 备选日期字段名 */
        String defaultDateFieldName,
        /** 单窗口最大 ID 数 */
        Integer maxIdsPerWindow,
        /** 窗口最大时间跨度秒 */
        Integer maxWindowSpanSeconds
) {
}
