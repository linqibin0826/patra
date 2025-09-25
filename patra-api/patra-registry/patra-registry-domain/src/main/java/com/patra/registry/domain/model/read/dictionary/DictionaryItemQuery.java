package com.patra.registry.domain.model.read.dictionary;

/**
 * 字典项查询对象（CQRS 查询侧）。
 *
 * <p>在 app 与 contract 模块间共享的数据结构；不可变，便于对外 API 消费。</p>
 *
 * @param typeCode 所属字典类型编码
 * @param itemCode 字典项编码（类型内唯一）
 * @param displayName 展示名
 * @param description 描述
 * @param isDefault 是否为该类型默认项
 * @param sortOrder 排序值（小值优先）
 * @param enabled 是否启用
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemQuery(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled
) {
    
    /** 校验参数的紧凑构造器。 */
    public DictionaryItemQuery {
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
    
    /** 是否可用（启用即视为可用）。 */
    public boolean isAvailable() {
        return enabled;
    }
    
    /** 获取引用字符串（typeCode:itemCode）。 */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /** 是否可作为默认项（需可用且标记默认）。 */
    public boolean canBeDefault() {
        return isAvailable() && isDefault;
    }
}
