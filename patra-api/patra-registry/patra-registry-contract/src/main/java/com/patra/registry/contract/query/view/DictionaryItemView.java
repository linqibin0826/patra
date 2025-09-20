package com.patra.registry.contract.query.view;

/**
 * 字典项视图对象（对外消费，query.view）。
 *
 * <p>用于对外接口的简化项信息表示，隐藏内部状态字段（如 enabled/deleted）。</p>
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
    
    /** 获取引用字符串（typeCode:itemCode）。 */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /**
     * 从 DictionaryItemQuery 构建视图对象（对外字段）。
     *
     * @see com.patra.registry.contract.query.view.DictionaryItemQuery
     */
    public static DictionaryItemView fromQuery(DictionaryItemQuery query) {
        return new DictionaryItemView(
            query.typeCode(),
            query.itemCode(),
            query.displayName(),
            query.description(),
            query.isDefault(),
            query.sortOrder()
        );
    }
}
