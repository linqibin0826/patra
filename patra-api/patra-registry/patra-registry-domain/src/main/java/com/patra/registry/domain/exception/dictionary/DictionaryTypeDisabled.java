package com.patra.registry.domain.exception.dictionary;

import com.patra.registry.domain.exception.RegistryRuleViolation;

/**
 * 当尝试使用被禁用的字典类型时抛出。
 *
 * <p>表示该类型存在，但当前处于禁用状态，无法参与业务操作。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryTypeDisabled extends RegistryRuleViolation {
    
    /** 被禁用的字典类型编码 */
    private final String typeCode;
    
    /**
     * 使用类型编码构造异常。
     */
    public DictionaryTypeDisabled(String typeCode) {
        super(String.format("Dictionary type is disabled: %s", typeCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 自定义消息构造异常。 */
    public DictionaryTypeDisabled(String typeCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 自定义消息与原因构造异常。 */
    public DictionaryTypeDisabled(String typeCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 返回被禁用的类型编码。 */
    public String getTypeCode() {
        return typeCode;
    }
}
