package com.patra.ingest.adapter.scheduler.job;

import com.patra.ingest.domain.model.enums.OperationCode;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed 数据采集定时任务。
///
/// 绑定 PUBMED 数据来源与 HARVEST(采集)操作,实现 PubMed 医学出版物数据的定期采集调度。将参数解析和计划编排委托给抽象基类 {@link
/// AbstractProvenanceScheduleJob}。
///
/// 职责:
///
/// - 声明数据来源为 PUBMED(来自 ProvenanceCode 枚举)
///   - 声明操作类型为 HARVEST(原始数据采集)
///   - 暴露 XXL-Job 执行入口点(@XxlJob 注解)
///   - 委托参数解析和业务编排给基类模板方法
///
/// 调度策略: 由 XXL-Job 调度中心配置(通常按日/周定期执行),支持手动触发和失败重试。
///
/// 设计模式: 模板方法模式 - 子类只需定义数据来源和操作类型,具体执行流程由基类统一处理。
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  /// 固定的 PUBMED 来源。
  @Override
  protected ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  /// 固定的 HARVEST 操作。
  @Override
  protected OperationCode getOperationCode() {
    return OperationCode.HARVEST;
  }

  /// XXL-Job 入口点: 获取调度器参数并委托给 {@link #executeScheduleJob(String)}。
  @XxlJob("pubmedHarvest")
  public void run() {
    String jobParam = XxlJobHelper.getJobParam();
    log.debug("PubMed harvest 任务已触发,jobId [{}],参数: {}", XxlJobHelper.getJobId(), jobParam);

    // 将 XXL 参数传递给通用调度逻辑
    executeScheduleJob(jobParam);
  }
}
