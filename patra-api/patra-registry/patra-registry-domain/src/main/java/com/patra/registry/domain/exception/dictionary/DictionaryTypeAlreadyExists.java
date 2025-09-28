package com.patra.registry.domain.exception.dictionary;

import com.patra.registry.domain.exception.RegistryConflict;
import com.patra.registry.domain.exception.DomainValidationException;

/**
 * 当尝试创建已存在的字典类型时抛出。
    /**
     * 使用类型编码构造异常。
     *
     * @param typeCode 已存在的类型编码
     * @throws com.patra.registry.domain.exception.DomainValidationException 当编码为空
     */
public class DictionaryTypeAlreadyExists extends RegistryConflict {
    
    /** 已存在的字典类型编码 */
    private final String typeCode;
    
    /**
     * 使用类型编码构造异常。
     *
     * @param typeCode 已存在的类型编码
     * @throws IllegalArgumentException 当编码为空
     */
    public DictionaryTypeAlreadyExists(String typeCode) {
        super(String.format("Dictionary type already exists: %s", typeCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new DomainValidationException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * 自定义消息构造异常。
     */
    public DictionaryTypeAlreadyExists(String typeCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new DomainValidationException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * 自定义消息与原因构造异常。
     */
    public DictionaryTypeAlreadyExists(String typeCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new DomainValidationException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 返回已存在的类型编码。 */
    public String getTypeCode() {
        return typeCode;
    }
}
