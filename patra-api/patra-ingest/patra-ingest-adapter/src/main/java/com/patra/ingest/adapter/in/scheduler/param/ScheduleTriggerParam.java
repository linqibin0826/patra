package com.patra.ingest.adapter.in.scheduler.param;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 调度触发参数。
 *
 * <p>封装XXL-Job调度任务的输入参数，包含触发条件、时间窗口。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Builder
public class ScheduleTriggerParam {
    
    /**
     * 调度器代码，默认 XXL。
     */
    @Builder.Default
    private Scheduler scheduler = Scheduler.XXL;
    
    /**
     * 端点名称。
     */
    private String endpointName;
    
    /**
     * 触发器类型，默认 SCHEDULE。
     */
    @Builder.Default
    private TriggerType triggerType = TriggerType.SCHEDULE;
    
    /**
     * 来源代码（必需）。
     */
    private ProvenanceCode provenanceCode;
    
    /**
     * 优先级（可选）。
     */
    @Builder.Default
    private Priority priority = Priority.NORMAL;
    
    /**
     * 操作代码（必需）。
     */
    private OperationCode operationCode;
    
    /**
     * 时间窗口开始时间。
     */
    private Instant windowFrom;
    
    /**
     * 时间窗口结束时间。
     */
    private Instant windowTo;
}
