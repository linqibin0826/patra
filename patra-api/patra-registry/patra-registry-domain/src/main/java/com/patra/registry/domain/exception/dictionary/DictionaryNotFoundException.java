package com.patra.registry.domain.exception.dictionary;

import com.patra.registry.domain.exception.RegistryNotFound;

/**
 * 当请求的字典类型或字典项未找到时抛出。
 *
 * <p>表示该资源不存在或因业务规则不可访问。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryNotFoundException extends RegistryNotFound {
    
    /** 关联的字典类型编码（如适用） */
    private final String typeCode;
    
    /** 关联的字典项编码（如适用） */
    private final String itemCode;
    
    /**
     * 类型未找到的场景构造异常。
     */
    public DictionaryNotFoundException(String typeCode) {
        super(String.format("Dictionary type not found: %s", typeCode));
        this.typeCode = typeCode;
        this.itemCode = null;
    }
    
    /**
     * 项未找到的场景构造异常。
     */
    public DictionaryNotFoundException(String typeCode, String itemCode) {
        super(String.format("Dictionary item not found: typeCode=%s, itemCode=%s", typeCode, itemCode));
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * 自定义消息构造异常。
     */
    public DictionaryNotFoundException(String message, String typeCode, String itemCode) {
        super(message);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /** 获取类型编码（如有）。 */
    public String getTypeCode() {
        return typeCode;
    }
    
    /** 获取项编码（如有）。 */
    public String getItemCode() {
        return itemCode;
    }
}
