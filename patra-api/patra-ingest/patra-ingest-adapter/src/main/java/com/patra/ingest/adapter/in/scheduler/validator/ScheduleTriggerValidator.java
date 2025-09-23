package com.patra.ingest.adapter.in.scheduler.validator;

import com.patra.ingest.adapter.in.scheduler.param.ScheduleTriggerParam;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 调度触发参数验证器。
 *
 * <p>按照指定顺序执行前置校验：
 * 1) provenance 存在 
 * 2) operation 可用 
 * 3) window 合法(from<to,UTC) 
 * 4) safetyLag ≤ provider.maxLag</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTriggerValidator {
    
    /**
     * 执行完整的触发参数验证。
     *
     * @param param 触发参数
     * @param config 来源配置快照（用于验证操作可用性和安全延迟）
     * @throws IngestConfigurationException 验证失败时抛出
     */
    public void validate(ScheduleTriggerParam param, ProvenanceConfigSnapshot config) {
        Objects.requireNonNull(param, "触发参数不能为空");
        
        log.debug("开始验证调度触发参数, provenance={}, operation={}, endpoint={}", 
                param.getProvenanceCode(), param.getOperationCode(), param.getEndpointName());
        
        // 1) provenance 存在
        validateProvenanceExists(param, config);
        
        // 2) operation 可用  
        validateOperationAvailable(param, config);
        
        // 3) window 合法
        validateWindowValid(param);
        
        // 4) safetyLag ≤ provider.maxLag
        validateSafetyLag(param, config);
        
        log.debug("调度触发参数验证通过");
    }
    
    /**
     * 1) 验证 provenance 存在。
     */
    private void validateProvenanceExists(ScheduleTriggerParam param, ProvenanceConfigSnapshot config) {
        if (param.getProvenanceCode() == null) {
            throw new IngestConfigurationException(
                    null, 
                    param.getOperationCode() != null ? param.getOperationCode().getCode() : null,
                    param.getEndpointName(),
                    "来源代码不能为空",
                    null
            );
        }
        
        if (config == null) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode() != null ? param.getOperationCode().getCode() : null,
                    param.getEndpointName(),
                    String.format("来源配置不存在: %s", param.getProvenanceCode().getCode()),
                    null
            );
        }
        
        if (config.provenance() == null || config.provenance().code() == null) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode() != null ? param.getOperationCode().getCode() : null,
                    param.getEndpointName(),
                    String.format("来源配置无效: %s", param.getProvenanceCode().getCode()),
                    null
            );
        }
        
        // 验证代码匹配
        if (!param.getProvenanceCode().getCode().equals(config.provenance().code())) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode() != null ? param.getOperationCode().getCode() : null,
                    param.getEndpointName(),
                    String.format("来源代码不匹配: 请求=%s, 配置=%s", 
                            param.getProvenanceCode().getCode(), config.provenance().code()),
                    null
            );
        }
        
        log.debug("来源验证通过: {}", param.getProvenanceCode().getCode());
    }
    
    /**
     * 2) 验证 operation 可用。
     */
    private void validateOperationAvailable(ScheduleTriggerParam param, ProvenanceConfigSnapshot config) {
        if (param.getOperationCode() == null) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    null,
                    param.getEndpointName(),
                    "操作代码不能为空",
                    null
            );
        }
        
        // 检查端点配置是否支持该操作
        if (config.endpoint() != null) {
            String configTaskType = config.endpoint().taskType();
            String paramOperation = param.getOperationCode().getCode().toLowerCase();
            
            // 如果配置中有任务类型，验证操作是否匹配
            if (configTaskType != null && !configTaskType.toLowerCase().contains(paramOperation)) {
                log.warn("操作可能不被支持: 配置任务类型={}, 请求操作={}", configTaskType, paramOperation);
                // 这里只记录警告，不抛异常，允许尝试执行
            }
        }
        
        log.debug("操作验证通过: {}", param.getOperationCode().getCode());
    }
    
    /**
     * 3) 验证 window 合法(from<to,UTC)。
     */
    private void validateWindowValid(ScheduleTriggerParam param) {
        if (param.getWindowFrom() == null) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode().getCode(),
                    param.getEndpointName(),
                    "时间窗口开始时间不能为空",
                    null
            );
        }
        
        Instant now = Instant.now();
        
        // 验证开始时间不能太久以前（防止意外的大范围回填）
        if (param.getWindowFrom().isBefore(now.minus(Duration.ofDays(365)))) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode().getCode(),
                    param.getEndpointName(),
                    String.format("时间窗口开始时间过早: %s (不能超过一年前)", param.getWindowFrom()),
                    null
            );
        }
        
        // 如果有结束时间，验证 from < to
        if (param.getWindowTo() != null) {
            if (!param.getWindowFrom().isBefore(param.getWindowTo())) {
                throw new IngestConfigurationException(
                        param.getProvenanceCode().getCode(),
                        param.getOperationCode().getCode(),
                        param.getEndpointName(),
                        String.format("时间窗口无效: 开始时间(%s) 必须早于结束时间(%s)", 
                                param.getWindowFrom(), param.getWindowTo()),
                        null
                );
            }
            
            // 验证结束时间不能太久以后
            if (param.getWindowTo().isAfter(now.plus(Duration.ofDays(30)))) {
                throw new IngestConfigurationException(
                        param.getProvenanceCode().getCode(),
                        param.getOperationCode().getCode(),
                        param.getEndpointName(),
                        String.format("时间窗口结束时间过晚: %s (不能超过30天后)", param.getWindowTo()),
                        null
                );
            }
        }
        
        log.debug("时间窗口验证通过: {} -> {}", param.getWindowFrom(), param.getWindowTo());
    }
    
    /**
     * 4) 验证 safetyLag ≤ provider.maxLag。
     */
    private void validateSafetyLag(ScheduleTriggerParam param, ProvenanceConfigSnapshot config) {
        if (param.getSafetyLag() == null) {
            log.debug("安全延迟未设置，跳过验证");
            return;
        }
        
        if (param.getSafetyLag().isNegative()) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode().getCode(),
                    param.getEndpointName(),
                    String.format("安全延迟不能为负数: %s", param.getSafetyLag()),
                    null
            );
        }
        
        // 检查是否超过配置的最大延迟
        if (config.windowOffset() != null && config.windowOffset().watermarkLagSeconds() != null) {
            Duration maxLag = Duration.ofSeconds(config.windowOffset().watermarkLagSeconds());
            if (param.getSafetyLag().compareTo(maxLag) > 0) {
                throw new IngestConfigurationException(
                        param.getProvenanceCode().getCode(),
                        param.getOperationCode().getCode(),
                        param.getEndpointName(),
                        String.format("安全延迟(%s)超过提供商最大延迟(%s)", 
                                param.getSafetyLag(), maxLag),
                        null
                );
            }
        }
        
        // 通用安全检查：不能超过24小时
        if (param.getSafetyLag().compareTo(Duration.ofHours(24)) > 0) {
            throw new IngestConfigurationException(
                    param.getProvenanceCode().getCode(),
                    param.getOperationCode().getCode(),
                    param.getEndpointName(),
                    String.format("安全延迟(%s)不能超过24小时", param.getSafetyLag()),
                    null
            );
        }
        
        log.debug("安全延迟验证通过: {}", param.getSafetyLag());
    }
}
