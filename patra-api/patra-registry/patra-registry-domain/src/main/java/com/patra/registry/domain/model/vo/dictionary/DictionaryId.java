package com.patra.registry.domain.model.vo.dictionary;

import java.util.Objects;

/**
 * 字典聚合根标识值对象。
 * 
 * <p>基于字典类型编码作为自然键的聚合根标识。</p>
 *
 * @param typeCode 字典类型编码（自然键）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryId(String typeCode) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryId {
        Objects.requireNonNull(typeCode, "Dictionary ID type code cannot be null");
        if (typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary ID type code cannot be empty");
        }
        typeCode = typeCode.trim();
    }
    
    /** 从字典类型创建ID。 */
    public static DictionaryId of(String typeCode) {
        return new DictionaryId(typeCode);
    }
    
    /** 从字典类型对象创建ID。 */
    public static DictionaryId of(DictionaryType dictionaryType) {
        Objects.requireNonNull(dictionaryType, "Dictionary type cannot be null");
        return new DictionaryId(dictionaryType.typeCode());
    }
    
    @Override
    public String toString() {
        return "DictionaryId[" + typeCode + "]";
    }
}
