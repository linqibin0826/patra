package com.patra.ingest.adapter.in.scheduler.job;

import com.patra.common.enums.ProvenanceCode;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.patra.ingest.domain.model.enums.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed 调度任务。
 *
 * <p>负责 PubMed 数据源的定时采集任务，包含：
 * - HARVEST: 增量采集新发布的文献
 * - BACKFILL: 回填历史数据
 * - UPDATE: 更新已有文献的元数据</p>
 *
 * <p>使用JSON参数格式：</p>
 * <pre>
 * {
 *   "endpointName": "search",
 *   "operationCode": "HARVEST",
 *   "windowFrom": "2024-01-01T00:00:00Z",
 *   "windowTo": "2024-01-02T00:00:00Z",
 *   "priority": 1,
 *   "triggerParams": {
 *     "batchSize": 1000,
 *     "maxRetries": 3
 *   }
 * }
 * </pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PubMedScheduleJob extends AbstractProvenanceScheduleJob {
    
    @Override
    protected ProvenanceCode getProvenanceCode() { return ProvenanceCode.PUBMED; }

    @Override
    protected OperationType getOperationType() { return OperationType.HARVEST; }
    
    /**
     * PubMed HARVEST 任务入口点。
     *
     * <p>XXL-Job 参数格式为JSON字符串</p>
     */
    @XxlJob("pubmedHarvest")
    public void harvest() { executeScheduleJob(XxlJobHelper.getJobParam()); }
    
    /**
     * PubMed BACKFILL 任务入口点。
     *
     * <p>XXL-Job 参数格式为JSON字符串</p>
     */
    @XxlJob("pubmedBackfill")
    public void backfill() { executeScheduleJob(XxlJobHelper.getJobParam()); }
    
    /**
     * PubMed UPDATE 任务入口点。
     *
     * <p>XXL-Job 参数格式为JSON字符串</p>
     */
    @XxlJob("pubmedUpdate")
    public void update() { executeScheduleJob(XxlJobHelper.getJobParam()); }
}
