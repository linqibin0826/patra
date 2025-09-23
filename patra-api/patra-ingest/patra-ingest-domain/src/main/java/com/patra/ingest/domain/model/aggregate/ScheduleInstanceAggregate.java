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
    private String provenanceConfigSnapshotJson;
    private String provenanceConfigHash;
    private String exprProtoHash;
    private String exprProtoSnapshotJson;

    private ScheduleInstanceAggregate(Long id,
                                      SchedulerCode schedulerCode,
                                      String schedulerJobId,
                                      String schedulerLogId,
                                      TriggerType triggerType,
                                      Instant triggeredAt,
                                      ProvenanceCode provenanceCode,
                                      String provenanceConfigSnapshotJson,
                                      String exprProtoHash,
                                      String exprProtoSnapshotJson) {
        super(id);
        this.schedulerCode = Objects.requireNonNull(schedulerCode, "schedulerCode不能为空");
        this.schedulerJobId = schedulerJobId;
        this.schedulerLogId = schedulerLogId;
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType不能为空");
        this.triggeredAt = triggeredAt == null ? Instant.now() : triggeredAt;
        this.provenanceCode = provenanceCode;
        this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
        this.exprProtoHash = exprProtoHash;
        this.exprProtoSnapshotJson = exprProtoSnapshotJson;
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
                provenanceCode,
                null,
                null,
                null);
    }

    public static ScheduleInstanceAggregate restore(Long id,
                                                    SchedulerCode schedulerCode,
                                                    String schedulerJobId,
                                                    String schedulerLogId,
                                                    TriggerType triggerType,
                                                    Instant triggeredAt,
                                                    ProvenanceCode provenanceCode,
                                                    String provenanceConfigSnapshotJson,
                                                    String exprProtoHash,
                                                    String exprProtoSnapshotJson,
                                                    long version) {
        ScheduleInstanceAggregate aggregate = new ScheduleInstanceAggregate(id,
                schedulerCode,
                schedulerJobId,
                schedulerLogId,
                triggerType,
                triggeredAt,
                provenanceCode,
                provenanceConfigSnapshotJson,
                exprProtoHash,
                exprProtoSnapshotJson);
        aggregate.assignVersion(version);
        return aggregate;
    }

    /**
     * 更新快照信息，便于回溯。
     */
    public void recordSnapshots(String provenanceConfigHash,
                                String provenanceConfigSnapshotJson,
                                String exprProtoHash,
                                String exprProtoSnapshotJson) {
        this.provenanceConfigHash = provenanceConfigHash;
        this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
        this.exprProtoHash = exprProtoHash;
        this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    }
}
