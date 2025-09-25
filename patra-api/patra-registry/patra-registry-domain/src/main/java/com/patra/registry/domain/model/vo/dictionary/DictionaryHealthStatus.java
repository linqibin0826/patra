package com.patra.registry.domain.model.vo.dictionary;

import java.util.Collections;
import java.util.List;

/**
 * 字典系统健康状态值对象（监控/诊断）。
 *
 * <p>不可变，承载系统项数、完整性问题及其他需要关注的指标。</p>
 *
 * @param totalTypes 类型总数
 * @param totalItems 项总数
 * @param enabledItems 启用项总数
 * @param deletedItems 已删除项总数（软删）
 * @param typesWithoutDefault 无默认项的类型列表
 * @param typesWithMultipleDefaults 多默认项的类型列表
 * @param disabledTypes 禁用的类型数量
 * @param systemTypes 系统内置类型数量（只读）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryHealthStatus(
    int totalTypes,
    int totalItems,
    int enabledItems,
    int deletedItems,
    List<String> typesWithoutDefault,
    List<String> typesWithMultipleDefaults,
    int disabledTypes,
    int systemTypes
) {
    
    /** 带参数校验与不可变化集合的紧凑构造器。 */
    public DictionaryHealthStatus {
        if (totalTypes < 0) {
            throw new IllegalArgumentException("Total types count cannot be negative");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items count cannot be negative");
        }
        if (enabledItems < 0) {
            throw new IllegalArgumentException("Enabled items count cannot be negative");
        }
        if (deletedItems < 0) {
            throw new IllegalArgumentException("Deleted items count cannot be negative");
        }
        if (disabledTypes < 0) {
            throw new IllegalArgumentException("Disabled types count cannot be negative");
        }
        if (systemTypes < 0) {
            throw new IllegalArgumentException("System types count cannot be negative");
        }
        
        // Ensure immutable collections
        typesWithoutDefault = typesWithoutDefault != null ? 
            Collections.unmodifiableList(List.copyOf(typesWithoutDefault)) : 
            Collections.emptyList();
        typesWithMultipleDefaults = typesWithMultipleDefaults != null ? 
            Collections.unmodifiableList(List.copyOf(typesWithMultipleDefaults)) : 
            Collections.emptyList();
    }
    
    /** 创建“健康”状态（无问题）。 */
    public static DictionaryHealthStatus healthy(int totalTypes, int totalItems, int enabledItems, int systemTypes) {
        return new DictionaryHealthStatus(
            totalTypes, 
            totalItems, 
            enabledItems, 
            0, // no deleted items
            Collections.emptyList(), // no types without defaults
            Collections.emptyList(), // no types with multiple defaults
            0, // no disabled types
            systemTypes
        );
    }
    
    /** 是否健康（无完整性问题即健康）。 */
    public boolean isHealthy() {
        return typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty();
    }
    
    /** 是否存在完整性问题。 */
    public boolean hasIntegrityIssues() {
        return !isHealthy();
    }
    
    /** 存在问题的类型数量（无默认或多默认）。 */
    public int getTypesWithIssuesCount() {
        return typesWithoutDefault.size() + typesWithMultipleDefaults.size();
    }
    
    /** 禁用项数量（totalItems - enabledItems - deletedItems）。 */
    public int getDisabledItems() {
        return Math.max(0, totalItems - enabledItems - deletedItems);
    }
    
    /** 启用项占比（0.0~100.0；无数据返回 0.0）。 */
    public double getEnabledItemsPercentage() {
        if (totalItems == 0) {
            return 0.0;
        }
        return (double) enabledItems / totalItems * 100.0;
    }
    
    /** 非系统类型数量（用户管理类型）。 */
    public int getUserTypes() {
        return Math.max(0, totalTypes - systemTypes);
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
}
