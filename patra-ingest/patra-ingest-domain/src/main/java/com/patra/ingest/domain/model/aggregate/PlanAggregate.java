package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;

import java.time.Instant;
import java.util.Objects;

/**
 * 计划聚合根：封装一次采集计划的“蓝图”与状态流转逻辑。
 * <p>包含窗口信息、表达式/配置快照、切片策略与当前状态；提供有限状态迁移方法，
 * 不直接承担持久化逻辑（由 Repository 负责）。</p>
 * <p>幂等：通过 planKey（来源 + 操作 + 窗口 + 策略 哈希）在仓储层实现复用。</p>
 * <p>状态流转约束：DRAFT → SLICING → READY/PARTIAL → COMPLETED/FAILED；FAILED 可由上层补偿逻辑决定后续行为。</p>
 * <p>
 * 线程安全：聚合为单线程内构建与修改（不保证并发安全），请勿跨线程共享引用。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class PlanAggregate extends AggregateRoot<Long> {

    /**
     * 调度实例 ID（与一次外部调度触发关联）
     */
    private final Long scheduleInstanceId;
    /**
     * 计划幂等键（业务唯一标识，用于去重）
     */
    private final String planKey;
    /**
     * 来源编码（如：PUBMED）
     */
    private final String provenanceCode;
    /**
     * 来源端点（枚举：区分同一来源下不同数据子域）
     */
    private final Endpoint endpoint;
    /**
     * 操作类型（枚举：全量、增量、补偿等）
     */
    private final OperationCode operationCode;
    /**
     * 计划表达式原型哈希（用于快速对比变化）
     */
    private final String exprProtoHash;
    /**
     * 表达式原型快照 JSON（未编译，原始结构化内容）
     */
    private final String exprProtoSnapshotJson;
    /**
     * 来源配置快照 JSON（执行时稳定快照）
     */
    private final String provenanceConfigSnapshotJson;
    /**
     * 来源配置快照哈希（用于检测配置变更）
     */
    private final String provenanceConfigHash;
    /**
     * 窗口开始（半开区间左边界）
     */
    private final Instant windowFrom;
    /**
     * 窗口结束（半开区间右边界）
     */
    private final Instant windowTo;
    /**
     * 切片策略编码（如 TIME / SINGLE 等）
     */
    private final String sliceStrategyCode;
    /**
     * 切片策略参数 JSON（策略自定义动态参数）
     */
    private final String sliceParamsJson;
    /**
     * 当前计划状态（状态机字段）
     */
    private PlanStatus status;

    private PlanAggregate(Long id,
                          Long scheduleInstanceId,
                          String planKey,
                          String provenanceCode,
                          Endpoint endpoint,
                          OperationCode operationCode,
                          String exprProtoHash,
                          String exprProtoSnapshotJson,
                          String provenanceConfigSnapshotJson,
                          String provenanceConfigHash,
                          Instant windowFrom,
                          Instant windowTo,
                          String sliceStrategyCode,
                          String sliceParamsJson,
                          PlanStatus status) {
        super(id);
        this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId不能为空");
        this.planKey = Objects.requireNonNull(planKey, "planKey不能为空");
        this.provenanceCode = provenanceCode;
        this.endpoint = endpoint;
        this.operationCode = operationCode;
        this.exprProtoHash = exprProtoHash;
        this.exprProtoSnapshotJson = exprProtoSnapshotJson;
        this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
        this.provenanceConfigHash = provenanceConfigHash;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.sliceStrategyCode = sliceStrategyCode;
        this.sliceParamsJson = sliceParamsJson;
        this.status = status == null ? PlanStatus.DRAFT : status;
    }

    /**
     * 创建新的计划蓝图聚合（初始状态 DRAFT）。
     *
     * @param scheduleInstanceId           调度实例 ID
     * @param planKey                      幂等键
     * @param provenanceCode               来源编码
     * @param endpointName                 端点字符串（将解析为枚举）
     * @param operationCode                操作码字符串（将解析为枚举）
     * @param exprProtoHash                表达式原型哈希
     * @param exprProtoSnapshotJson        表达式原型快照 JSON
     * @param provenanceConfigSnapshotJson 来源配置快照 JSON
     * @param provenanceConfigHash         来源配置快照哈希
     * @param windowFrom                   窗口起始
     * @param windowTo                     窗口结束
     * @param sliceStrategyCode            切片策略编码
     * @param sliceParamsJson              切片策略参数 JSON
     * @return 新的计划聚合
     */
    public static PlanAggregate create(Long scheduleInstanceId,
                                       String planKey,
                                       String provenanceCode,
                                       String endpointName,
                                       String operationCode,
                                       String exprProtoHash,
                                       String exprProtoSnapshotJson,
                                       String provenanceConfigSnapshotJson,
                                       String provenanceConfigHash,
                                       Instant windowFrom,
                                       Instant windowTo,
                                       String sliceStrategyCode,
                                       String sliceParamsJson) {
        // 解析为领域内枚举，统一大小写/空白处理
        Endpoint endpoint = endpointName == null ? null : Endpoint.fromCode(endpointName);
        OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
        return new PlanAggregate(null,
                scheduleInstanceId,
                planKey,
                provenanceCode,
                endpoint,
                op,
                exprProtoHash,
                exprProtoSnapshotJson,
                provenanceConfigSnapshotJson,
                provenanceConfigHash,
                windowFrom,
                windowTo,
                sliceStrategyCode,
                sliceParamsJson,
                PlanStatus.DRAFT);
    }

    /**
     * 从持久化记录重建计划聚合（用于仓储层 restore）。
     *
     * @param id                           主键 ID
     * @param scheduleInstanceId           调度实例 ID
     * @param planKey                      计划幂等键
     * @param provenanceCode               来源编码
     * @param endpointName                 端点字符串
     * @param operationCode                操作码字符串
     * @param exprProtoHash                表达式哈希
     * @param exprProtoSnapshotJson        表达式快照 JSON
     * @param provenanceConfigSnapshotJson 配置快照 JSON
     * @param provenanceConfigHash         配置快照哈希
     * @param windowFrom                   窗口开始
     * @param windowTo                     窗口结束
     * @param sliceStrategyCode            切片策略编码
     * @param sliceParamsJson              切片策略参数 JSON
     * @param status                       当前状态
     * @param version                      乐观锁版本号
     * @return 计划聚合
     */
    public static PlanAggregate restore(Long id,
                                        Long scheduleInstanceId,
                                        String planKey,
                                        String provenanceCode,
                                        String endpointName,
                                        String operationCode,
                                        String exprProtoHash,
                                        String exprProtoSnapshotJson,
                                        String provenanceConfigSnapshotJson,
                                        String provenanceConfigHash,
                                        Instant windowFrom,
                                        Instant windowTo,
                                        String sliceStrategyCode,
                                        String sliceParamsJson,
                                        PlanStatus status,
                                        long version) {
        Endpoint endpoint = endpointName == null ? null : Endpoint.fromCode(endpointName);
        OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
        PlanAggregate aggregate = new PlanAggregate(id,
                scheduleInstanceId,
                planKey,
                provenanceCode,
                endpoint,
                op,
                exprProtoHash,
                exprProtoSnapshotJson,
                provenanceConfigSnapshotJson,
                provenanceConfigHash,
                windowFrom,
                windowTo,
                sliceStrategyCode,
                sliceParamsJson,
                status);
        aggregate.assignVersion(version);
        return aggregate;
    }

    public void startSlicing() {
        if (this.status != PlanStatus.DRAFT) {
            throw new IllegalStateException("计划状态非法，无法进入切片阶段");
        }
        this.status = PlanStatus.SLICING;
    }

    /**
     * 标记计划已生成全部切片，进入待发布阶段。
     */
    public void markReady() {
        this.status = PlanStatus.READY;
    }

    /**
     * 标记计划部分成功（部分任务/切片重试中）。
     */
    public void markPartial() {
        this.status = PlanStatus.PARTIAL;
    }

    /**
     * 标记计划失败（终止或需外部补偿）。
     */
    public void markFailed() {
        this.status = PlanStatus.FAILED;
    }

    /**
     * 标记计划已完成（所有任务完成）。
     */
    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
    }

    public Long getScheduleInstanceId() {
        return scheduleInstanceId;
    }

    /**
     * 获取计划幂等键。
     *
     * @return planKey
     */
    public String getPlanKey() {
        return planKey;
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }

    /**
     * 获取端点名称（可能为 null）。
     *
     * @return endpoint name 或 null
     */
    public String getEndpointName() {
        return endpoint == null ? null : endpoint.name();
    }

    /**
     * 获取操作码（可能为 null）。
     *
     * @return operation code 或 null
     */
    public String getOperationCode() {
        return operationCode == null ? null : operationCode.getCode();
    }

    // ========== 枚举原始访问器（领域内部使用） ==========
    public Endpoint getEndpoint() {
        return endpoint;
    }

    public OperationCode getOperation() {
        return operationCode;
    }

    public String getExprProtoHash() {
        return exprProtoHash;
    }

    public String getExprProtoSnapshotJson() {
        return exprProtoSnapshotJson;
    }

    public String getProvenanceConfigSnapshotJson() {
        return provenanceConfigSnapshotJson;
    }

    public String getProvenanceConfigHash() {
        return provenanceConfigHash;
    }

    public Instant getWindowFrom() {
        return windowFrom;
    }

    public Instant getWindowTo() {
        return windowTo;
    }

    public String getSliceStrategyCode() {
        return sliceStrategyCode;
    }

    public String getSliceParamsJson() {
        return sliceParamsJson;
    }

    /**
     * 获取当前计划状态。
     */
    public PlanStatus getStatus() {
        return status;
    }
}
