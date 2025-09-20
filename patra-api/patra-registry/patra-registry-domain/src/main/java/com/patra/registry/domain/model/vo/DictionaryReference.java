package com.patra.registry.domain.model.vo;

/**
 * 字典引用值对象（用于校验/查找）。
 *
 * <p>不可变，表示某类型下特定字典项的引用。</p>
 *
 * @param typeCode 被引用的类型编码（不能为空）
 * @param itemCode 被引用的项编码（不能为空）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryReference(
    String typeCode,
    String itemCode
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryReference {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        // Normalize the codes to ensure consistency
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
    }
    
    /** 工厂方法：由类型与项编码创建引用。 */
    public static DictionaryReference of(String typeCode, String itemCode) {
        return new DictionaryReference(typeCode, itemCode);
    }
    
    /** 返回引用字符串表示（typeCode:itemCode）。 */
    public String toReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /** 与给定类型/项编码是否匹配。 */
    public boolean matches(String otherTypeCode, String otherItemCode) {
        return typeCode.equals(otherTypeCode) && itemCode.equals(otherItemCode);
    }
    
    /** 与另一引用是否具有相同类型编码。 */
    public boolean hasSameType(DictionaryReference other) {
        return other != null && typeCode.equals(other.typeCode);
    }
}
