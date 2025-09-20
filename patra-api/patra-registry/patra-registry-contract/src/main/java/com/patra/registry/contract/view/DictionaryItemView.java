package com.patra.registry.contract.view;

/**
 * 字典项视图对象（对外消费）。
 *
 * <p>位于 contract 模块，作为清晰 API 边界，供外部系统集成使用；
 * 不可变，屏蔽内部实现细节。</p>
 *
 * @param typeCode 所属类型编码
 * @param itemCode 字典项编码（类型内唯一）
 * @param displayName 展示名
 * @param description 描述
 * @param isDefault 是否默认项
 * @param sortOrder 排序值（小值优先）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemView(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryItemView {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item display name cannot be null or empty");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
        displayName = displayName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /** 获取引用字符串（typeCode:itemCode），便于日志/集成。 */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /** 获取展示标签（itemCode - displayName），便于外部下拉/选择组件。 */
    public String getDisplayLabel() {
        return itemCode + " - " + displayName;
    }
    
    /** 是否包含有效描述。 */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }
    
    /** 创建简化视图（仅保留必要字段）。 */
    public DictionaryItemView toSimplifiedView() {
        return new DictionaryItemView(typeCode, itemCode, displayName, "", isDefault, 0);
    }
}
