package com.patra.ingest.adapter.scheduler.param;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 基于来源的定时任务的通用参数模型,与 XXL-Job 传递的 JSON 结构对齐。
 *
 * <p>所有字段都是可选的,空值将回退到业务层的默认值。
 *
 * @param windowFrom 时间窗口起始边界,采用 ISO-8601 Instant 格式
 * @param windowTo 时间窗口结束边界,采用 ISO-8601 Instant 格式
 * @param priority 调度优先级,不区分大小写的枚举名称
 * @param step 切片步长,采用 ISO-8601 Duration 字符串
 * @param schedulerLogId 调度器日志 ID,默认为 0
 * @param triggeredAt 触发时间戳,采用 ISO-8601 Instant 格式
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvenanceScheduleJobParam(
    String windowFrom,
    String windowTo,
    String priority,
    String step,
    String schedulerLogId,
    String triggeredAt) {

  /**
   * 创建一个空参数实例以进行统一的回退处理。
   *
   * @return 空参数实例
   */
  public static ProvenanceScheduleJobParam empty() {
    return new ProvenanceScheduleJobParam(null, null, null, null, null, null);
  }
}
