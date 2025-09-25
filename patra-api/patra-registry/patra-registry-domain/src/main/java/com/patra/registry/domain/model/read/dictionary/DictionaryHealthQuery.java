package com.patra.registry.domain.model.read.dictionary;

import java.util.Collections;
import java.util.List;

/**
 * 字典系统健康状态查询对象（用于监控）。
 *
 * <p>在 app 与 contract 模块间共享；不可变，承载字典系统的健康指标，用于监控/诊断/健康检查。</p>
 *
 * @param totalTypes 字典类型总数
 * @param totalItems 字典项总数
 * @param enabledItems 启用项总数
 * @param typesWithoutDefault 无默认项的类型编码列表（潜在问题）
 * @param typesWithMultipleDefaults 存在多个默认项的类型编码列表（数据完整性问题）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryHealthQuery(
    int totalTypes,
    int totalItems,
    int enabledItems,
    List<String> typesWithoutDefault,
    List<String> typesWithMultipleDefaults
) {
    
    /** 校验参数并不可变化集合的紧凑构造器。 */
    public DictionaryHealthQuery {
        if (totalTypes < 0) {
            throw new IllegalArgumentException("Total types count cannot be negative");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items count cannot be negative");
        }
        if (enabledItems < 0) {
            throw new IllegalArgumentException("Enabled items count cannot be negative");
        }
        
        // Ensure immutable collections
        typesWithoutDefault = typesWithoutDefault != null ? 
            Collections.unmodifiableList(List.copyOf(typesWithoutDefault)) : 
            Collections.emptyList();
        typesWithMultipleDefaults = typesWithMultipleDefaults != null ? 
            Collections.unmodifiableList(List.copyOf(typesWithMultipleDefaults)) : 
            Collections.emptyList();
    }
    
    /** 创建无问题的健康状态。 */
    public static DictionaryHealthQuery healthy(int totalTypes, int totalItems, int enabledItems) {
        return new DictionaryHealthQuery(
            totalTypes, 
            totalItems, 
            enabledItems, 
            Collections.emptyList(), // no types without defaults
            Collections.emptyList()  // no types with multiple defaults
        );
    }
    
    /** 是否健康（无完整性问题即为健康）。 */
    public boolean isHealthy() {
        return typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty();
    }
    
    /** 是否存在数据完整性问题。 */
    public boolean hasIntegrityIssues() {
        return !isHealthy();
    }
    
    /** 存在问题的类型数量（无默认或多默认）。 */
    public int getTypesWithIssuesCount() {
        return typesWithoutDefault.size() + typesWithMultipleDefaults.size();
    }
    
    /** 禁用项数量（totalItems - enabledItems）。 */
    public int getDisabledItems() {
        return Math.max(0, totalItems - enabledItems);
    }
    
    /** 启用项占比（0.0~100.0；无数据返回 0.0）。 */
    public double getEnabledItemsPercentage() {
        if (totalItems == 0) {
            return 0.0;
        }
        return (double) enabledItems / totalItems * 100.0;
    }
    
    /** 是否存在无默认项的类型。 */
    public boolean hasTypesWithoutDefaults() {
        return !typesWithoutDefault.isEmpty();
    }
    
    /** 是否存在多个默认项的类型。 */
    public boolean hasTypesWithMultipleDefaults() {
        return !typesWithMultipleDefaults.isEmpty();
    }
    
    /** 获取健康状态摘要。 */
    public String getHealthSummary() {
        if (isHealthy()) {
            return String.format("Healthy: %d types, %d items (%d enabled)", 
                totalTypes, totalItems, enabledItems);
        } else {
            return String.format("Issues detected: %d types with problems out of %d total types", 
                getTypesWithIssuesCount(), totalTypes);
        }
    }
    
    /** 获取存在完整性问题的所有类型（含无默认与多默认）。 */
    public List<String> getAllTypesWithIssues() {
        if (typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> allIssues = new java.util.ArrayList<>();
        allIssues.addAll(typesWithoutDefault);
        allIssues.addAll(typesWithMultipleDefaults);
        return Collections.unmodifiableList(allIssues);
    }
}
