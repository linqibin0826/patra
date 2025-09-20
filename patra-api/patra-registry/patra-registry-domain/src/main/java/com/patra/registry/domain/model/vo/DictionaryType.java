package com.patra.registry.domain.model.vo;

/**
 * 字典类型值对象（不可变）。
 *
 * <p>封装类型标识、展示属性与行为特征。</p>
 *
 * @param typeCode 类型编码（必填）
 * @param typeName 类型名称（展示）
 * @param description 类型描述
 * @param allowCustomItems 是否允许用户自定义项
 * @param isSystem 是否为系统内置类型（通常只读）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryType(
    String typeCode,
    String typeName,
    String description,
    boolean allowCustomItems,
    boolean isSystem
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryType {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        // Normalize the typeCode to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /** 是否可被用户编辑（系统类型通常只读）。 */
    public boolean isEditable() {
        return !isSystem;
    }
    
    /** 是否支持自定义项创建。 */
    public boolean supportsCustomItems() {
        return allowCustomItems;
    }
}
