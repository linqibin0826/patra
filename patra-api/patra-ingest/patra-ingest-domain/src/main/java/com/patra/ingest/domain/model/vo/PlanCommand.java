package com.patra.ingest.domain.model.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * 计划触发命令值对象。
 * 
 * <p>封装从Adapter层传入的计划创建参数，经过缺省解析和校验后的标准化命令对象。</p>
 * 
 * @author linqibin
 * @since 0.1.0
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
     * 检查是否为HARVEST操作。
     */
    public boolean isHarvestOperation() {
        return "HARVEST".equalsIgnoreCase(operationCode);
    }
    
    /**
     * 检查是否为BACKFILL操作。
     */
    public boolean isBackfillOperation() {
        return "BACKFILL".equalsIgnoreCase(operationCode);
    }
    
    /**
     * 检查是否为UPDATE操作。
     */
    public boolean isUpdateOperation() {
        return "UPDATE".equalsIgnoreCase(operationCode);
    }
    
    /**
     * 检查是否需要时间窗口。
     */
    public boolean requiresTimeWindow() {
        return isHarvestOperation() || isBackfillOperation();
    }
    
    /**
     * 检查是否有明确的时间窗口。
     */
    public boolean hasExplicitTimeWindow() {
        return windowFrom != null && windowTo != null;
    }
    
    /**
     * 生成计划键。
     * 
     * <p>用于计划的业务幂等标识，基于关键参数生成唯一键。</p>
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
    
    private static boolean isValidOperationCode(String operationCode) {
        return operationCode != null && 
               ("HARVEST".equalsIgnoreCase(operationCode) || 
                "BACKFILL".equalsIgnoreCase(operationCode) || 
                "UPDATE".equalsIgnoreCase(operationCode) ||
                "METRICS".equalsIgnoreCase(operationCode));
    }
}
