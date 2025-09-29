package com.patra.ingest.adapter.inbound.scheduler.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed HARVEST 调度作业：绑定 PUBMED 来源 + HARVEST 操作，通过父类统一完成参数解析与计划编排。
 * <p>该类自身不处理业务细节，职责是声明来源/操作并提供 XXL 执行入口。</p>
 */
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

    /** 固定 PUBMED 来源。 */
    @Override
    protected ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.PUBMED;
    }

    /** 固定 HARVEST 操作。 */
    @Override
    protected OperationCode getOperationCode() {
        return OperationCode.HARVEST;
    }

    /**
     * XXL-Job 执行入口：获取调度参数并委派给模板方法 {@link #executeScheduleJob(String)}。
     */
    @XxlJob("pubmedHarvest")
    public void run() {
        // 透传 XXL 参数到统一的调度执行逻辑
        executeScheduleJob(XxlJobHelper.getJobParam());
    }
}
