package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.SchedulerCode;
import com.patra.ingest.domain.model.enums.TriggerType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.Objects;

/**
 * 调度实例聚合根：记录一次计划触发及其初始快照。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduleInstanceAggregate extends AggregateRoot<Long> {

    private final SchedulerCode schedulerCode;
    private final String schedulerJobId;
    private final String schedulerLogId;
    private final TriggerType triggerType;
    private final Instant triggeredAt;
    private final ProvenanceCode provenanceCode;

    private ScheduleInstanceAggregate(Long id,
                                      SchedulerCode schedulerCode,
                                      String schedulerJobId,
                                      String schedulerLogId,
                                      TriggerType triggerType,
                                      Instant triggeredAt,
                                      ProvenanceCode provenanceCode) {
        super(id);
        this.schedulerCode = Objects.requireNonNull(schedulerCode, "schedulerCode不能为空");
        this.schedulerJobId = schedulerJobId;
        this.schedulerLogId = schedulerLogId;
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType不能为空");
        this.triggeredAt = triggeredAt == null ? Instant.now() : triggeredAt;
        this.provenanceCode = provenanceCode;
        // exprProto* 不再在调度实例层面保存（留空，Plan 层保存）
    }

    public static ScheduleInstanceAggregate start(SchedulerCode schedulerCode,
            String schedulerJobId,
            String schedulerLogId,
            TriggerType triggerType,
            Instant triggeredAt,
            ProvenanceCode provenanceCode) {
    return new ScheduleInstanceAggregate(null,
        schedulerCode,
        schedulerJobId,
        schedulerLogId,
        triggerType,
        triggeredAt,
        provenanceCode);
    }

    public static ScheduleInstanceAggregate restore(Long id,
            SchedulerCode schedulerCode,
            String schedulerJobId,
            String schedulerLogId,
            TriggerType triggerType,
            Instant triggeredAt,
            ProvenanceCode provenanceCode,
            long version) {
    ScheduleInstanceAggregate aggregate = new ScheduleInstanceAggregate(id,
        schedulerCode,
        schedulerJobId,
        schedulerLogId,
        triggerType,
        triggeredAt,
        provenanceCode);
        aggregate.assignVersion(version);
        return aggregate;
    }

    // 目前无需额外快照记录；如未来需要可扩展方法
    public void recordSnapshots() {}
}
