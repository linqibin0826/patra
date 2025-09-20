package com.patra.registry.contract.query.view;

/**
 * 字典类型查询对象（CQRS 查询侧）。
 *
 * <p>在 app 与 contract 模块间共享；不可变，便于对外 API 消费。</p>
 *
 * @param typeCode 类型编码
 * @param typeName 类型名称
 * @param description 类型描述
 * @param enabledItemCount 启用项数量
 * @param hasDefault 是否配置了默认项
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeQuery(
    String typeCode,
    String typeName,
    String description,
    int enabledItemCount,
    boolean hasDefault
) {
    
    /** 校验参数的紧凑构造器。 */
    public DictionaryTypeQuery {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        if (enabledItemCount < 0) {
            throw new IllegalArgumentException("Enabled item count cannot be negative");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /** 是否有启用项。 */
    public boolean hasEnabledItems() {
        return enabledItemCount > 0;
    }
    
    /** 是否配置完善（存在启用项且有默认项）。 */
    public boolean isProperlyConfigured() {
        return hasEnabledItems() && hasDefault;
    }
    
    /** 是否存在配置问题（无启用项或缺默认项）。 */
    public boolean hasConfigurationIssues() {
        return !hasEnabledItems() || (hasEnabledItems() && !hasDefault);
    }
    
    /** 获取类型状态摘要。 */
    public String getStatusSummary() {
        if (!hasEnabledItems()) {
            return "No enabled items";
        } else if (!hasDefault) {
            return String.format("%d items, no default", enabledItemCount);
        } else {
            return String.format("%d items, has default", enabledItemCount);
        }
    }
}
