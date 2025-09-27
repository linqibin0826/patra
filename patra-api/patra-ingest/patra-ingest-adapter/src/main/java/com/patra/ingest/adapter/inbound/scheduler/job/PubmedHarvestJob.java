package com.patra.ingest.adapter.inbound.scheduler.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed HARVEST 任务入口，负责衔接 XXL-Job 与应用层。
 * <p>该任务固定来源为 PUBMED、操作类型为 HARVEST，通过父类统一解析参数并触发后续编排。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

    /**
     * 获取任务对应的数据来源编码。
     *
     * @return PUBMED 来源
     */
    @Override
    protected ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.PUBMED;
    }

    /**
     * 获取任务对应的操作类型。
     *
     * @return HARVEST 操作
     */
    @Override
    protected OperationCode getOperationCode() {
        return OperationCode.HARVEST;
    }

    /**
     * XXL-Job 执行入口，从调度器获取参数并交由父类处理。
     */
    @XxlJob("pubmedHarvest")
    public void run() {
        // 透传 XXL 参数到统一的调度执行逻辑
        executeScheduleJob(XxlJobHelper.getJobParam());
    }
}
