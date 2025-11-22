package com.patra.ingest.adapter.scheduler.param;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/// 数据来源调度任务参数记录。
/// 
/// 定义基于 Provenance(数据来源)的定时采集任务的通用参数模型,与 XXL-Job 传递的 JSON 结构对齐。所有字段均为可选,未提供的参数将回退到应用层的默认值。
/// 
/// 参数说明:
/// 
/// @param windowFrom 采集时间窗口起始边界,采用 ISO-8601 Instant 格式(如 "2025-01-01T00:00:00Z")
/// @param windowTo 采集时间窗口结束边界,采用 ISO-8601 Instant 格式
/// @param priority 任务调度优先级,不区分大小写的枚举名称(HIGH/NORMAL/LOW)
/// @param step 时间切片步长,采用 ISO-8601 Duration 格式(如 "P1D" 表示1天)
/// @param schedulerLogId 调度器日志 ID,用于关联调度记录,默认为 0
/// @param triggeredAt 任务触发时间戳,采用 ISO-8601 Instant 格式,默认为当前时间
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvenanceScheduleJobParam(
    String windowFrom,
    String windowTo,
    String priority,
    String step,
    String schedulerLogId,
    String triggeredAt) {

  /// 创建一个空参数实例以进行统一的回退处理。
/// 
/// @return 空参数实例
  public static ProvenanceScheduleJobParam empty() {
    return new ProvenanceScheduleJobParam(null, null, null, null, null, null);
  }
}
