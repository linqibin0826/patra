package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 调度实例聚合根：记录一次计划触发及其初始快照。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduleInstanceAggregate extends AggregateRoot<Long> {

    private final Scheduler scheduler;
    private final String schedulerJobId;
    private final String schedulerLogId;
    private final TriggerType triggerType;
    private final Instant triggeredAt;
    private final ProvenanceCode provenanceCode;
    private final Map<String, Object> triggerParams;

    private ScheduleInstanceAggregate(Long id,
                                      Scheduler scheduler,
                                      String schedulerJobId,
                                      String schedulerLogId,
                                      TriggerType triggerType,
                                      Instant triggeredAt,
                                      Map<String, Object> triggerParams,
                                      ProvenanceCode provenanceCode
    ) {
        super(id);
        this.scheduler = Objects.requireNonNull(scheduler, "schedulerCode不能为空");
        this.schedulerJobId = schedulerJobId;
        this.schedulerLogId = schedulerLogId;
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType不能为空");
        this.triggeredAt = triggeredAt == null ? Instant.now() : triggeredAt;
        this.triggerParams = triggerParams;
        this.provenanceCode = provenanceCode;
        // exprProto* 不再在调度实例层面保存（留空，Plan 层保存）
    }

    public static ScheduleInstanceAggregate start(Scheduler scheduler,
                                                  String schedulerJobId,
                                                  String schedulerLogId,
                                                  TriggerType triggerType,
                                                  Instant triggeredAt,
                                                  Map<String, Object> triggerParams,
                                                  ProvenanceCode provenanceCode) {
        return new ScheduleInstanceAggregate(null,
                scheduler,
                schedulerJobId,
                schedulerLogId,
                triggerType,
                triggeredAt,
                triggerParams,
                provenanceCode);
    }

    public static ScheduleInstanceAggregate restore(Long id,
                                                    Scheduler scheduler,
                                                    String schedulerJobId,
                                                    String schedulerLogId,
                                                    TriggerType triggerType,
                                                    Instant triggeredAt,
                                                    Map<String, Object> triggerParams,
                                                    ProvenanceCode provenanceCode,
                                                    long version) {
        ScheduleInstanceAggregate aggregate = new ScheduleInstanceAggregate(id,
                scheduler,
                schedulerJobId,
                schedulerLogId,
                triggerType,
                triggeredAt,
                triggerParams,
                provenanceCode);
        aggregate.assignVersion(version);
        return aggregate;
    }

    // 目前无需额外快照记录；如未来需要可扩展方法
    public void recordSnapshots() {
    }
}
