package com.patra.registry.contract.view;

/**
 * 字典类型视图对象（对外消费）。
 *
 * <p>位于 contract 模块，作为清晰 API 边界；不可变表示，面向外部消费。</p>
 *
 * @param typeCode 类型编码
 * @param typeName 类型名称
 * @param description 类型描述
 * @param itemCount 可用项数量（仅启用项）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeView(
    String typeCode,
    String typeName,
    String description,
    int itemCount
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryTypeView {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("Item count cannot be negative");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /** 是否含有可用项。 */
    public boolean hasItems() {
        return itemCount > 0;
    }
    
    /** 是否包含有效描述。 */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }
    
    /** 获取显示标签（typeCode - typeName），便于外部下拉/选择组件。 */
    public String getDisplayLabel() {
        return typeCode + " - " + typeName;
    }
    
    /** 获取类型摘要（含项数）。 */
    public String getSummary() {
        if (itemCount == 0) {
            return typeName + " (no items)";
        } else if (itemCount == 1) {
            return typeName + " (1 item)";
        } else {
            return typeName + " (" + itemCount + " items)";
        }
    }
    
    /** 创建简化视图（清空描述）。 */
    public DictionaryTypeView toSimplifiedView() {
        return new DictionaryTypeView(typeCode, typeName, "", itemCount);
    }
    
    /** 是否适合外部使用（有名称且至少一个项）。 */
    public boolean isSuitableForExternalUse() {
        return hasItems() && typeName != null && !typeName.trim().isEmpty();
    }
}
