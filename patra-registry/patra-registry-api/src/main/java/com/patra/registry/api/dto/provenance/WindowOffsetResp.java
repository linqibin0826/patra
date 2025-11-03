package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * 增量采集的时间窗口和偏移配置。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - 配置行的主标识符
 *   <li>provenanceId - 拥有该配置的数据源
 *   <li>operationType - 操作类型鉴别器(如 HARVEST/UPDATE)
 *   <li>effectiveFrom - 配置生效的时间戳
 *   <li>effectiveTo - 配置保持有效的截止时间戳
 *   <li>windowModeCode - 窗口生成模式(FIXED/SLIDING/NONE)
 *   <li>windowSizeValue - 窗口大小的数值部分
 *   <li>windowSizeUnitCode - 窗口大小的时间单位
 *   <li>calendarAlignTo - 窗口边界的日历对齐锚点
 *   <li>lookbackValue - 启动或重放时应用的历史回溯量
 *   <li>lookbackUnitCode - 回溯值的时间单位
 *   <li>overlapValue - 顺序窗口之间应用的重叠量
 *   <li>overlapUnitCode - 重叠值的时间单位
 *   <li>watermarkLagSeconds - 计算水位线时允许的延迟(秒)
 *   <li>offsetTypeCode - 偏移求值策略鉴别器
 *   <li>offsetFieldKey - 用作增量指针的统一标准键
 *   <li>offsetDateFormat - 用于偏移解析的可选日期格式
 *   <li>windowDateFieldKey - 用于时间切片回退的统一标准键
 *   <li>maxIdsPerWindow - 每个窗口处理的 ID 安全上限
 *   <li>maxWindowSpanSeconds - 允许的最大窗口跨度(秒)
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record WindowOffsetResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String windowModeCode,
    Integer windowSizeValue,
    String windowSizeUnitCode,
    String calendarAlignTo,
    Integer lookbackValue,
    String lookbackUnitCode,
    Integer overlapValue,
    String overlapUnitCode,
    Integer watermarkLagSeconds,
    String offsetTypeCode,
    String offsetFieldKey,
    String offsetDateFormat,
    String windowDateFieldKey,
    Integer maxIdsPerWindow,
    Integer maxWindowSpanSeconds) {}
