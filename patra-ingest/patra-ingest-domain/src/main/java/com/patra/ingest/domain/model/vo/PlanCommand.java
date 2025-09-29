package com.patra.ingest.domain.model.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * 计划触发命令（标准化输入）。
 * <p>封装 Adapter → 应用层传入的创建参数，集中进行：必填校验 / 编码合法性 / 时间窗口合理性 / 默认值注入。</p>
 * <ul>
 *   <li>operationCode：必须在 HARVEST/BACKFILL/UPDATE/METRICS 集合内</li>
 *   <li>windowFrom/windowTo：若同时存在需满足 from < to</li>
 *   <li>priority：未提供时默认 5（范围策略可在上层再校正）</li>
 * </ul>
 * 幂等：业务幂等键可通过 {@link #generatePlanKey()} 生成。
 */
public record PlanCommand(
        String provenanceCode,
        String endpointName,
        String operationCode,
        String triggerTypeCode,
        String schedulerCode,
        String schedulerJobId,
        String schedulerLogId,
        Instant windowFrom,
        Instant windowTo,
        Integer priority,
        String triggerParams
) {

    public PlanCommand {
        Objects.requireNonNull(provenanceCode, "provenanceCode不能为空");
        Objects.requireNonNull(operationCode, "operationCode不能为空");
        Objects.requireNonNull(triggerTypeCode, "triggerTypeCode不能为空");
        Objects.requireNonNull(schedulerCode, "schedulerCode不能为空");

        // 校验操作代码
        if (!isValidOperationCode(operationCode)) {
            throw new IllegalArgumentException("无效的操作代码: " + operationCode);
        }

        // 校验时间窗口（如果提供）
        if (windowFrom != null && windowTo != null && !windowFrom.isBefore(windowTo)) {
            throw new IllegalArgumentException("窗口起始时间必须早于结束时间");
        }

        // 设置默认优先级
        priority = priority != null ? priority : 5;
    }

    /**
     * 是否 HARVEST 操作
     */
    public boolean isHarvestOperation() {
        return "HARVEST".equalsIgnoreCase(operationCode);
    }

    /**
     * 是否 BACKFILL 操作
     */
    public boolean isBackfillOperation() {
        return "BACKFILL".equalsIgnoreCase(operationCode);
    }

    /**
     * 是否 UPDATE 操作
     */
    public boolean isUpdateOperation() {
        return "UPDATE".equalsIgnoreCase(operationCode);
    }

    /**
     * 是否需要明确时间窗口（HARVEST/BACKFILL 返回 true）
     */
    public boolean requiresTimeWindow() {
        return isHarvestOperation() || isBackfillOperation();
    }

    /**
     * 是否提供了显式时间窗口
     */
    public boolean hasExplicitTimeWindow() {
        return windowFrom != null && windowTo != null;
    }

    /**
     * 生成计划幂等业务键（组成：provenance + operation + optional endpoint + window/hash）。
     */
    public String generatePlanKey() {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(provenanceCode).append(":");
        keyBuilder.append(operationCode).append(":");

        if (endpointName != null) {
            keyBuilder.append(endpointName).append(":");
        }

        if (hasExplicitTimeWindow()) {
            keyBuilder.append(windowFrom.getEpochSecond()).append("-").append(windowTo.getEpochSecond());
        } else {
            keyBuilder.append("auto-window");
        }

        return keyBuilder.toString();
    }

    /**
     * 校验操作码是否在支持集合内
     */
    private static boolean isValidOperationCode(String operationCode) {
        return operationCode != null &&
                ("HARVEST".equalsIgnoreCase(operationCode) ||
                        "BACKFILL".equalsIgnoreCase(operationCode) ||
                        "UPDATE".equalsIgnoreCase(operationCode) ||
                        "METRICS".equalsIgnoreCase(operationCode));
    }
}
