package com.patra.registry.domain.model.vo;

/**
 * 字典项值对象（不可变）。
 *
 * <p>封装项标识、展示属性、状态与排序信息。</p>
 *
 * @param itemCode 项编码（在类型内唯一）
 * @param displayName 展示名
 * @param description 描述
 * @param isDefault 是否为默认项（每个类型仅允许一个）
 * @param sortOrder 排序值（小值优先）
 * @param enabled 是否启用
 * @param deleted 是否逻辑删除
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItem(
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled,
    boolean deleted
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryItem {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item display name cannot be null or empty");
        }
        // Normalize the codes and names to ensure consistency
        itemCode = itemCode.trim();
        displayName = displayName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /** 是否可用（启用且未删除）。 */
    public boolean isAvailable() {
        return enabled && !deleted;
    }
    
    /** 是否可见（未删除）。 */
    public boolean isVisible() {
        return !deleted;
    }
    
    /** 是否可作为默认项（需可用且标记为默认）。 */
    public boolean canBeDefault() {
        return isAvailable() && isDefault;
    }
    
    /** 返回修改启用状态后的拷贝。 */
    public DictionaryItem withEnabled(boolean newEnabled) {
        return new DictionaryItem(itemCode, displayName, description, isDefault, sortOrder, newEnabled, deleted);
    }
    
    /** 返回修改默认标记后的拷贝。 */
    public DictionaryItem withDefault(boolean newIsDefault) {
        return new DictionaryItem(itemCode, displayName, description, newIsDefault, sortOrder, enabled, deleted);
    }
}
