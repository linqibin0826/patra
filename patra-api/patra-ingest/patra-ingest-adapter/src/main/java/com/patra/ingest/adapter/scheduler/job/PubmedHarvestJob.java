package com.patra.ingest.adapter.scheduler.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed HARVEST 定时任务。将 PUBMED 来源与 HARVEST 操作绑定,并将参数解析和计划编排委托给基类。
 *
 * <p>此类不处理业务细节;它声明来源/操作并暴露 XXL 执行入口点。
 */
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  /** 固定的 PUBMED 来源。 */
  @Override
  protected ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  /** 固定的 HARVEST 操作。 */
  @Override
  protected OperationCode getOperationCode() {
    return OperationCode.HARVEST;
  }

  /** XXL-Job 入口点: 获取调度器参数并委托给 {@link #executeScheduleJob(String)}。 */
  @XxlJob("pubmedHarvest")
  public void run() {
    String jobParam = XxlJobHelper.getJobParam();
    log.debug("PubMed harvest 任务已触发,jobId [{}],参数: {}", XxlJobHelper.getJobId(), jobParam);

    // 将 XXL 参数传递给通用调度逻辑
    executeScheduleJob(jobParam);
  }
}
