package com.patra.registry.domain.exception;

/**
 * 当尝试使用被禁用的字典项时抛出。
 *
 * <p>表示该项存在，但当前处于禁用状态，无法参与业务操作。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryItemDisabled extends RegistryRuleViolation {
    
    /** 字典类型编码 */
    private final String typeCode;
    
    /** 被禁用的字典项编码 */
    private final String itemCode;
    
    /** 使用类型编码与项编码构造异常。 */
    public DictionaryItemDisabled(String typeCode, String itemCode) {
        super(String.format("Dictionary item is disabled: typeCode=%s, itemCode=%s", typeCode, itemCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /** 自定义消息构造异常。 */
    public DictionaryItemDisabled(String typeCode, String itemCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /** 自定义消息与原因构造异常。 */
    public DictionaryItemDisabled(String typeCode, String itemCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /** 返回类型编码。 */
    public String getTypeCode() {
        return typeCode;
    }
    
    /** 返回被禁用的项编码。 */
    public String getItemCode() {
        return itemCode;
    }
}
