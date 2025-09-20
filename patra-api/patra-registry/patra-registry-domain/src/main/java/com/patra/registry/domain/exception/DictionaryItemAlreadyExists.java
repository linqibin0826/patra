package com.patra.registry.domain.exception;

/**
 * 当尝试在某类型下创建已存在的字典项时抛出。
 *
 * <p>表示给定项编码在该类型中已被使用，因唯一性约束无法再次创建。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryItemAlreadyExists extends RegistryConflict {
    
    /** 字典类型编码 */
    private final String typeCode;
    
    /** 已存在的字典项编码 */
    private final String itemCode;
    
    /**
     * 使用类型编码与项编码构造异常。
     */
    public DictionaryItemAlreadyExists(String typeCode, String itemCode) {
        super(String.format("Dictionary item already exists: typeCode=%s, itemCode=%s", typeCode, itemCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * 自定义消息构造异常。
     */
    public DictionaryItemAlreadyExists(String typeCode, String itemCode, String message) {
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
    
    /**
     * 自定义消息与原因构造异常。
     */
    public DictionaryItemAlreadyExists(String typeCode, String itemCode, String message, Throwable cause) {
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
    
    /** 返回已存在的项编码。 */
    public String getItemCode() {
        return itemCode;
    }
}
