package com.patra.registry.domain.exception;

/**
 * 当字典类型需要配置默认项但缺失时抛出。
 *
 * <p>表示该类型按业务规则应有默认项，但未标记默认项或默认项不存在。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryDefaultItemMissing extends RegistryRuleViolation {
    
    /** 缺失默认项的字典类型编码 */
    private final String typeCode;
    
    /** 使用类型编码构造异常。 */
    public DictionaryDefaultItemMissing(String typeCode) {
        super(String.format("Dictionary type is missing a default item: %s", typeCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 自定义消息构造异常。 */
    public DictionaryDefaultItemMissing(String typeCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 自定义消息与原因构造异常。 */
    public DictionaryDefaultItemMissing(String typeCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /** 返回类型编码。 */
    public String getTypeCode() {
        return typeCode;
    }
}
